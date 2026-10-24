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

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sonatype.goodies.testsupport.TestSupport;
import org.sonatype.nexus.security.SecuritySystem;
import org.sonatype.nexus.security.oidc.OidcConfiguration;
import org.sonatype.nexus.security.oidc.OidcConfigurationStore;
import org.sonatype.nexus.security.role.Role;
import org.sonatype.nexus.security.role.RoleIdentifier;
import org.sonatype.nexus.security.user.User;
import org.sonatype.nexus.security.user.UserNotFoundException;
import org.sonatype.nexus.security.user.UserStatus;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.SimpleAccount;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the behaviors the OAuth2 realm owns: refusing logins while the feature is off, mapping IdP
 * groups onto existing Nexus roles, JIT provisioning of unknown users and storage of the role mappings
 * under the realm name that {@code AuthorizingRealmImpl} resolves at authorization time.
 */
public class OidcRealmTest
    extends TestSupport
{
  @Mock
  private SecuritySystem securitySystem;

  @Mock
  private OidcConfigurationStore configurationStore;

  @Mock
  private OidcConfiguration configuration;

  private OidcRealm underTest;

  @Before
  public void setup() {
    underTest = new OidcRealm(securitySystem, configurationStore);

    when(configuration.isEnabled()).thenReturn(true);
    when(configuration.getFirstNameClaim()).thenReturn("given_name");
    when(configuration.getLastNameClaim()).thenReturn("family_name");
    when(configuration.getEmailClaim()).thenReturn("email");
  }

  @Test
  public void refusesLoginWhenConfigurationMissing() {
    when(configurationStore.load()).thenReturn(null);

    assertThrows(AuthenticationException.class, () -> underTest.getAuthenticationInfo(token("alice")));
  }

  @Test
  public void refusesLoginWhenFeatureDisabled() {
    when(configurationStore.load()).thenReturn(configuration);
    when(configuration.isEnabled()).thenReturn(false);

    assertThrows(AuthenticationException.class, () -> underTest.getAuthenticationInfo(token("alice")));
  }

  @Test
  public void mapRolesOnlyKeepsGroupsMatchingExistingRoleIds() throws Exception {
    Role developers = role("developers");
    Role admins = role("nx-admin");
    when(securitySystem.listRoles()).thenReturn(Set.of(developers, admins));

    Set<String> mapped = underTest.mapRoles(List.of("developers", "everyone", "nx-admin"));

    assertThat(mapped, containsInAnyOrder("developers", "nx-admin"));
  }

  @Test
  public void mapRolesToleratesNullGroupsAndRoleListingFailure() throws Exception {
    assertThat(underTest.mapRoles(null), is(empty()));

    when(securitySystem.listRoles()).thenThrow(new RuntimeException("roles unavailable"));
    assertThat(underTest.mapRoles(List.of("developers")), is(empty()));
  }

  @Test
  public void provisionsUnknownUserAndStoresRoleMappingsUnderRealmName() throws Exception {
    Role developers = role("developers");
    when(configurationStore.load()).thenReturn(configuration);
    when(securitySystem.listRoles()).thenReturn(Set.of(developers));
    when(securitySystem.getUser("alice", "default")).thenThrow(new UserNotFoundException("alice"));

    OidcAuthenticationToken token = new OidcAuthenticationToken(
        "alice",
        Map.of("given_name", "Alice", "family_name", "Smith", "email", "alice@example.com"),
        List.of("developers", "unknown-group"));

    AuthenticationInfo info = underTest.getAuthenticationInfo(token);

    assertThat(info.getPrincipals().getPrimaryPrincipal(), is("alice"));
    assertThat(((SimpleAccount) info).getRoles(), hasItem("developers"));

    ArgumentCaptor<User> provisioned = ArgumentCaptor.forClass(User.class);
    verify(securitySystem).addUser(provisioned.capture(), anyString());
    User user = provisioned.getValue();
    assertThat(user.getUserId(), is("alice"));
    assertThat(user.getFirstName(), is("Alice"));
    assertThat(user.getLastName(), is("Smith"));
    assertThat(user.getEmailAddress(), is("alice@example.com"));
    assertThat(user.getSource(), is("default"));
    assertThat(user.getStatus(), is(UserStatus.active));

    ArgumentCaptor<Set<RoleIdentifier>> mappings = roleMappings();
    verify(securitySystem).setUsersRoles(eq("alice"), eq(OidcRealm.REALM_NAME), mappings.capture());
    assertThat(mappings.getValue(), hasItem(new RoleIdentifier(OidcRealm.REALM_NAME, "developers")));
    // only the group that matched a known role id is mapped
    assertThat(mappings.getValue().size(), is(1));
  }

  @Test
  public void updatesExistingUserAndKeepsFieldsWithoutClaims() throws Exception {
    when(configurationStore.load()).thenReturn(configuration);
    when(securitySystem.listRoles()).thenReturn(Set.of());

    User existing = new User();
    existing.setUserId("alice");
    existing.setFirstName("Alice");
    existing.setLastName("Old");
    existing.setEmailAddress("old@example.com");
    existing.setSource("default");
    when(securitySystem.getUser("alice", "default")).thenReturn(existing);

    // the IdP only sent the first name claim this time
    OidcAuthenticationToken token = new OidcAuthenticationToken("alice", Map.of("given_name", "Alicia"), List.of());

    underTest.getAuthenticationInfo(token);

    ArgumentCaptor<User> updated = ArgumentCaptor.forClass(User.class);
    verify(securitySystem).updateUser(updated.capture());
    User user = updated.getValue();
    assertThat(user.getFirstName(), is("Alicia"));
    assertThat(user.getLastName(), is("Old"));
    assertThat(user.getEmailAddress(), is("old@example.com"));

    verify(securitySystem, never()).addUser(any(), anyString());
    verify(securitySystem, never()).setUsersRoles(anyString(), anyString(), any());
  }

  private OidcAuthenticationToken token(final String userId) {
    return new OidcAuthenticationToken(userId, Map.of(), List.of());
  }

  private Role role(final String roleId) {
    Role role = mock(Role.class);
    when(role.getRoleId()).thenReturn(roleId);
    return role;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private ArgumentCaptor<Set<RoleIdentifier>> roleMappings() {
    return ArgumentCaptor.forClass((Class) Set.class);
  }
}
