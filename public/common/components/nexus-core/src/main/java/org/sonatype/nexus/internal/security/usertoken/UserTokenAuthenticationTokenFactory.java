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

import javax.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.sonatype.nexus.security.authc.AuthenticationTokenFactory;
import org.sonatype.nexus.security.authc.NexusApiKeyAuthenticationToken;

import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.web.util.WebUtils;
import org.springframework.stereotype.Component;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.net.HttpHeaders.AUTHORIZATION;

/**
 * Turns an HTTP Basic pair made of a name code and a pass code into a user token authentication.
 *
 * <p>This is consulted by {@code nx-authc} before it falls back to ordinary password authentication, so user
 * tokens work on every endpoint (repository content, REST and the Docker registry) without having to tweak the
 * security filter chains. Only values matching the generated token format are considered, which means normal
 * user/password requests keep flowing through the regular realms.</p>
 *
 * @since 3.89
 */
@Component
@Singleton
public class UserTokenAuthenticationTokenFactory
    implements AuthenticationTokenFactory
{
  static final int CODE_LENGTH = UserTokenServiceImpl.CODE_LENGTH;

  private static final String BASIC_PREFIX = "Basic ";

  private final UserTokenService userTokens;

  @Inject
  public UserTokenAuthenticationTokenFactory(final UserTokenService userTokens) {
    this.userTokens = checkNotNull(userTokens);
  }

  @Nullable
  @Override
  public AuthenticationToken createToken(final ServletRequest request, final ServletResponse response) {
    if (!userTokens.isEnabled() || !(request instanceof HttpServletRequest)) {
      return null;
    }

    HttpServletRequest http = WebUtils.toHttp(request);
    String header = http.getHeader(AUTHORIZATION);
    if (header == null || !header.regionMatches(true, 0, BASIC_PREFIX, 0, BASIC_PREFIX.length())) {
      return null;
    }

    String decoded;
    try {
      decoded = new String(
          Base64.getDecoder().decode(header.substring(BASIC_PREFIX.length()).trim()),
          StandardCharsets.UTF_8);
    }
    catch (IllegalArgumentException e) {
      return null;
    }

    int separator = decoded.indexOf(':');
    if (separator <= 0) {
      return null;
    }

    String nameCode = decoded.substring(0, separator);
    String passCode = decoded.substring(separator + 1);
    if (!isCode(nameCode) || !isCode(passCode)) {
      return null;
    }

    String token = nameCode + passCode;
    if (userTokens.authenticate(token).isEmpty()) {
      return null;
    }

    return new NexusApiKeyAuthenticationToken(UserTokenRealm.NAME, token.toCharArray(), request.getRemoteHost());
  }

  private static boolean isCode(final String value) {
    if (value.length() != CODE_LENGTH) {
      return false;
    }
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
        return false;
      }
    }
    return true;
  }
}
