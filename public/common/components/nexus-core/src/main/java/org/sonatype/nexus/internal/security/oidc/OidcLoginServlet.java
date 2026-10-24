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
package org.sonatype.nexus.internal.security.oidc;

import java.io.IOException;

import javax.annotation.Nullable;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.springframework.stereotype.Component;

/**
 * Application-level entry points of the OAuth2/OIDC single sign-on flow:
 *
 * <ul>
 *   <li>{@code GET /oidc/login} starts the flow and redirects the browser to the IdP. This is the path
 *       the login page's SSO button targets, mirroring the {@code /saml} entry point.</li>
 *   <li>{@code GET /oidc/callback} is the IdP redirect target; it completes the flow and lands the
 *       browser in the application.</li>
 * </ul>
 *
 * @since 3.89
 */
@WebServlet({OidcLoginFlow.LOGIN_PATH, OidcLoginFlow.CALLBACK_PATH})
@Component
@Singleton
public class OidcLoginServlet
    extends HttpServlet
{
  private static final long serialVersionUID = 1L;

  private final OidcLoginFlow flow;

  @Inject
  public OidcLoginServlet(final OidcLoginFlow flow) {
    this.flow = flow;
  }

  @Override
  protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
      throws IOException {
    OidcLoginFlow.Outcome outcome;
    if (OidcLoginFlow.CALLBACK_PATH.equals(request.getServletPath())) {
      outcome = flow.complete(
          request,
          request.getParameter("code"),
          request.getParameter("state"),
          request.getParameter("error"));
    }
    else {
      outcome = flow.begin(request, request.getParameter("hash"));
    }

    if (outcome.isRedirect()) {
      response.setStatus(HttpServletResponse.SC_SEE_OTHER);
      response.setHeader("Location", outcome.getLocation());
      return;
    }

    response.setStatus(outcome.getStatus());
    response.setContentType("text/plain;charset=UTF-8");
    response.getWriter().write(message(outcome));
  }

  private static String message(final OidcLoginFlow.Outcome outcome) {
    @Nullable String message = outcome.getMessage();
    return message != null && !message.isBlank() ? message : "OIDC login failed";
  }
}
