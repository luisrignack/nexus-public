/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.security.authc.ldap;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.sonatype.nexus.security.SecuritySystem;
import org.sonatype.nexus.security.ldap.LdapConfiguration;
import org.sonatype.nexus.security.ldap.LdapConfigurationStore;
import org.sonatype.nexus.security.ldap.LdapDirectory;
import org.sonatype.nexus.security.ldap.LdapDirectory.LdapUserInfo;
import org.sonatype.nexus.security.role.Role;
import org.sonatype.nexus.security.role.RoleIdentifier;
import org.sonatype.nexus.security.user.User;
import org.sonatype.nexus.security.user.UserManager;
import org.sonatype.nexus.security.user.UserStatus;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAccount;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.realm.AuthenticatingRealm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Shiro realm that authenticates users against the configured LDAP servers.
 *
 * <p>Servers are tried in the order configured by the administrator; the first server that
 * successfully binds the user wins. On every login the user is provisioned (JIT) into the default
 * user manager with the roles mapped from the LDAP group memberships: every group value that
 * matches an existing Nexus role id becomes a role of the user, when {@code ldapGroupsAsRoles}
 * is on.</p>
 *
 * <p>The realm is registered as {@code LdapRealm}; {@code RealmToSource} already maps that name to
 * the {@code LDAP} user source.</p>
 *
 * @since 3.89
 */
@Component
@Singleton
@Qualifier(LdapRealm.REALM_NAME)
public class LdapRealm
    extends AuthenticatingRealm
{
  public static final String REALM_NAME = "LdapRealm";

  private static final Logger log = LoggerFactory.getLogger(LdapRealm.class);

  private final SecuritySystem securitySystem;

  private final LdapConfigurationStore configurationStore;

  private final LdapDirectory ldapDirectory;

  /**
   * The security system is injected lazily because it reaches back to the realm manager, which
   * collects this realm as a bean: without the lazy proxy the two beans form a construction cycle
   * (same approach as {@code OidcRealm}). The store and the directory live in {@code nexus-core}
   * and may be absent in isolated test contexts, so they are resolved lazily as well.
   */
  @Inject
  public LdapRealm(
      @Lazy final SecuritySystem securitySystem,
      @Lazy final LdapConfigurationStore configurationStore,
      @Lazy final LdapDirectory ldapDirectory)
  {
    this.securitySystem = checkNotNull(securitySystem);
    this.configurationStore = configurationStore;
    this.ldapDirectory = ldapDirectory;
    setName(REALM_NAME);
    setAuthenticationTokenClass(UsernamePasswordToken.class);
    setCachingEnabled(false);
  }

  @Override
  protected AuthenticationInfo doGetAuthenticationInfo(final AuthenticationToken authenticationToken)
      throws AuthenticationException
  {
    UsernamePasswordToken token = (UsernamePasswordToken) authenticationToken;
    String username = token.getUsername();
    char[] password = token.getPassword() != null ? token.getPassword() : new char[0];

    for (LdapConfiguration configuration : listServers()) {
      try {
        LdapUserInfo userInfo = ldapDirectory.authenticate(configuration, username, password);
        Set<String> roleIds = configuration.isLdapGroupsAsRoles()
            ? mapRoles(userInfo.getGroups())
            : new HashSet<>();
        provisionUser(configuration, userInfo, roleIds);

        SimpleAccount account = new SimpleAccount(userInfo.getUsername(), password, REALM_NAME);
        account.setRoles(roleIds);
        return account;
      }
      catch (Exception e) {
        log.debug("LDAP authentication against server '{}' failed for user '{}' ({}): {}",
            configuration.getName(), username, e.getClass().getSimpleName(), e.getMessage());
      }
    }

    // The user is not known to any LDAP server; other realms may still handle the token.
    throw new NoLdapUserFoundException(
        "No LDAP server could authenticate user '" + username + "'");
  }

  /**
   * Signals that no configured LDAP server recognized the user, letting the modularity
   * configuration try other realms as well.
   */
  public static class NoLdapUserFoundException
      extends AuthenticationException
  {
    public NoLdapUserFoundException(final String message) {
      super(message);
    }
  }

  /**
   * Lists the enabled servers, tolerating an absent store (isolated test contexts).
   */
  private List<LdapConfiguration> listServers() {
    if (configurationStore == null) {
      return Collections.emptyList();
    }
    try {
      return configurationStore.listActive();
    }
    catch (Exception e) {
      log.trace("LDAP configuration store unavailable", e);
      return Collections.emptyList();
    }
  }

  /**
   * Maps LDAP groups to Nexus role ids: a group maps to a role with the same id when it exists.
   */
  Set<String> mapRoles(final Iterable<String> groups) {
    Set<String> roleIds = new HashSet<>();
    if (groups == null) {
      return roleIds;
    }
    Set<String> knownRoleIds = new HashSet<>();
    try {
      for (Role role : securitySystem.listRoles()) {
        knownRoleIds.add(role.getRoleId());
      }
    }
    catch (Exception e) {
      log.trace("Unable to list Nexus roles", e);
      return roleIds;
    }
    for (String group : groups) {
      if (group != null && knownRoleIds.contains(group)) {
        roleIds.add(group);
      }
    }
    return roleIds;
  }

  private void provisionUser(
      final LdapConfiguration configuration,
      final LdapUserInfo userInfo,
      final Set<String> roleIds) throws Exception {
    String source = UserManager.DEFAULT_SOURCE;

    User user;
    try {
      user = securitySystem.getUser(userInfo.getUsername(), source);
      user.setFirstName(orDefault(userInfo.getRealName(), user.getFirstName()));
      user.setEmailAddress(orDefault(userInfo.getEmailAddress(), user.getEmailAddress()));
      securitySystem.updateUser(user);
    }
    catch (Exception e) {
      user = new User();
      user.setUserId(userInfo.getUsername());
      user.setFirstName(orDefault(userInfo.getRealName(), ""));
      user.setLastName("");
      user.setEmailAddress(orDefault(userInfo.getEmailAddress(), ""));
      user.setSource(source);
      user.setStatus(UserStatus.active);
      securitySystem.addUser(user, java.util.UUID.randomUUID().toString());
      log.info("Provisioned LDAP user '{}' via server '{}'", userInfo.getUsername(), configuration.getName());
    }

    if (!roleIds.isEmpty()) {
      // Role mappings are keyed by the authenticating realm name: AuthorizingRealmImpl resolves the
      // principal's realm ("LdapRealm") to a role-mapping source, so the mappings must be stored
      // under that same name for the mapped roles to be granted after login.
      Set<RoleIdentifier> roles = new HashSet<>();
      roleIds.forEach(roleId -> roles.add(new RoleIdentifier(REALM_NAME, roleId)));
      securitySystem.setUsersRoles(userInfo.getUsername(), REALM_NAME, roles);
    }
  }

  private static String orDefault(final String value, final String fallback) {
    return value != null ? value : fallback;
  }
}
