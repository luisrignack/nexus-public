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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.sonatype.goodies.testsupport.TestSupport;
import org.sonatype.nexus.capability.CapabilityContext;
import org.sonatype.nexus.capability.CapabilityIdentity;
import org.sonatype.nexus.capability.CapabilityReference;
import org.sonatype.nexus.capability.CapabilityRegistry;
import org.sonatype.nexus.common.event.EventManager;
import org.sonatype.nexus.crypto.RandomBytesGenerator;
import org.sonatype.nexus.internal.security.usertoken.UserTokenService.UserToken;
import org.sonatype.nexus.security.authc.apikey.ApiKey;
import org.sonatype.nexus.security.authc.apikey.ApiKeyLowLevelService;
import org.sonatype.nexus.security.usertoken.event.UserTokenConfigChangedEvent;
import org.sonatype.nexus.security.usertoken.event.UserTokenDeletedEvent;
import org.sonatype.nexus.security.usertoken.event.UserTokenEvent;

import com.google.common.base.Predicate;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class UserTokenServiceImplTest
    extends TestSupport
{
  private static final PrincipalCollection PRINCIPALS = new SimplePrincipalCollection("bob", "NexusAuthenticatingRealm");

  private static final char[] TOKEN =
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef".toCharArray();

  @Mock
  private ApiKeyLowLevelService apiKeys;

  @Mock
  private CapabilityRegistry capabilityRegistry;

  @Mock
  private EventManager eventManager;

  @Mock
  private RandomBytesGenerator randomBytesGenerator;

  @Mock
  private CapabilityReference reference;

  @Mock
  private CapabilityContext context;

  @Mock
  private ApiKey apiKey;

  private UserTokenServiceImpl underTest;

  @Before
  public void setUp() {
    underTest = new UserTokenServiceImpl(apiKeys, capabilityRegistry, eventManager, randomBytesGenerator);
    lenient().when(reference.context()).thenReturn(context);
    lenient().when(context.id()).thenReturn(CapabilityIdentity.capabilityIdentity("usertoken"));
    lenient().when(context.notes()).thenReturn("");
    lenient().when(capabilityRegistry.get(any(Predicate.class))).thenReturn(List.of(reference));
  }

  @Test
  public void getConfiguration_withoutCapability_returnsDisabled() {
    when(capabilityRegistry.get(any(Predicate.class))).thenReturn(List.of());

    assertThat(underTest.getConfiguration(), is(UserTokenConfiguration.DISABLED));
    assertFalse(underTest.isEnabled());
    assertFalse(underTest.isProtectContent());
  }

  @Test
  public void getConfiguration_mapsCapabilityProperties() {
    when(context.isActive()).thenReturn(true);
    when(context.properties()).thenReturn(Map.of(
        "protectContent", "true",
        "expirationEnabled", "true",
        "expirationDays", "7"));

    UserTokenConfiguration configuration = underTest.getConfiguration();

    assertTrue(configuration.enabled());
    assertTrue(configuration.protectContent());
    assertTrue(configuration.expirationEnabled());
    assertThat(configuration.expirationDays(), is(7));
  }

  @Test
  public void getConfiguration_invalidExpirationDays_fallsBackToDefault() {
    when(context.isActive()).thenReturn(true);
    when(context.properties()).thenReturn(Map.of(
        "protectContent", "false",
        "expirationEnabled", "false",
        "expirationDays", "not-a-number"));

    assertThat(underTest.getConfiguration().expirationDays(),
        is(UserTokenConfiguration.DEFAULT_EXPIRATION_DAYS));
  }

  @Test
  public void setConfiguration_withoutCapability_throws() {
    when(capabilityRegistry.get(any(Predicate.class))).thenReturn(List.of());

    assertThrows(IllegalStateException.class,
        () -> underTest.setConfiguration(new UserTokenConfiguration(true, true, true, 10)));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void setConfiguration_updatesCapabilityAndPublishesEvent() {
    when(context.properties()).thenReturn(Map.of("custom", "value"));
    when(context.isActive()).thenReturn(true);

    UserTokenConfiguration result = underTest.setConfiguration(
        new UserTokenConfiguration(true, true, true, 10));

    ArgumentCaptor<Map<String, String>> properties = ArgumentCaptor.forClass(Map.class);
    verify(capabilityRegistry).update(
        eq(CapabilityIdentity.capabilityIdentity("usertoken")),
        eq(true),
        eq(""),
        properties.capture());

    assertThat(properties.getValue().get("protectContent"), is("true"));
    assertThat(properties.getValue().get("expirationEnabled"), is("true"));
    assertThat(properties.getValue().get("expirationDays"), is("10"));
    assertThat(properties.getValue().get("custom"), is("value"));
    assertTrue(result.enabled());

    verify(eventManager).post(any(UserTokenConfigChangedEvent.class));
  }

  @Test
  public void createToken_persistsTwoHexHalves() {
    when(context.isActive()).thenReturn(true);
    when(context.properties()).thenReturn(Map.of(
        "protectContent", "false",
        "expirationEnabled", "true",
        "expirationDays", "30"));
    when(randomBytesGenerator.generate(16))
        .thenReturn(hexBytes("000102030405060708090a0b0c0d0e0f"))
        .thenReturn(hexBytes("f0e0d0c0b0a090807060504030201000"));

    UserToken token = underTest.createToken(PRINCIPALS);

    assertThat(token.nameCode(), is("000102030405060708090a0b0c0d0e0f"));
    assertThat(token.passCode(), is("f0e0d0c0b0a090807060504030201000"));
    assertThat(token.combined().length(), is(UserTokenServiceImpl.TOKEN_LENGTH));
    assertThat(token.expiresAt(), is(notNullValue()));
    assertFalse(token.isExpired());

    verify(apiKeys).deleteApiKey(UserTokenService.DOMAIN, PRINCIPALS);
    verify(apiKeys).persistApiKey(
        eq(UserTokenService.DOMAIN), eq(PRINCIPALS), eq(token.combined().toCharArray()), any(OffsetDateTime.class));
    verify(eventManager).post(any(UserTokenEvent.class));
  }

  @Test
  public void createToken_expirationDisabled_hasNoExpiration() {
    when(context.isActive()).thenReturn(true);
    when(context.properties()).thenReturn(Map.of(
        "protectContent", "false",
        "expirationEnabled", "false",
        "expirationDays", "30"));
    when(randomBytesGenerator.generate(16)).thenReturn(hexBytes("000102030405060708090a0b0c0d0e0f"));

    UserToken token = underTest.createToken(PRINCIPALS);

    assertThat(token.expiresAt(), is(nullValue()));
    assertFalse(token.isExpired());
  }

  @Test
  public void getToken_splitsStoredValue() {
    when(apiKey.getApiKey()).thenReturn(TOKEN);
    when(apiKey.getCreated()).thenReturn(OffsetDateTime.now());
    when(apiKeys.getApiKey(UserTokenService.DOMAIN, PRINCIPALS)).thenReturn(Optional.of(apiKey));

    UserToken token = underTest.getToken(PRINCIPALS).orElseThrow();

    assertThat(token.nameCode(), is("0123456789abcdef0123456789abcdef"));
    assertThat(token.passCode(), is("0123456789abcdef0123456789abcdef"));
  }

  @Test
  public void getOrCreateToken_replacesExpiredToken() {
    when(context.isActive()).thenReturn(true);
    when(context.properties()).thenReturn(Map.of(
        "protectContent", "false",
        "expirationEnabled", "true",
        "expirationDays", "1"));
    when(apiKey.getApiKey()).thenReturn(TOKEN);
    when(apiKey.getCreated()).thenReturn(OffsetDateTime.now().minusDays(2));
    when(apiKeys.getApiKey(UserTokenService.DOMAIN, PRINCIPALS)).thenReturn(Optional.of(apiKey));
    when(randomBytesGenerator.generate(16)).thenReturn(hexBytes("000102030405060708090a0b0c0d0e0f"));

    UserToken token = underTest.getOrCreateToken(PRINCIPALS);

    verify(apiKeys).deleteApiKey(UserTokenService.DOMAIN, PRINCIPALS);
    assertThat(token.nameCode(), is("000102030405060708090a0b0c0d0e0f"));
  }

  @Test
  public void authenticate_disabled_isEmpty() {
    when(capabilityRegistry.get(any(Predicate.class))).thenReturn(List.of());

    assertThat(underTest.authenticate(new String(TOKEN)), is(Optional.empty()));
    verifyNoInteractions(apiKeys);
  }

  @Test
  public void authenticate_unknownToken_isEmpty() {
    when(context.isActive()).thenReturn(true);
    when(context.properties()).thenReturn(Map.of("expirationEnabled", "false"));
    when(apiKeys.getApiKeyByToken(eq(UserTokenService.DOMAIN), any(char[].class))).thenReturn(Optional.empty());

    assertThat(underTest.authenticate(new String(TOKEN)), is(Optional.empty()));
  }

  @Test
  public void authenticate_validToken_returnsPrincipals() {
    when(context.isActive()).thenReturn(true);
    when(context.properties()).thenReturn(Map.of("expirationEnabled", "false"));
    when(apiKeys.getApiKeyByToken(eq(UserTokenService.DOMAIN), any(char[].class)))
        .thenReturn(Optional.of(apiKey));
    when(apiKey.getPrincipals()).thenReturn(PRINCIPALS);

    assertThat(underTest.authenticate(new String(TOKEN)), is(Optional.of(PRINCIPALS)));
  }

  @Test
  public void authenticate_expiredToken_isDeleted() {
    when(context.isActive()).thenReturn(true);
    when(context.properties()).thenReturn(Map.of("expirationEnabled", "true", "expirationDays", "1"));
    when(apiKeys.getApiKeyByToken(eq(UserTokenService.DOMAIN), any(char[].class)))
        .thenReturn(Optional.of(apiKey));
    when(apiKey.getCreated()).thenReturn(OffsetDateTime.now().minusDays(5));
    when(apiKey.getPrincipals()).thenReturn(PRINCIPALS);

    assertThat(underTest.authenticate(new String(TOKEN)), is(Optional.empty()));

    verify(apiKeys).deleteApiKey(UserTokenService.DOMAIN, PRINCIPALS);
  }

  @Test
  public void deleteToken_whenNothingDeleted_doesNotPublishEvent() {
    when(apiKeys.deleteApiKey(UserTokenService.DOMAIN, PRINCIPALS)).thenReturn(0);

    assertFalse(underTest.deleteToken(PRINCIPALS));
    verify(eventManager, never()).post(any(UserTokenDeletedEvent.class));
  }

  @Test
  public void deleteTokensForUser_onlyDeletesMatchingUser() {
    ApiKey other = org.mockito.Mockito.mock(ApiKey.class);
    PrincipalCollection otherPrincipals = new SimplePrincipalCollection("alice", "NexusAuthenticatingRealm");
    when(apiKey.getPrimaryPrincipal()).thenReturn("bob");
    when(apiKey.getPrincipals()).thenReturn(PRINCIPALS);
    when(other.getPrimaryPrincipal()).thenReturn("alice");
    when(other.getPrincipals()).thenReturn(otherPrincipals);
    when(apiKeys.browse(UserTokenService.DOMAIN)).thenReturn(List.of(apiKey, other));
    when(apiKeys.deleteApiKey(UserTokenService.DOMAIN, PRINCIPALS)).thenReturn(1);

    assertThat(underTest.deleteTokensForUser("bob"), is(1));

    verify(apiKeys).deleteApiKey(UserTokenService.DOMAIN, PRINCIPALS);
    verify(apiKeys, never()).deleteApiKey(eq(UserTokenService.DOMAIN), eq(otherPrincipals));
    verify(eventManager).post(any(UserTokenDeletedEvent.class));
  }

  @Test
  public void deleteAllTokens_publishesEventOnlyWhenSomethingWasDeleted() {
    when(apiKeys.deleteApiKeys(UserTokenService.DOMAIN)).thenReturn(0);
    assertThat(underTest.deleteAllTokens(), is(0));
    verify(eventManager, never()).post(any(UserTokenDeletedEvent.class));

    when(apiKeys.deleteApiKeys(UserTokenService.DOMAIN)).thenReturn(3);
    assertThat(underTest.deleteAllTokens(), is(3));
    verify(eventManager).post(any(UserTokenDeletedEvent.class));
  }

  private static byte[] hexBytes(final String hex) {
    byte[] bytes = new byte[hex.length() / 2];
    for (int i = 0; i < bytes.length; i++) {
      bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
    }
    return bytes;
  }
}
