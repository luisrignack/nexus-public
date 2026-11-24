/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 */
package org.sonatype.nexus.security.authc.saml;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.sonatype.nexus.security.SecuritySystem;
import org.sonatype.nexus.security.role.Role;
import org.sonatype.nexus.security.role.RoleIdentifier;
import org.sonatype.nexus.security.saml.SamlConfiguration;
import org.sonatype.nexus.security.saml.SamlConfigurationStore;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Shiro realm that accepts {@link SamlAuthenticationToken}s produced by the SAML login endpoint.
 *
 * <p>Users are provisioned on first login with the attributes of the assertion and the groups that
 * match existing Nexus role ids. Role mappings are stored under {@code SamlRealm}, which is the
 * source that the authorization layer associates with this realm.</p>
 */
@Component
@Singleton
@Qualifier(SamlRealm.REALM_NAME)
public class SamlRealm
    extends AuthenticatingRealm
{
  public static final String REALM_NAME = "SamlRealm";

  private static final Logger log = LoggerFactory.getLogger(SamlRealm.class);

  private final SecuritySystem securitySystem;

  private final SamlConfigurationStore configurationStore;

  @Inject
  public SamlRealm(
      @Lazy final SecuritySystem securitySystem,
      @Lazy final SamlConfigurationStore configurationStore)
  {
    this.securitySystem = checkNotNull(securitySystem);
    this.configurationStore = checkNotNull(configurationStore);
    setName(REALM_NAME);
    setAuthenticationTokenClass(SamlAuthenticationToken.class);
    setCachingEnabled(false);
  }

  @Override
  protected AuthenticationInfo doGetAuthenticationInfo(final AuthenticationToken authenticationToken)
      throws AuthenticationException
  {
    SamlConfiguration configuration = configurationStore.load();
    if (configuration == null || !configuration.isEnabled()) {
      throw new AuthenticationException("SAML single sign-on is not enabled");
    }

    SamlAuthenticationToken token = (SamlAuthenticationToken) authenticationToken;
    try {
      Set<String> roleIds = mapRoles(token.getGroups());
      provisionUser(configuration, token, roleIds);

      SimpleAccount account = new SimpleAccount(token.getUserId(), token.getCredentials(), REALM_NAME);
      account.setRoles(roleIds);
      return account;
    }
    catch (Exception e) {
      throw new AuthenticationException("SAML login failed for user '" + token.getUserId() + "'", e);
    }
  }

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
      final SamlConfiguration configuration,
      final SamlAuthenticationToken token,
      final Set<String> roleIds) throws Exception
  {
    String source = UserManager.DEFAULT_SOURCE;

    User user;
    try {
      user = securitySystem.getUser(token.getUserId(), source);
      user.setFirstName(orDefault(token.attribute(configuration.getFirstNameAttribute()), user.getFirstName()));
      user.setLastName(orDefault(token.attribute(configuration.getLastNameAttribute()), user.getLastName()));
      user.setEmailAddress(orDefault(token.attribute(configuration.getEmailAttribute()), user.getEmailAddress()));
      securitySystem.updateUser(user);
    }
    catch (Exception e) {
      user = new User();
      user.setUserId(token.getUserId());
      user.setFirstName(orDefault(token.attribute(configuration.getFirstNameAttribute()), ""));
      user.setLastName(orDefault(token.attribute(configuration.getLastNameAttribute()), ""));
      user.setEmailAddress(orDefault(token.attribute(configuration.getEmailAttribute()), ""));
      user.setSource(source);
      user.setStatus(UserStatus.active);
      securitySystem.addUser(user, UUID.randomUUID().toString());
      log.info("Provisioned SAML user '{}'", token.getUserId());
    }

    if (!roleIds.isEmpty()) {
      Set<RoleIdentifier> roles = new HashSet<>();
      roleIds.forEach(roleId -> roles.add(new RoleIdentifier(REALM_NAME, roleId)));
      securitySystem.setUsersRoles(token.getUserId(), REALM_NAME, roles);
    }
  }

  private static String orDefault(final String value, final String fallback) {
    return value != null ? value : fallback;
  }
}
