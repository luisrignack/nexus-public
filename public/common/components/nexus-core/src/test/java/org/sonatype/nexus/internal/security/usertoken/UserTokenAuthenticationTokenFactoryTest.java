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
package org.sonatype.nexus.internal.security.usertoken;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;

import org.sonatype.goodies.testsupport.TestSupport;
import org.sonatype.nexus.security.authc.NexusApiKeyAuthenticationToken;

import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static com.google.common.net.HttpHeaders.AUTHORIZATION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class UserTokenAuthenticationTokenFactoryTest
    extends TestSupport
{
  private static final String NAME_CODE = "0123456789abcdef0123456789abcdef";

  private static final String PASS_CODE = "fedcba9876543210fedcba9876543210";

  private static final String TOKEN = NAME_CODE + PASS_CODE;

  private static final PrincipalCollection PRINCIPALS =
      new SimplePrincipalCollection("bob", "NexusAuthenticatingRealm");

  @Mock
  private UserTokenService userTokens;

  @Mock
  private HttpServletRequest request;

  private UserTokenAuthenticationTokenFactory underTest;

  @Before
  public void setUp() {
    underTest = new UserTokenAuthenticationTokenFactory(userTokens);
    when(request.getRemoteHost()).thenReturn("localhost");
  }

  @Test
  public void createToken_whenFeatureDisabled_returnsNull() {
    when(userTokens.isEnabled()).thenReturn(false);
    when(request.getHeader(AUTHORIZATION)).thenReturn(basic(NAME_CODE, PASS_CODE));

    assertThat(underTest.createToken(request, null), is(nullValue()));
  }

  @Test
  public void createToken_withoutAuthorizationHeader_returnsNull() {
    when(userTokens.isEnabled()).thenReturn(true);
    when(request.getHeader(AUTHORIZATION)).thenReturn(null);

    assertThat(underTest.createToken(request, null), is(nullValue()));
  }

  @Test
  public void createToken_withNonBasicAuthorization_returnsNull() {
    when(userTokens.isEnabled()).thenReturn(true);
    when(request.getHeader(AUTHORIZATION)).thenReturn("Bearer something");

    assertThat(underTest.createToken(request, null), is(nullValue()));
  }

  @Test
  public void createToken_withMalformedBase64_returnsNull() {
    when(userTokens.isEnabled()).thenReturn(true);
    when(request.getHeader(AUTHORIZATION)).thenReturn("Basic !!!not-base64!!!");

    assertThat(underTest.createToken(request, null), is(nullValue()));
  }

  @Test
  public void createToken_withRegularPassword_returnsNull() {
    when(userTokens.isEnabled()).thenReturn(true);
    when(request.getHeader(AUTHORIZATION)).thenReturn(basic("admin", "s3cret"));

    assertThat(underTest.createToken(request, null), is(nullValue()));
  }

  @Test
  public void createToken_withUnknownToken_returnsNull() {
    when(userTokens.isEnabled()).thenReturn(true);
    when(request.getHeader(AUTHORIZATION)).thenReturn(basic(NAME_CODE, PASS_CODE));
    when(userTokens.authenticate(TOKEN)).thenReturn(Optional.empty());

    assertThat(underTest.createToken(request, null), is(nullValue()));
  }

  @Test
  public void createToken_withValidToken_returnsApiKeyToken() {
    when(userTokens.isEnabled()).thenReturn(true);
    when(request.getHeader(AUTHORIZATION)).thenReturn(basic(NAME_CODE, PASS_CODE));
    when(userTokens.authenticate(TOKEN)).thenReturn(Optional.of(PRINCIPALS));

    AuthenticationToken token = underTest.createToken(request, null);

    assertThat(token, is(notNullValue()));
    assertThat(token, is(org.hamcrest.Matchers.instanceOf(NexusApiKeyAuthenticationToken.class)));
    assertThat(token.getPrincipal(), is(UserTokenRealm.NAME));
    assertThat(new String((char[]) token.getCredentials()), is(TOKEN));
  }

  @Test
  public void createToken_withNonHttpRequest_returnsNull() {
    when(userTokens.isEnabled()).thenReturn(true);

    assertThat(underTest.createToken(mock(ServletRequest.class), null), is(nullValue()));
  }

  private static String basic(final String username, final String password) {
    return "Basic " + Base64.getEncoder().encodeToString(
        (username + ':' + password).getBytes(StandardCharsets.UTF_8));
  }
}
