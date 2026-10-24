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

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import org.sonatype.nexus.security.oidc.OidcConfiguration;

/**
 * Mutable {@link OidcConfiguration} holder used while reading from and writing to the capability store.
 *
 * <p>The client secret is carried here already decrypted; persistence encrypts it before it reaches the
 * capability properties, and reads decrypt it back into this field.</p>
 *
 * @since 3.89
 */
public class OidcConfigurationData
    implements OidcConfiguration
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

  private Map<String, String> exactMatchClaims = new HashMap<>();

  private Map<String, String> authorizationCustomParams = new HashMap<>();

  private Map<String, String> tokenRequestCustomParams = new HashMap<>();

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  @Override
  public String getClientId() {
    return clientId;
  }

  @Override
  public void setClientId(final String clientId) {
    this.clientId = clientId;
  }

  @Override
  public String getClientSecret() {
    return clientSecret;
  }

  @Override
  public void setClientSecret(final String clientSecret) {
    this.clientSecret = clientSecret;
  }

  @Override
  public String getIdpAuthorizationUrl() {
    return idpAuthorizationUrl;
  }

  @Override
  public void setIdpAuthorizationUrl(final String idpAuthorizationUrl) {
    this.idpAuthorizationUrl = idpAuthorizationUrl;
  }

  @Override
  public String getIdpLogoutUrl() {
    return idpLogoutUrl;
  }

  @Override
  public void setIdpLogoutUrl(final String idpLogoutUrl) {
    this.idpLogoutUrl = idpLogoutUrl;
  }

  @Override
  public String getIdpTokenUrl() {
    return idpTokenUrl;
  }

  @Override
  public void setIdpTokenUrl(final String idpTokenUrl) {
    this.idpTokenUrl = idpTokenUrl;
  }

  @Override
  public String getIdpJwksUrl() {
    return idpJwksUrl;
  }

  @Override
  public void setIdpJwksUrl(final String idpJwksUrl) {
    this.idpJwksUrl = idpJwksUrl;
  }

  @Override
  public String getIdpJwks() {
    return idpJwks;
  }

  @Override
  public void setIdpJwks(final String idpJwks) {
    this.idpJwks = idpJwks;
  }

  @Override
  public String getIdpJwsAlgorithm() {
    return idpJwsAlgorithm;
  }

  @Override
  public void setIdpJwsAlgorithm(final String idpJwsAlgorithm) {
    this.idpJwsAlgorithm = idpJwsAlgorithm;
  }

  @Override
  public String getUsernameClaim() {
    return usernameClaim;
  }

  @Override
  public void setUsernameClaim(final String usernameClaim) {
    this.usernameClaim = usernameClaim;
  }

  @Override
  public String getFirstNameClaim() {
    return firstNameClaim;
  }

  @Override
  public void setFirstNameClaim(final String firstNameClaim) {
    this.firstNameClaim = firstNameClaim;
  }

  @Override
  public String getLastNameClaim() {
    return lastNameClaim;
  }

  @Override
  public void setLastNameClaim(final String lastNameClaim) {
    this.lastNameClaim = lastNameClaim;
  }

  @Override
  public String getEmailClaim() {
    return emailClaim;
  }

  @Override
  public void setEmailClaim(final String emailClaim) {
    this.emailClaim = emailClaim;
  }

  @Override
  public String getGroupsClaim() {
    return groupsClaim;
  }

  @Override
  public void setGroupsClaim(final String groupsClaim) {
    this.groupsClaim = groupsClaim;
  }

  @Override
  public Map<String, String> getExactMatchClaims() {
    return exactMatchClaims;
  }

  @Override
  public void setExactMatchClaims(final Map<String, String> exactMatchClaims) {
    this.exactMatchClaims = exactMatchClaims != null ? exactMatchClaims : new HashMap<>();
  }

  @Override
  public Map<String, String> getAuthorizationCustomParams() {
    return authorizationCustomParams;
  }

  @Override
  public void setAuthorizationCustomParams(final Map<String, String> authorizationCustomParams) {
    this.authorizationCustomParams = authorizationCustomParams != null ? authorizationCustomParams : new HashMap<>();
  }

  @Override
  public Map<String, String> getTokenRequestCustomParams() {
    return tokenRequestCustomParams;
  }

  @Override
  public void setTokenRequestCustomParams(final Map<String, String> tokenRequestCustomParams) {
    this.tokenRequestCustomParams = tokenRequestCustomParams != null ? tokenRequestCustomParams : new HashMap<>();
  }

  /**
   * Returns an independent copy of this configuration.
   */
  public OidcConfigurationData copy() {
    OidcConfigurationData copy = new OidcConfigurationData();
    copy.enabled = enabled;
    copy.clientId = clientId;
    copy.clientSecret = clientSecret;
    copy.idpAuthorizationUrl = idpAuthorizationUrl;
    copy.idpLogoutUrl = idpLogoutUrl;
    copy.idpTokenUrl = idpTokenUrl;
    copy.idpJwksUrl = idpJwksUrl;
    copy.idpJwks = idpJwks;
    copy.idpJwsAlgorithm = idpJwsAlgorithm;
    copy.usernameClaim = usernameClaim;
    copy.firstNameClaim = firstNameClaim;
    copy.lastNameClaim = lastNameClaim;
    copy.emailClaim = emailClaim;
    copy.groupsClaim = groupsClaim;
    copy.exactMatchClaims = new HashMap<>(exactMatchClaims);
    copy.authorizationCustomParams = new HashMap<>(authorizationCustomParams);
    copy.tokenRequestCustomParams = new HashMap<>(tokenRequestCustomParams);
    return copy;
  }

  /**
   * Returns a copy with the given client secret, keeping the rest of the fields untouched.
   */
  public OidcConfigurationData withClientSecret(@Nullable final String secret) {
    OidcConfigurationData copy = copy();
    copy.clientSecret = secret;
    return copy;
  }
}
