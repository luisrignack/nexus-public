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
package org.sonatype.nexus.internal.security.oidc.rest;

import java.util.Map;

import javax.annotation.Nullable;

import org.sonatype.nexus.internal.security.oidc.OidcConfigurationData;
import org.sonatype.nexus.internal.security.oidc.OidcConfigurationStoreImpl;
import org.sonatype.nexus.security.oidc.OidcConfiguration;

import io.swagger.annotations.ApiModelProperty;

/**
 * Transfer object for the OAuth2/OIDC configuration screen. Field names match the CoreUI form exactly.
 *
 * <p>The client secret is never returned in plain form: reads expose a placeholder sentinel, and a write
 * that carries the sentinel keeps the stored secret untouched.</p>
 *
 * @since 3.89
 */
public class OidcConfigurationXO
{
  private boolean enabled;

  private String clientId;

  private String clientSecret;

  private String idpAuthorizationUrl;

  private String idpLogoutUrl;

  private String idpTokenUrl;

  private String idpJwksUrl;

  private String idpJwks;

  private String idpJwsAlgorithm;

  private String usernameClaim;

  private String firstNameClaim;

  private String lastNameClaim;

  private String emailClaim;

  private String groupsClaim;

  private Map<String, String> exactMatchClaims;

  private Map<String, String> authorizationCustomParams;

  private Map<String, String> tokenRequestCustomParams;

  public OidcConfigurationXO() {
    // for deserialization
  }

  /**
   * Builds the wire representation from the stored configuration, masking the client secret.
   */
  public static OidcConfigurationXO from(final OidcConfiguration configuration) {
    OidcConfigurationXO xo = new OidcConfigurationXO();
    xo.enabled = configuration.isEnabled();
    xo.clientId = configuration.getClientId();
    xo.clientSecret = hasSecret(configuration.getClientSecret())
        ? OidcConfigurationStoreImpl.CLIENT_SECRET_PLACEHOLDER
        : null;
    xo.idpAuthorizationUrl = configuration.getIdpAuthorizationUrl();
    xo.idpLogoutUrl = configuration.getIdpLogoutUrl();
    xo.idpTokenUrl = configuration.getIdpTokenUrl();
    xo.idpJwksUrl = configuration.getIdpJwksUrl();
    xo.idpJwks = configuration.getIdpJwks();
    xo.idpJwsAlgorithm = configuration.getIdpJwsAlgorithm();
    xo.usernameClaim = configuration.getUsernameClaim();
    xo.firstNameClaim = configuration.getFirstNameClaim();
    xo.lastNameClaim = configuration.getLastNameClaim();
    xo.emailClaim = configuration.getEmailClaim();
    xo.groupsClaim = configuration.getGroupsClaim();
    xo.exactMatchClaims = configuration.getExactMatchClaims();
    xo.authorizationCustomParams = configuration.getAuthorizationCustomParams();
    xo.tokenRequestCustomParams = configuration.getTokenRequestCustomParams();
    return xo;
  }

  /**
   * Builds a configuration model from the wire representation. The client secret is carried through
   * verbatim; the store decides whether it is a new value or the placeholder.
   */
  public OidcConfiguration toConfiguration() {
    OidcConfigurationData data = new OidcConfigurationData();
    data.setEnabled(enabled);
    data.setClientId(clientId);
    data.setClientSecret(clientSecret);
    data.setIdpAuthorizationUrl(idpAuthorizationUrl);
    data.setIdpLogoutUrl(idpLogoutUrl);
    data.setIdpTokenUrl(idpTokenUrl);
    data.setIdpJwksUrl(idpJwksUrl);
    data.setIdpJwks(idpJwks);
    data.setIdpJwsAlgorithm(idpJwsAlgorithm);
    data.setUsernameClaim(usernameClaim);
    data.setFirstNameClaim(firstNameClaim);
    data.setLastNameClaim(lastNameClaim);
    data.setEmailClaim(emailClaim);
    data.setGroupsClaim(groupsClaim);
    data.setExactMatchClaims(exactMatchClaims);
    data.setAuthorizationCustomParams(authorizationCustomParams);
    data.setTokenRequestCustomParams(tokenRequestCustomParams);
    return data;
  }

  private static boolean hasSecret(@Nullable final String secret) {
    return secret != null && !secret.isBlank();
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public String getClientId() {
    return clientId;
  }

  public void setClientId(final String clientId) {
    this.clientId = clientId;
  }

  @ApiModelProperty(value = "The client secret, write-only. Sending the masked placeholder keeps the stored value.")
  public String getClientSecret() {
    return clientSecret;
  }

  public void setClientSecret(final String clientSecret) {
    this.clientSecret = clientSecret;
  }

  public String getIdpAuthorizationUrl() {
    return idpAuthorizationUrl;
  }

  public void setIdpAuthorizationUrl(final String idpAuthorizationUrl) {
    this.idpAuthorizationUrl = idpAuthorizationUrl;
  }

  public String getIdpLogoutUrl() {
    return idpLogoutUrl;
  }

  public void setIdpLogoutUrl(final String idpLogoutUrl) {
    this.idpLogoutUrl = idpLogoutUrl;
  }

  public String getIdpTokenUrl() {
    return idpTokenUrl;
  }

  public void setIdpTokenUrl(final String idpTokenUrl) {
    this.idpTokenUrl = idpTokenUrl;
  }

  public String getIdpJwksUrl() {
    return idpJwksUrl;
  }

  public void setIdpJwksUrl(final String idpJwksUrl) {
    this.idpJwksUrl = idpJwksUrl;
  }

  public String getIdpJwks() {
    return idpJwks;
  }

  public void setIdpJwks(final String idpJwks) {
    this.idpJwks = idpJwks;
  }

  public String getIdpJwsAlgorithm() {
    return idpJwsAlgorithm;
  }

  public void setIdpJwsAlgorithm(final String idpJwsAlgorithm) {
    this.idpJwsAlgorithm = idpJwsAlgorithm;
  }

  public String getUsernameClaim() {
    return usernameClaim;
  }

  public void setUsernameClaim(final String usernameClaim) {
    this.usernameClaim = usernameClaim;
  }

  public String getFirstNameClaim() {
    return firstNameClaim;
  }

  public void setFirstNameClaim(final String firstNameClaim) {
    this.firstNameClaim = firstNameClaim;
  }

  public String getLastNameClaim() {
    return lastNameClaim;
  }

  public void setLastNameClaim(final String lastNameClaim) {
    this.lastNameClaim = lastNameClaim;
  }

  public String getEmailClaim() {
    return emailClaim;
  }

  public void setEmailClaim(final String emailClaim) {
    this.emailClaim = emailClaim;
  }

  public String getGroupsClaim() {
    return groupsClaim;
  }

  public void setGroupsClaim(final String groupsClaim) {
    this.groupsClaim = groupsClaim;
  }

  public Map<String, String> getExactMatchClaims() {
    return exactMatchClaims;
  }

  public void setExactMatchClaims(final Map<String, String> exactMatchClaims) {
    this.exactMatchClaims = exactMatchClaims;
  }

  public Map<String, String> getAuthorizationCustomParams() {
    return authorizationCustomParams;
  }

  public void setAuthorizationCustomParams(final Map<String, String> authorizationCustomParams) {
    this.authorizationCustomParams = authorizationCustomParams;
  }

  public Map<String, String> getTokenRequestCustomParams() {
    return tokenRequestCustomParams;
  }

  public void setTokenRequestCustomParams(final Map<String, String> tokenRequestCustomParams) {
    this.tokenRequestCustomParams = tokenRequestCustomParams;
  }
}
