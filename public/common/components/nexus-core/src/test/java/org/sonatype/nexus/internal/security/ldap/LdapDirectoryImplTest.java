/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 */
package org.sonatype.nexus.internal.security.ldap;

import org.sonatype.nexus.security.ldap.LdapConfiguration;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;

/**
 * Verifies that the user search filter escapes caller-supplied usernames but keeps the wildcard
 * used by the "Verify User Mapping" listing search.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class LdapDirectoryImplTest
{
  @Mock
  private LdapConfiguration configuration;

  @Test
  public void verificationSearchKeepsWildcard() {
    when(configuration.getUserObjectClass()).thenReturn("inetOrgPerson");
    when(configuration.getUserIdAttribute()).thenReturn("uid");
    when(configuration.getSearchBehavior()).thenReturn("EXACT");

    String filter = LdapDirectoryImpl.userSearchFilter(configuration, "*", false);
    assertThat(filter, containsString("(uid=*)"));
  }

  @Test
  public void loginSearchEscapesWildcardsInUsername() {
    when(configuration.getUserObjectClass()).thenReturn("inetOrgPerson");
    when(configuration.getUserIdAttribute()).thenReturn("uid");
    when(configuration.getSearchBehavior()).thenReturn("EXACT");

    String filter = LdapDirectoryImpl.userSearchFilter(configuration, "a*b", true);
    assertThat(filter, not(containsString("(uid=a*b)")));
    assertThat(filter, containsString("(uid=a\\2ab)"));
  }
}
