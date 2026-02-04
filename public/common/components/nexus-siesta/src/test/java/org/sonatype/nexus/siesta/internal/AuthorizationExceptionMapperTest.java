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
package org.sonatype.nexus.siesta.internal;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.sonatype.goodies.testsupport.Test5Support;

import jakarta.inject.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

class AuthorizationExceptionMapperTest
    extends Test5Support
{
  @Mock
  private HttpServletRequest httpRequest;

  @Mock
  private Provider<HttpServletRequest> httpRequestProvider;

  private AuthorizationExceptionMapper underTest;

  @BeforeEach
  void setup() {
    when(httpRequestProvider.get()).thenReturn(httpRequest);
    underTest = new AuthorizationExceptionMapper(httpRequestProvider);
  }

  @Test
  void convertAnonymousReturnsForbidden() {
    when(httpRequest.getAttribute("nexus.anonymous")).thenReturn(null);
    try (Response response = underTest.convert(null, null)) {
      assertThat(response.getStatus(), is(Status.FORBIDDEN.getStatusCode()));
    }
  }

  @Test
  void convertAnonymousReturnsUnauthorized() {
    when(httpRequest.getAttribute("nexus.anonymous")).thenReturn("anonymous");
    when(httpRequest.getAttribute("auth.scheme")).thenReturn("scheme");
    when(httpRequest.getAttribute("auth.realm")).thenReturn("realm");
    try (Response response = underTest.convert(null, null)) {
      assertThat(response.getStatus(), is(Status.UNAUTHORIZED.getStatusCode()));
      assertThat(response.getHeaders().get("WWW-Authenticate"), contains("scheme realm=\"realm\""));
    }
  }
}
