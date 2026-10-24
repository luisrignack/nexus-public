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
package org.sonatype.nexus.security.oidc;

import java.util.Map;

/**
 * OAuth2/OIDC single sign-on configuration.
 *
 * <p>The fields mirror the OAuth2 administration screen of the CoreUI, which stores and reads them
 * from {@code /service/rest/internal/oauth2}.</p>
 *
 * @since 3.89
 */
public interface OidcConfiguration
{
  boolean isEnabled();

  void setEnabled(boolean enabled);

  String getClientId();

  void setClientId(String clientId);

  /**
   * Encrypted client secret as produced by {@code SecretsService.encrypt}.
   */
  String getClientSecret();

  void setClientSecret(String clientSecret);

  String getIdpAuthorizationUrl();

  void setIdpAuthorizationUrl(String idpAuthorizationUrl);

  String getIdpLogoutUrl();

  void setIdpLogoutUrl(String idpLogoutUrl);

  String getIdpTokenUrl();

  void setIdpTokenUrl(String idpTokenUrl);

  String getIdpJwksUrl();

  void setIdpJwksUrl(String idpJwksUrl);

  /**
   * Inline JWKS JSON document; takes precedence over {@link #getIdpJwksUrl()} when non-blank.
   */
  String getIdpJwks();

  void setIdpJwks(String idpJwks);

  /**
   * Expected JWS algorithm, for example {@code RS256}.
   */
  String getIdpJwsAlgorithm();

  void setIdpJwsAlgorithm(String idpJwsAlgorithm);

  String getUsernameClaim();

  void setUsernameClaim(String usernameClaim);

  String getFirstNameClaim();

  void setFirstNameClaim(String firstNameClaim);

  String getLastNameClaim();

  void setLastNameClaim(String lastNameClaim);

  String getEmailClaim();

  void setEmailClaim(String emailClaim);

  String getGroupsClaim();

  void setGroupsClaim(String groupsClaim);

  /**
   * Claims that must match exactly for the token to be accepted, keyed by claim name.
   */
  Map<String, String> getExactMatchClaims();

  void setExactMatchClaims(Map<String, String> exactMatchClaims);

  Map<String, String> getAuthorizationCustomParams();

  void setAuthorizationCustomParams(Map<String, String> authorizationCustomParams);

  Map<String, String> getTokenRequestCustomParams();

  void setTokenRequestCustomParams(Map<String, String> tokenRequestCustomParams);
}
