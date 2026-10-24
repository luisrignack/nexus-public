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

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.capability.CapabilityReference;
import org.sonatype.nexus.capability.CapabilityReferenceFilterBuilder;
import org.sonatype.nexus.capability.CapabilityRegistry;
import org.sonatype.nexus.common.event.EventManager;
import org.sonatype.nexus.crypto.RandomBytesGenerator;
import org.sonatype.nexus.security.authc.apikey.ApiKey;
import org.sonatype.nexus.security.authc.apikey.ApiKeyLowLevelService;
import org.sonatype.nexus.security.usertoken.event.UserTokenConfigChangedEvent;
import org.sonatype.nexus.security.usertoken.event.UserTokenDeletedEvent;
import org.sonatype.nexus.security.usertoken.event.UserTokenEvent;
import org.sonatype.nexus.security.usertoken.event.UserTokenEvent.UserTokenEventTypes;

import org.apache.shiro.subject.PrincipalCollection;
import org.springframework.stereotype.Component;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Default {@link UserTokenService}, backed by the shared API key tables.
 *
 * <p>Tokens are stored as a single value made of two equal halves: an access (name) code and a secret
 * (pass) code. Splitting them keeps compatibility with the API key storage, which uses the first half
 * as an indexable access key and encrypts the second half at rest.</p>
 *
 * @since 3.89
 */
