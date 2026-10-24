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
import java.util.Optional;

import javax.annotation.Nullable;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.sonatype.nexus.capability.CapabilityReference;
import org.sonatype.nexus.capability.CapabilityReferenceFilterBuilder;
import org.sonatype.nexus.capability.CapabilityRegistry;
import org.sonatype.nexus.crypto.secrets.Secret;
import org.sonatype.nexus.crypto.secrets.SecretsService;
import org.sonatype.nexus.security.oidc.OidcConfiguration;
import org.sonatype.nexus.security.oidc.OidcConfigurationStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * {@link OidcConfigurationStore} backed by the {@code oidc} capability.
 *
 * <p>The client secret is the only secret piece of the configuration. It is encrypted through
 * {@link SecretsService} before it reaches the capability properties and decrypted on read, so the
 * capability store only ever holds the opaque secret token, never the plain value.</p>
 *
 * @since 3.89
 */
@Component
@Singleton
public class OidcConfigurationStoreImpl
    implements OidcConfigurationStore
{
  private static final Logger log = LoggerFactory.getLogger(OidcConfigurationStoreImpl.class);

  private static final String SECRET_PURPOSE = "oidc";

  /**
   * Sentinel stored in the UI-facing client secret field so a round-trip through the form does not
   * overwrite the persisted secret with a blank value.
   */
  public static final String CLIENT_SECRET_PLACEHOLDER = "#~NXRM~PLACEHOLDER~OAUTH2~SECRET~#";

  private final CapabilityRegistry capabilityRegistry;

  private final SecretsService secretsService;

  @Inject
  public OidcConfigurationStoreImpl(final CapabilityRegistry capabilityRegistry, final SecretsService secretsService) {
    this.capabilityRegistry = checkNotNull(capabilityRegistry);
    this.secretsService = checkNotNull(secretsService);
  }

  @Override
  public OidcConfiguration newEntity() {
    OidcConfigurationData data = new OidcConfigurationData();
    data.setEnabled(false);
    data.setIdpJwsAlgorithm("RS256");
    data.setUsernameClaim("sub");
    return data;
  }

  @Nullable
  @Override
  public OidcConfiguration load() {
    return findCapability().map(this::toConfiguration).orElse(null);
  }

  @Override
  public void save(final OidcConfiguration configuration) {
    checkNotNull(configuration);

    CapabilityReference reference = findCapability()
        .orElseThrow(() -> new IllegalStateException("OIDC capability is not available"));

    Map<String, String> properties = new HashMap<>();
    properties.put(OidcCapabilityProperties.ENABLED, Boolean.toString(configuration.isEnabled()));
    putIfNotNull(properties, OidcCapabilityProperties.CLIENT_ID, configuration.getClientId());
    putIfNotNull(properties, OidcCapabilityProperties.IDP_AUTHORIZATION_URL, configuration.getIdpAuthorizationUrl());
    putIfNotNull(properties, OidcCapabilityProperties.IDP_LOGOUT_URL, configuration.getIdpLogoutUrl());
    putIfNotNull(properties, OidcCapabilityProperties.IDP_TOKEN_URL, configuration.getIdpTokenUrl());
    putIfNotNull(properties, OidcCapabilityProperties.IDP_JWKS_URL, configuration.getIdpJwksUrl());
    putIfNotNull(properties, OidcCapabilityProperties.IDP_JWKS, configuration.getIdpJwks());
    putIfNotNull(properties, OidcCapabilityProperties.IDP_JWS_ALGORITHM, configuration.getIdpJwsAlgorithm());
    putIfNotNull(properties, OidcCapabilityProperties.USERNAME_CLAIM, configuration.getUsernameClaim());
    putIfNotNull(properties, OidcCapabilityProperties.FIRST_NAME_CLAIM, configuration.getFirstNameClaim());
    putIfNotNull(properties, OidcCapabilityProperties.LAST_NAME_CLAIM, configuration.getLastNameClaim());
    putIfNotNull(properties, OidcCapabilityProperties.EMAIL_CLAIM, configuration.getEmailClaim());
    putIfNotNull(properties, OidcCapabilityProperties.GROUPS_CLAIM, configuration.getGroupsClaim());
    OidcCapabilityProperties.writeMap(
        properties,
        OidcCapabilityProperties.EXACT_MATCH_CLAIMS,
        configuration.getExactMatchClaims());
    OidcCapabilityProperties.writeMap(
        properties,
        OidcCapabilityProperties.AUTHORIZATION_CUSTOM_PARAMS,
        configuration.getAuthorizationCustomParams());
    OidcCapabilityProperties.writeMap(
        properties,
        OidcCapabilityProperties.TOKEN_REQUEST_CUSTOM_PARAMS,
        configuration.getTokenRequestCustomParams());

    String secretToken = resolveSecretToken(reference, configuration.getClientSecret());
    if (secretToken != null) {
      properties.put(OidcCapabilityProperties.CLIENT_SECRET, secretToken);
    }

    capabilityRegistry.update(
        reference.context().id(),
        configuration.isEnabled(),
        reference.context().notes(),
        properties);

    log.info("OIDC configuration saved (enabled={})", configuration.isEnabled());
  }

  /**
   * Decides which secret token to persist. A null, blank or placeholder incoming secret keeps the token
   * already stored; any other value is treated as a new plain secret and encrypted.
   */
  @Nullable
  private String resolveSecretToken(final CapabilityReference reference, @Nullable final String incomingSecret) {
    String existing = reference.context().properties().get(OidcCapabilityProperties.CLIENT_SECRET);

    if (incomingSecret == null
        || incomingSecret.isBlank()
        || CLIENT_SECRET_PLACEHOLDER.equals(incomingSecret)) {
      return existing;
    }

    try {
      Secret encrypted = secretsService.encrypt(SECRET_PURPOSE, incomingSecret.toCharArray(), null);
      // Drop the previous token so the secrets store does not accumulate orphans.
      if (existing != null && !existing.isBlank()) {
        try {
          secretsService.remove(secretsService.from(existing));
        }
        catch (Exception e) {
          log.debug("Unable to remove previous OIDC client secret", e);
        }
      }
      return encrypted.getId();
    }
    catch (Exception e) {
      throw new IllegalStateException("Unable to encrypt the OIDC client secret", e);
    }
  }

  private OidcConfiguration toConfiguration(final CapabilityReference reference) {
    Map<String, String> properties = reference.context().properties();

    OidcConfigurationData data = new OidcConfigurationData();
    data.setEnabled(reference.context().isActive());
    data.setClientId(properties.get(OidcCapabilityProperties.CLIENT_ID));
    data.setIdpAuthorizationUrl(properties.get(OidcCapabilityProperties.IDP_AUTHORIZATION_URL));
    data.setIdpLogoutUrl(properties.get(OidcCapabilityProperties.IDP_LOGOUT_URL));
    data.setIdpTokenUrl(properties.get(OidcCapabilityProperties.IDP_TOKEN_URL));
    data.setIdpJwksUrl(properties.get(OidcCapabilityProperties.IDP_JWKS_URL));
    data.setIdpJwks(properties.get(OidcCapabilityProperties.IDP_JWKS));
    data.setIdpJwsAlgorithm(properties.get(OidcCapabilityProperties.IDP_JWS_ALGORITHM));
    data.setUsernameClaim(properties.get(OidcCapabilityProperties.USERNAME_CLAIM));
    data.setFirstNameClaim(properties.get(OidcCapabilityProperties.FIRST_NAME_CLAIM));
    data.setLastNameClaim(properties.get(OidcCapabilityProperties.LAST_NAME_CLAIM));
    data.setEmailClaim(properties.get(OidcCapabilityProperties.EMAIL_CLAIM));
    data.setGroupsClaim(properties.get(OidcCapabilityProperties.GROUPS_CLAIM));
    data.setExactMatchClaims(OidcCapabilityProperties.readMap(
        properties,
        OidcCapabilityProperties.EXACT_MATCH_CLAIMS));
    data.setAuthorizationCustomParams(OidcCapabilityProperties.readMap(
        properties,
        OidcCapabilityProperties.AUTHORIZATION_CUSTOM_PARAMS));
    data.setTokenRequestCustomParams(OidcCapabilityProperties.readMap(
        properties,
        OidcCapabilityProperties.TOKEN_REQUEST_CUSTOM_PARAMS));

    String secretToken = properties.get(OidcCapabilityProperties.CLIENT_SECRET);
    data.setClientSecret(decryptSecret(secretToken));
    return data;
  }

  /**
   * Decrypts the stored secret token back to its plain value. A missing or undecryptable token yields
   * {@code null} so the rest of the configuration still loads.
   */
  @Nullable
  private String decryptSecret(@Nullable final String secretToken) {
    if (secretToken == null || secretToken.isBlank()) {
      return null;
    }
    try {
      Secret secret = secretsService.from(secretToken);
      char[] plain = secret.decrypt();
      return plain != null ? new String(plain) : null;
    }
    catch (Exception e) {
      log.warn("Unable to decrypt the OIDC client secret", e);
      return null;
    }
  }

  private static void putIfNotNull(final Map<String, String> properties, final String key, @Nullable final String value) {
    if (value != null) {
      properties.put(key, value);
    }
  }

  private Optional<CapabilityReference> findCapability() {
    return capabilityRegistry.get(
            CapabilityReferenceFilterBuilder.capabilities()
                .withType(OidcCapability.TYPE)
                .includeNotExposed())
        .stream()
        .map(CapabilityReference.class::cast)
        .findFirst();
  }
}
