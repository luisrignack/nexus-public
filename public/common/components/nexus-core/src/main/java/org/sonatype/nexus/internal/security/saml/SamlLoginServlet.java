/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 */
package org.sonatype.nexus.internal.security.saml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.annotation.WebServlet;

import org.sonatype.nexus.internal.security.saml.SamlLoginFlow.Outcome;

import org.springframework.stereotype.Component;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Browser endpoints of the SAML service provider. Jetty discovers this bean automatically through
 * {@code JettyServer#registerServlets} and reads the {@link WebServlet} annotation.
 */
@Component
@Singleton
@WebServlet({SamlLoginFlow.LOGIN_PATH, SamlLoginFlow.ACS_PATH})
public class SamlLoginServlet
    extends HttpServlet
{
  private final SamlLoginFlow flow;

  @Inject
  public SamlLoginServlet(final SamlLoginFlow flow) {
    this.flow = checkNotNull(flow);
  }

  @Override
  protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
    respond(request, response, flow.begin(request));
  }

  @Override
  protected void doPost(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
    respond(request, response, flow.complete(request));
  }

  private static void respond(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final Outcome outcome) throws IOException
  {
    if (outcome.isRedirect()) {
      String location = outcome.getLocation();
      if (location != null && location.startsWith("/") && !location.startsWith(request.getContextPath() + "/")) {
        location = request.getContextPath() + location;
      }
      response.setStatus(HttpServletResponse.SC_SEE_OTHER);
      response.setHeader("Location", location);
      return;
    }
    response.setStatus(outcome.getStatus());
    response.setContentType("text/plain; charset=UTF-8");
    response.getOutputStream().write(outcome.getMessage().getBytes(StandardCharsets.UTF_8));
  }
}
