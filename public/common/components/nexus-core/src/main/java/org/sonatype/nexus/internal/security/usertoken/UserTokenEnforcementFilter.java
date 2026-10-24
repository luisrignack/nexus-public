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

import java.io.IOException;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.common.app.WebFilterPriority;
import org.sonatype.nexus.security.token.BearerTokenRealm;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.net.HttpHeaders.WWW_AUTHENTICATE;

/**
 * Rejects password-based authentication against repository content when the
 * <em>Require User Tokens for Repository Authentication</em> option is enabled.
 *
 * @since 3.89
 */
@Component
@Singleton
@WebFilter(value = "/*", filterName = UserTokenEnforcementFilter.NAME)
@Order(WebFilterPriority.AUTHENTICATION + 50)
public class UserTokenEnforcementFilter
    extends ComponentSupport
    implements Filter
{
  public static final String NAME = "nx-usertoken-enforcement";

  private static final String ANONYMOUS = "anonymous";

  private final UserTokenService userTokens;

  @Inject
  public UserTokenEnforcementFilter(final UserTokenService userTokens) {
    this.userTokens = checkNotNull(userTokens);
  }

  @Override
  public void doFilter(
      final ServletRequest request,
      final ServletResponse response,
      final FilterChain chain) throws IOException, ServletException
  {
    if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
      HttpServletRequest httpRequest = (HttpServletRequest) request;
      if (userTokens.isEnabled() && userTokens.isProtectContent()
          && isRepositoryContent(httpRequest)
          && requiresToken(httpRequest)) {
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        httpResponse.setHeader(WWW_AUTHENTICATE, "Basic realm=\"Nexus Repository Manager\"");
        httpResponse.sendError(
            HttpServletResponse.SC_UNAUTHORIZED,
            "A user token is required to access repository content");
        return;
      }
    }

    chain.doFilter(request, response);
  }

  private static boolean isRepositoryContent(final HttpServletRequest request) {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    return path.startsWith("/repository/") || path.startsWith("/v2/");
  }

  private static boolean requiresToken(final HttpServletRequest request) {
    if (request.getHeader(com.google.common.net.HttpHeaders.AUTHORIZATION) == null) {
      // session-based (UI) or anonymous content access is not affected
      return false;
    }

    if (Boolean.TRUE.equals(request.getAttribute(BearerTokenRealm.IS_TOKEN_AUTH_KEY))) {
      return false;
    }

    Subject subject = SecurityUtils.getSubject();
    if (subject == null || !subject.isAuthenticated()) {
      return false;
    }
    Object principal = subject.getPrincipal();
    return principal != null && !ANONYMOUS.equals(principal.toString());
  }
}
