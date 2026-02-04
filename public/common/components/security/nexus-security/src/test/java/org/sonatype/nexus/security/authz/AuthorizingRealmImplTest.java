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
package org.sonatype.nexus.security.authz;

import java.util.HashSet;
import java.util.Set;

import org.sonatype.nexus.security.AbstractSecurityTest;
import org.sonatype.nexus.security.authz.AuthorizingRealmImplTest.AuthorizingRealmImplTestConfiguration;
import org.sonatype.nexus.security.config.CPrivilege;
import org.sonatype.nexus.security.config.CRole;
import org.sonatype.nexus.security.config.CUser;
import org.sonatype.nexus.security.config.MemorySecurityConfiguration;
import org.sonatype.nexus.security.config.PreconfiguredSecurityConfigurationSource;
import org.sonatype.nexus.security.config.SecurityConfigurationManager;
import org.sonatype.nexus.security.config.SecurityConfigurationSource;
import org.sonatype.nexus.security.config.memory.MemoryCUser;
import org.sonatype.nexus.security.internal.AuthorizingRealmImpl;
import org.sonatype.nexus.security.privilege.WildcardPrivilegeDescriptor;
import org.sonatype.nexus.security.user.UserStatus;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.permission.RolePermissionResolver;
import org.apache.shiro.authz.permission.WildcardPermission;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AuthorizingRealmImpl}.
 */
@Import(AuthorizingRealmImplTestConfiguration.class)
@TestPropertySource(properties = {"nexus.security.principal.permissions.cache.enabled=true"})
public class AuthorizingRealmImplTest
    extends AbstractSecurityTest
{
  private AuthorizingRealmImpl realm;

  private SecurityConfigurationManager configurationManager;

  private SimplePrincipalCollection principal;

  private MockedStatic<SecurityUtils> securityUtilsMock;

  protected static class AuthorizingRealmImplTestConfiguration
  {
    @Qualifier("default")
    @Primary
    @Bean
    SecurityConfigurationSource securityConfigurationSource() {
      return new PreconfiguredSecurityConfigurationSource(new MemorySecurityConfiguration());
    }
  }

  @Override
  @BeforeEach
  protected void setUp() throws Exception {
    super.setUp();
    realm = (AuthorizingRealmImpl) lookup(Realm.class, AuthorizingRealmImpl.NAME);
    realm.setRolePermissionResolver(this.lookup(RolePermissionResolver.class));

    configurationManager = lookup(SecurityConfigurationManager.class);

    buildTestAuthorizationConfig();

    // Fails because the configuration requirement in nexus authorizing realm isn't initialized
    // thus NPE
    principal = new SimplePrincipalCollection("username", realm.getName());
    Subject mockSubject = mock(Subject.class);
    when(mockSubject.getPrincipals()).thenReturn(principal);

    securityUtilsMock = mockStatic(SecurityUtils.class);
    securityUtilsMock.when(SecurityUtils::getSubject).thenReturn(mockSubject);
  }

  @AfterEach
  public void tearDown() {
    if (securityUtilsMock != null) {
      securityUtilsMock.close();
    }
  }

  @Test
  public void testAuthorization() {
    assertTrue(realm.hasRole(principal, "role"));

    // Verify the permission
    assertTrue(realm.isPermitted(principal, new WildcardPermission("app:config:read")));
    // Verify other method not allowed
    assertFalse(realm.isPermitted(principal, new WildcardPermission("app:config:create")));
    assertFalse(realm.isPermitted(principal, new WildcardPermission("app:config:update")));
    assertFalse(realm.isPermitted(principal, new WildcardPermission("app:config:delete")));

    // Verify other permission not allowed
    assertFalse(realm.isPermitted(principal, new WildcardPermission("app:ui:read")));
    assertFalse(realm.isPermitted(principal, new WildcardPermission("app:ui:create")));
    assertFalse(realm.isPermitted(principal, new WildcardPermission("app:ui:update")));
    assertFalse(realm.isPermitted(principal, new WildcardPermission("app:ui:delete")));
  }

  private void buildTestAuthorizationConfig() throws Exception {
    buildTestAuthorizationConfig("username");
  }

  private void buildTestAuthorizationConfig(String userId) throws Exception {
    CPrivilege priv = WildcardPrivilegeDescriptor.privilege("app:config:read");
    configurationManager.createPrivilege(priv);

    CRole role = configurationManager.newRole();
    role.setId("role");
    role.setName("somerole");
    role.setDescription("somedescription");
    role.addPrivilege(priv.getId());

    configurationManager.createRole(role);

    CUser user = new MemoryCUser();
    user.setEmail("dummyemail@foo");
    user.setFirstName("dummyFirstName");
    user.setLastName("dummyLastName");
    user.setStatus(UserStatus.active.toString());
    user.setId(userId);
    user.setPassword("password");

    Set<String> roles = new HashSet<String>();
    roles.add(role.getId());

    configurationManager.createUser(user, roles);
  }
}
