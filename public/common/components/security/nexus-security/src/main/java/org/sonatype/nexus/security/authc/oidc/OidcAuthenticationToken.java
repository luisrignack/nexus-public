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
package org.sonatype.nexus.security.authc.oidc;

import java.util.List;
import java.util.Map;

import org.apache.shiro.authc.AuthenticationToken;

/**
 * Shiro {@link AuthenticationToken} for an already validated OIDC ID token.
 *
 * <p>The token is created after the {@code id_token} signature, expiration, audience and required
 * claims have been verified; the realm only has to provision the user and map groups to roles.</p>
 *
 * @since 3.89
 */
public class OidcAuthenticationToken
    implements AuthenticationToken
{
  private final String userId;

  private final Map<String, Object> claims;

  private final List<String> groups;

  public OidcAuthenticationToken(final String userId, final Map<String, Object> claims, final List<String> groups) {
    this.userId = userId;
    this.claims = claims;
    this.groups = groups;
  }

  @Override
  public Object getPrincipal() {
    return userId;
  }

  @Override
  public Object getCredentials() {
    return claims;
  }

  public String getUserId() {
    return userId;
  }

  public Map<String, Object> getClaims() {
    return claims;
  }

  public List<String> getGroups() {
    return groups;
  }

  public String claim(final String name) {
    Object value = claims.get(name);
    return value instanceof String ? (String) value : null;
  }
}
