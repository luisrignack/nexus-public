/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 */
package org.sonatype.nexus.security.authc.ldap;

import java.util.List;
import java.util.Set;

import org.sonatype.goodies.testsupport.TestSupport;
import org.sonatype.nexus.security.SecuritySystem;
import org.sonatype.nexus.security.ldap.LdapConfiguration;
import org.sonatype.nexus.security.ldap.LdapConfigurationStore;
import org.sonatype.nexus.security.ldap.LdapDirectory;
import org.sonatype.nexus.security.ldap.LdapDirectory.LdapUserInfo;
import org.sonatype.nexus.security.role.Role;
import org.sonatype.nexus.security.role.RoleIdentifier;
import org.sonatype.nexus.security.user.User;
import org.sonatype.nexus.security.user.UserNotFoundException;
import org.sonatype.nexus.security.user.UserStatus;

import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authc.SimpleAccount;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the LDAP realm group mapping and the new attribute/default-group options.
 */
public class LdapRealmTest
    extends TestSupport
{
  @Mock
  private SecuritySystem securitySystem;

  @Mock
  private LdapConfigurationStore configurationStore;

  @Mock
  private LdapDirectory ldapDirectory;

  @Mock
  private LdapConfiguration configuration;

  private LdapRealm underTest;

  @Before
  public void setup() {
    underTest = new LdapRealm(securitySystem, configurationStore, ldapDirectory);
    Role members = role("members");
    Role developers = role("developers");
    when(securitySystem.listRoles()).thenReturn(Set.of(members, developers));
    when(configuration.isLdapGroupsAsRoles()).thenReturn(true);
    when(configuration.getDefaultMembersGroup()).thenReturn("members");
    when(configuration.getName()).thenReturn("etecsa");
  }

  @Test
  public void mapsGroupsAndDefaultMembersGroup() throws Exception {
    LdapConfigurationStore stub = configurationStore;
    when(stub.listActive()).thenReturn(List.of(configuration));
    LdapUserInfo info = userInfo("juan", "Juan", "Perez", "DTSR", "Juan", "juan@etecsa.cu",
        List.of("developers", "unknown"));
    when(ldapDirectory.authenticate(configuration, "juan", "secret".toCharArray())).thenReturn(info);
    when(securitySystem.getUser("juan", "default")).thenThrow(new UserNotFoundException("juan"));

    AuthenticationInfo result = underTest.getAuthenticationInfo(new UsernamePasswordToken("juan", "secret"));

    assertThat(result.getPrincipals().getPrimaryPrincipal(), is("juan"));
    assertThat(((SimpleAccount) result).getRoles(), hasItem("developers"));
    assertThat(((SimpleAccount) result).getRoles(), hasItem("members"));

    ArgumentCaptor<User> created = ArgumentCaptor.forClass(User.class);
    verify(securitySystem).addUser(created.capture(), anyString());
    assertThat(created.getValue().getFirstName(), is("Juan"));
    assertThat(created.getValue().getLastName(), is("Perez"));
    assertThat(created.getValue().getEmailAddress(), is("juan@etecsa.cu"));

    ArgumentCaptor<Set<RoleIdentifier>> roles = ArgumentCaptor.forClass((Class) Set.class);
    verify(securitySystem).setUsersRoles(eq("juan"), eq(LdapRealm.REALM_NAME), roles.capture());
    assertThat(roles.getValue(), hasItem(new RoleIdentifier(LdapRealm.REALM_NAME, "developers")));
    assertThat(roles.getValue(), hasItem(new RoleIdentifier(LdapRealm.REALM_NAME, "members")));
  }

  private Role role(final String id) {
    Role role = mock(Role.class);
    when(role.getRoleId()).thenReturn(id);
    return role;
  }

  private LdapUserInfo userInfo(
      final String username, final String first, final String last, final String company,
      final String realName, final String email, final List<String> groups)
  {
    LdapUserInfo info = mock(LdapUserInfo.class);
    when(info.getUsername()).thenReturn(username);
    when(info.getRealName()).thenReturn(realName);
    when(info.getLastName()).thenReturn(last);
    when(info.getCompany()).thenReturn(company);
    when(info.getEmailAddress()).thenReturn(email);
    when(info.getGroups()).thenReturn(Set.copyOf(groups));
    return info;
  }
}