@Component
@Singleton
public class UserTokenServiceImpl
    extends ComponentSupport
    implements UserTokenService
{
  static final int CODE_LENGTH = 32;

  static final int TOKEN_LENGTH = CODE_LENGTH * 2;

  private static final String PROTECT_CONTENT = "protectContent";

  private static final String EXPIRATION_ENABLED = "expirationEnabled";

  private static final String EXPIRATION_DAYS = "expirationDays";

  private final ApiKeyLowLevelService apiKeys;

  private final CapabilityRegistry capabilityRegistry;

  private final EventManager eventManager;

  private final RandomBytesGenerator randomBytesGenerator;

  @Inject
  public UserTokenServiceImpl(
      final ApiKeyLowLevelService apiKeys,
      final CapabilityRegistry capabilityRegistry,
      final EventManager eventManager,
      final RandomBytesGenerator randomBytesGenerator)
  {
    this.apiKeys = checkNotNull(apiKeys);
    this.capabilityRegistry = checkNotNull(capabilityRegistry);
    this.eventManager = checkNotNull(eventManager);
    this.randomBytesGenerator = checkNotNull(randomBytesGenerator);
  }

  @Override
  public UserTokenConfiguration getConfiguration() {
    return findCapability()
        .map(reference -> {
          Map<String, String> properties = reference.context().properties();
          return new UserTokenConfiguration(
              reference.context().isActive(),
              Boolean.parseBoolean(properties.get(PROTECT_CONTENT)),
              Boolean.parseBoolean(properties.get(EXPIRATION_ENABLED)),
              parseInt(properties.get(EXPIRATION_DAYS)));
        })
        .orElse(UserTokenConfiguration.DISABLED);
  }

  @Override
  public UserTokenConfiguration setConfiguration(final UserTokenConfiguration configuration) {
    checkNotNull(configuration);

    CapabilityReference reference = findCapability()
        .orElseThrow(() -> new IllegalStateException("User token capability is not available"));

    Map<String, String> properties = new HashMap<>(reference.context().properties());
    properties.put(PROTECT_CONTENT, Boolean.toString(configuration.protectContent()));
    properties.put(EXPIRATION_ENABLED, Boolean.toString(configuration.expirationEnabled()));
    properties.put(EXPIRATION_DAYS, Integer.toString(configuration.expirationDays()));

    capabilityRegistry.update(
        reference.context().id(),
        configuration.enabled(),
        reference.context().notes(),
        properties);

    eventManager.post(new UserTokenConfigChangedEvent(
        configuration.enabled(),
        configuration.protectContent(),
        configuration.expirationEnabled(),
        configuration.expirationDays()));

    return getConfiguration();
  }

  @Override
  public boolean isEnabled() {
    return getConfiguration().enabled();
  }

  @Override
  public boolean isProtectContent() {
    return getConfiguration().protectContent();
  }

  @Override
  public Optional<UserToken> getToken(final PrincipalCollection principals) {
    checkNotNull(principals);
    return apiKeys.getApiKey(DOMAIN, principals).map(this::toUserToken);
  }

  @Override
  public UserToken getOrCreateToken(final PrincipalCollection principals) {
    return getToken(principals)
        .filter(token -> !token.isExpired())
        .orElseGet(() -> createToken(principals));
  }

  @Override
  public UserToken createToken(final PrincipalCollection principals) {
    checkNotNull(principals);

    apiKeys.deleteApiKey(DOMAIN, principals);

    String nameCode = randomCode();
    String passCode = randomCode();
    String token = nameCode + passCode;
    OffsetDateTime created = OffsetDateTime.now();

    apiKeys.persistApiKey(DOMAIN, principals, token.toCharArray(), created);

    eventManager.post(new UserTokenEvent(UserTokenEventTypes.CREATED));

    return new UserToken(nameCode, passCode, created, expirationOf(created));
  }

  @Override
  public boolean deleteToken(final PrincipalCollection principals) {
    checkNotNull(principals);

    int deleted = apiKeys.deleteApiKey(DOMAIN, principals);
    if (deleted > 0) {
      eventManager.post(new UserTokenDeletedEvent(
          UserTokenEventTypes.DELETED,
          deleted,
          principals.getPrimaryPrincipal().toString()));
      return true;
    }
    return false;
  }

  @Override
  public int deleteTokensForUser(final String userId) {
    checkNotNull(userId);

    int deleted = 0;
    for (ApiKey key : apiKeys.browse(DOMAIN)) {
      if (userId.equals(key.getPrimaryPrincipal())) {
        deleted += apiKeys.deleteApiKey(DOMAIN, key.getPrincipals());
      }
    }

    if (deleted > 0) {
      eventManager.post(new UserTokenDeletedEvent(UserTokenEventTypes.DELETED, deleted, userId));
    }
    return deleted;
  }

  @Override
  public int deleteAllTokens() {
    int deleted = apiKeys.deleteApiKeys(DOMAIN);
    if (deleted > 0) {
      eventManager.post(new UserTokenDeletedEvent(UserTokenEventTypes.ALL_DELETED, deleted));
    }
    return deleted;
  }

  @Override
  public Optional<PrincipalCollection> authenticate(final String token) {
    if (token == null || !isEnabled()) {
      return Optional.empty();
    }

    Optional<ApiKey> key = apiKeys.getApiKeyByToken(DOMAIN, token.toCharArray());
    if (key.isEmpty()) {
      return Optional.empty();
    }

    UserTokenConfiguration configuration = getConfiguration();
    OffsetDateTime created = Optional.ofNullable(key.get().getCreated()).orElse(OffsetDateTime.now());
    OffsetDateTime expiresAt = expirationOf(configuration, created);
    if (expiresAt != null && expiresAt.isBefore(OffsetDateTime.now())) {
      log.debug("Rejecting expired user token for {}", key.get().getPrimaryPrincipal());
      apiKeys.deleteApiKey(DOMAIN, key.get().getPrincipals());
      return Optional.empty();
    }

    return key.map(ApiKey::getPrincipals);
  }

  private Optional<CapabilityReference> findCapability() {
    return capabilityRegistry.get(
            CapabilityReferenceFilterBuilder.capabilities()
                .withType(UserTokenCapability.TYPE)
                .includeNotExposed())
        .stream()
        .map(CapabilityReference.class::cast)
        .findFirst();
  }

  private UserToken toUserToken(final ApiKey key) {
    String token = new String(key.getApiKey());
    int split = token.length() / 2;
    String nameCode = token.substring(0, split);
    String passCode = token.substring(split);
    OffsetDateTime created = Optional.ofNullable(key.getCreated()).orElse(OffsetDateTime.now());
    return new UserToken(nameCode, passCode, created, expirationOf(created));
  }

  private OffsetDateTime expirationOf(final OffsetDateTime created) {
    return expirationOf(getConfiguration(), created);
  }

  private OffsetDateTime expirationOf(final UserTokenConfiguration configuration, final OffsetDateTime created) {
    if (!configuration.expirationEnabled()) {
      return null;
    }
    return created.plusDays(configuration.expirationDays());
  }

  private String randomCode() {
    return java.util.HexFormat.of().formatHex(randomBytesGenerator.generate(CODE_LENGTH / 2));
  }

  private static int parseInt(final String value) {
    if (value == null || value.isBlank()) {
      return UserTokenConfiguration.DEFAULT_EXPIRATION_DAYS;
    }
    try {
      return Integer.parseInt(value);
    }
    catch (NumberFormatException e) {
      return UserTokenConfiguration.DEFAULT_EXPIRATION_DAYS;
    }
  }
}
