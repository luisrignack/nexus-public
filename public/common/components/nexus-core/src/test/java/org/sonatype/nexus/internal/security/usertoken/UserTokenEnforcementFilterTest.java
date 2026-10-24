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

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.sonatype.goodies.testsupport.TestSupport;
import org.sonatype.nexus.security.token.BearerTokenRealm;

import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static com.google.common.net.HttpHeaders.AUTHORIZATION;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class UserTokenEnforcementFilterTest
    extends TestSupport
{
  private static final String CONTENT_PATH = "/repository/maven-central/org/acme/acme/1.0/acme-1.0.pom";

  @Mock
  private UserTokenService userTokens;

  @Mock
  private HttpServletRequest request;

  @Mock
  private HttpServletResponse response;

  @Mock
  private FilterChain chain;

  @Mock
  private Subject subject;

  private UserTokenEnforcementFilter underTest;

  @Before
  public void setUp() {
    underTest = new UserTokenEnforcementFilter(userTokens);
    when(request.getContextPath()).thenReturn("");
    when(request.getRequestURI()).thenReturn(CONTENT_PATH);
    when(request.getHeader(AUTHORIZATION)).thenReturn("Basic YWRtaW46czNjcmV0");
    ThreadContext.bind(subject);
  }

  @After
  public void tearDown() {
    ThreadContext.unbindSubject();
  }

  @Test
  public void doFilter_whenProtectionDisabled_passesThrough() throws Exception {
    when(userTokens.isEnabled()).thenReturn(true);
    when(userTokens.isProtectContent()).thenReturn(false);

    underTest.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    verify(response, never()).sendError(anyInt(), anyString());
  }

  @Test
  public void doFilter_outsideRepositoryContent_passesThrough() throws Exception {
    when(userTokens.isEnabled()).thenReturn(true);
    when(userTokens.isProtectContent()).thenReturn(true);
    when(request.getRequestURI()).thenReturn("/service/rest/v1/repositories");

    underTest.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
  }

  @Test
  public void doFilter_withoutAuthorization_passesThrough() throws Exception {
    when(userTokens.isEnabled()).thenReturn(true);
    when(userTokens.isProtectContent()).thenReturn(true);
    when(request.getHeader(AUTHORIZATION)).thenReturn(null);

    underTest.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
  }

  @Test
  public void doFilter_withTokenAuthentication_passesThrough() throws Exception {
    when(userTokens.isEnabled()).thenReturn(true);
    when(userTokens.isProtectContent()).thenReturn(true);
    when(request.getAttribute(BearerTokenRealm.IS_TOKEN_AUTH_KEY)).thenReturn(Boolean.TRUE);
    when(subject.isAuthenticated()).thenReturn(true);
    when(subject.getPrincipal()).thenReturn("bob");

    underTest.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    verify(response, never()).sendError(anyInt(), anyString());
  }

  @Test
  public void doFilter_withAnonymousSubject_passesThrough() throws Exception {
    when(userTokens.isEnabled()).thenReturn(true);
    when(userTokens.isProtectContent()).thenReturn(true);
    when(subject.isAuthenticated()).thenReturn(true);
    when(subject.getPrincipal()).thenReturn("anonymous");

    underTest.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
  }

  @Test
  public void doFilter_withPasswordAuthentication_isRejected() throws Exception {
    when(userTokens.isEnabled()).thenReturn(true);
    when(userTokens.isProtectContent()).thenReturn(true);
    when(subject.isAuthenticated()).thenReturn(true);
    when(subject.getPrincipal()).thenReturn("admin");

    underTest.doFilter(request, response, chain);

    verify(chain, never()).doFilter(request, response);
    verify(response).sendError(eq(HttpServletResponse.SC_UNAUTHORIZED), anyString());
  }
}
