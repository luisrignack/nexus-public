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
package org.sonatype.nexus.security.authc.oidc;

import java.util.HashSet;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.sonatype.nexus.security.SecuritySystem;
import org.sonatype.nexus.security.oidc.OidcConfiguration;
import org.sonatype.nexus.security.oidc.OidcConfigurationStore;
import org.sonatype.nexus.security.role.Role;
import org.sonatype.nexus.security.role.RoleIdentifier;
import org.sonatype.nexus.security.user.User;
import org.sonatype.nexus.security.user.UserManager;
import org.sonatype.nexus.security.user.UserStatus;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAccount;
import org.apache.shiro.realm.AuthenticatingRealm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Shiro realm that accepts {@link OidcAuthenticationToken}s produced by the OIDC login endpoint.
 *
 * <p>On the first login the user is provisioned (JIT) into the default user manager with the roles
 * mapped from the configured {@code groupsClaim}: every group value that matches an existing Nexus
 * role id becomes a role of the user. The realm is registered as {@code OAuth2Realm} so it appears
 * in the active realms configuration like the other external realms.</p>
 *
 * @since 3.89
 */
@Component
@Singleton
@Qualifier(OidcRealm.REALM_NAME)
public class OidcRealm
    extends AuthenticatingRealm
{
  public static final String REALM_NAME = "OAuth2Realm";

  private static final Logger log = LoggerFactory.getLogger(OidcRealm.class);

  private final SecuritySystem securitySystem;

  private final OidcConfigurationStore configurationStore;

  /**
   * The security system is injected lazily because it reaches back to the realm manager, which
   * collects this realm as a bean: without the lazy proxy the two beans form a construction cycle
   * (same approach as {@code UserManagerImpl}). The store lives in {@code nexus-core} and may be
   * absent in isolated test contexts, so it is resolved lazily as well.
   */
  @Inject
  public OidcRealm(
      @Lazy final SecuritySystem securitySystem,
      @Lazy final OidcConfigurationStore configurationStore)
  {
    this.securitySystem = checkNotNull(securitySystem);
    this.configurationStore = checkNotNull(configurationStore);
    setName(REALM_NAME);
    setAuthenticationTokenClass(OidcAuthenticationToken.class);
    setCachingEnabled(false);
  }

  @Override
  protected AuthenticationInfo doGetAuthenticationInfo(final AuthenticationToken authenticationToken)
      throws AuthenticationException
  {
    OidcConfiguration configuration = configurationStore.load();
    if (configuration == null || !configuration.isEnabled()) {
      throw new AuthenticationException("OAuth2/OIDC login is not enabled");
    }

    OidcAuthenticationToken token = (OidcAuthenticationToken) authenticationToken;
    try {
      Set<String> roleIds = mapRoles(token.getGroups());
      provisionUser(configuration, token, roleIds);

      SimpleAccount account = new SimpleAccount(token.getUserId(), token.getCredentials(), REALM_NAME);
      account.setRoles(roleIds);
      return account;
    }
    catch (Exception e) {
      throw new AuthenticationException("OIDC login failed for user '" + token.getUserId() + "'", e);
    }
  }

  /**
   * Maps IdP groups to Nexus role ids: a group maps to a role with the same id when it exists.
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
      if (knownRoleIds.contains(group)) {
        roleIds.add(group);
      }
    }
    return roleIds;
  }

  private void provisionUser(
      final OidcConfiguration configuration,
      final OidcAuthenticationToken token,
      final Set<String> roleIds) throws Exception {
    String source = UserManager.DEFAULT_SOURCE;

    User user;
    try {
      user = securitySystem.getUser(token.getUserId(), source);
      user.setFirstName(orDefault(token.claim(configuration.getFirstNameClaim()), user.getFirstName()));
      user.setLastName(orDefault(token.claim(configuration.getLastNameClaim()), user.getLastName()));
      user.setEmailAddress(orDefault(token.claim(configuration.getEmailClaim()), user.getEmailAddress()));
      securitySystem.updateUser(user);
    }
    catch (Exception e) {
      user = new User();
      user.setUserId(token.getUserId());
      user.setFirstName(orDefault(token.claim(configuration.getFirstNameClaim()), ""));
      user.setLastName(orDefault(token.claim(configuration.getLastNameClaim()), ""));
      user.setEmailAddress(orDefault(token.claim(configuration.getEmailClaim()), ""));
      user.setSource(source);
      user.setStatus(UserStatus.active);
      securitySystem.addUser(user, java.util.UUID.randomUUID().toString());
      log.info("Provisioned OIDC user '{}'", token.getUserId());
    }

    if (!roleIds.isEmpty()) {
      // Role mappings are keyed by the authenticating realm name: AuthorizingRealmImpl resolves the
      // principal's realm ("OAuth2Realm") to a role-mapping source, so the mappings must be stored
      // under that same name for the mapped roles to be granted after login.
      Set<RoleIdentifier> roles = new HashSet<>();
      roleIds.forEach(roleId -> roles.add(new RoleIdentifier(REALM_NAME, roleId)));
      securitySystem.setUsersRoles(token.getUserId(), REALM_NAME, roles);
    }
  }

  private static String orDefault(final String value, final String fallback) {
    return value != null ? value : fallback;
  }
}
