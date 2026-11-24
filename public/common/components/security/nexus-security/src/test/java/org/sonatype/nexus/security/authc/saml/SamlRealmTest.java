/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 */
package org.sonatype.nexus.security.authc.saml;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sonatype.goodies.testsupport.TestSupport;
import org.sonatype.nexus.security.SecuritySystem;
import org.sonatype.nexus.security.role.Role;
import org.sonatype.nexus.security.role.RoleIdentifier;
import org.sonatype.nexus.security.saml.SamlConfiguration;
import org.sonatype.nexus.security.saml.SamlConfigurationStore;
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
 * Tests the SAML realm: feature gating, group-to-role mapping and JIT provisioning.
 */
public class SamlRealmTest
    extends TestSupport
{
  @Mock
  private SecuritySystem securitySystem;

  @Mock
  private SamlConfigurationStore configurationStore;

  @Mock
  private SamlConfiguration configuration;

  private SamlRealm underTest;

  @Before
  public void setup() {
    underTest = new SamlRealm(securitySystem, configurationStore);
    when(configuration.isEnabled()).thenReturn(true);
    when(configuration.getFirstNameAttribute()).thenReturn("givenName");
    when(configuration.getLastNameAttribute()).thenReturn("sn");
    when(configuration.getEmailAttribute()).thenReturn("mail");
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
    assertThat(underTest.mapRoles(List.of("developers", "everyone", "nx-admin")),
        containsInAnyOrder("developers", "nx-admin"));
  }

  @Test
  public void mapRolesToleratesNullGroupsAndRoleListingFailure() throws Exception {
    assertThat(underTest.mapRoles(null), is(empty()));
    when(securitySystem.listRoles()).thenThrow(new RuntimeException("roles unavailable"));
    assertThat(underTest.mapRoles(List.of("developers")), is(empty()));
  }

  @Test
  public void provisionsUnknownUserAndStoresMappingsUnderRealmName() throws Exception {
    Role developers = role("developers");
    when(configurationStore.load()).thenReturn(configuration);
    when(securitySystem.listRoles()).thenReturn(Set.of(developers));
    when(securitySystem.getUser("alice", "default")).thenThrow(new UserNotFoundException("alice"));

    SamlAuthenticationToken token = new SamlAuthenticationToken(
        "alice",
        Map.of("givenName", "Alice", "sn", "Smith", "mail", "alice@example.com"),
        List.of("developers", "unknown-group"));

    AuthenticationInfo info = underTest.getAuthenticationInfo(token);

    assertThat(info.getPrincipals().getPrimaryPrincipal(), is("alice"));
    assertThat(((SimpleAccount) info).getRoles(), hasItem("developers"));

    ArgumentCaptor<User> provisioned = ArgumentCaptor.forClass(User.class);
    verify(securitySystem).addUser(provisioned.capture(), anyString());
    assertThat(provisioned.getValue().getFirstName(), is("Alice"));
    assertThat(provisioned.getValue().getLastName(), is("Smith"));
    assertThat(provisioned.getValue().getEmailAddress(), is("alice@example.com"));
    assertThat(provisioned.getValue().getStatus(), is(UserStatus.active));

    ArgumentCaptor<Set<RoleIdentifier>> mappings = roleMappings();
    verify(securitySystem).setUsersRoles(eq("alice"), eq(SamlRealm.REALM_NAME), mappings.capture());
    assertThat(mappings.getValue(), hasItem(new RoleIdentifier(SamlRealm.REALM_NAME, "developers")));
    assertThat(mappings.getValue().size(), is(1));
  }

  @Test
  public void updatesExistingUserAndKeepsFieldsWithoutAttributes() throws Exception {
    when(configurationStore.load()).thenReturn(configuration);
    when(securitySystem.listRoles()).thenReturn(Set.of());

    User existing = new User();
    existing.setUserId("alice");
    existing.setFirstName("Alice");
    existing.setLastName("Old");
    existing.setEmailAddress("old@example.com");
    existing.setSource("default");
    when(securitySystem.getUser("alice", "default")).thenReturn(existing);

    underTest.getAuthenticationInfo(new SamlAuthenticationToken("alice", Map.of("givenName", "Alicia"), List.of()));

    ArgumentCaptor<User> updated = ArgumentCaptor.forClass(User.class);
    verify(securitySystem).updateUser(updated.capture());
    assertThat(updated.getValue().getFirstName(), is("Alicia"));
    assertThat(updated.getValue().getLastName(), is("Old"));
    assertThat(updated.getValue().getEmailAddress(), is("old@example.com"));
    verify(securitySystem, never()).addUser(any(), anyString());
    verify(securitySystem, never()).setUsersRoles(anyString(), anyString(), any());
  }

  private SamlAuthenticationToken token(final String userId) {
    return new SamlAuthenticationToken(userId, Map.of(), List.of());
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
