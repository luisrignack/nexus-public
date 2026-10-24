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
import java.util.List;
import java.util.Map;

import org.sonatype.goodies.testsupport.TestSupport;
import org.sonatype.nexus.capability.CapabilityContext;
import org.sonatype.nexus.capability.CapabilityIdentity;
import org.sonatype.nexus.capability.CapabilityReference;
import org.sonatype.nexus.capability.CapabilityRegistry;
import org.sonatype.nexus.crypto.secrets.Secret;
import org.sonatype.nexus.crypto.secrets.SecretsService;
import org.sonatype.nexus.security.oidc.OidcConfiguration;

import com.google.common.base.Predicate;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises the capability-backed {@link OidcConfigurationStore}: round-trip of every field, encryption
 * of the client secret through {@link SecretsService} and the placeholder contract that keeps the stored
 * secret when the admin form resubmits its masked value.
 */
public class OidcConfigurationStoreImplTest
    extends TestSupport
{
  private static final String SECRET_TOKEN = "stored-token-1";

  @Mock
  private CapabilityRegistry capabilityRegistry;

  @Mock
  private SecretsService secretsService;

  @Mock
  private CapabilityReference reference;

  @Mock
  private CapabilityContext context;

  private final Map<String, String> properties = new HashMap<>();

  private OidcConfigurationStoreImpl store;

  @Before
  public void setup() throws Exception {
    when(reference.context()).thenReturn(context);
    when(context.properties()).thenReturn(properties);
    when(context.id()).thenReturn(new CapabilityIdentity("oidc.1"));
    when(context.notes()).thenReturn("notes");
    when(context.isActive()).thenReturn(true);
    when(capabilityRegistry.get(any(Predicate.class))).thenReturn(List.of(reference));

    store = new OidcConfigurationStoreImpl(capabilityRegistry, secretsService);
  }

  @Test
  public void loadReturnsNullWhenCapabilityMissing() {
    when(capabilityRegistry.get(any(Predicate.class))).thenReturn(List.of());

    assertThat(store.load(), nullValue());
  }

  @Test
  public void loadRoundTripsFieldsAndDecryptsSecret() throws Exception {
    properties.put(OidcCapabilityProperties.CLIENT_ID, "test-client");
    properties.put(OidcCapabilityProperties.CLIENT_SECRET, SECRET_TOKEN);
    properties.put(OidcCapabilityProperties.IDP_AUTHORIZATION_URL, "https://idp.example/realms/x/protocol/openid-connect/auth");
    properties.put(OidcCapabilityProperties.IDP_TOKEN_URL, "https://idp.example/realms/x/protocol/openid-connect/token");
    properties.put(OidcCapabilityProperties.IDP_JWKS_URL, "https://idp.example/realms/x/protocol/openid-connect/certs");
    properties.put(OidcCapabilityProperties.USERNAME_CLAIM, "preferred_username");
    properties.put(OidcCapabilityProperties.GROUPS_CLAIM, "groups");
    properties.put(OidcCapabilityProperties.EXACT_MATCH_CLAIMS, "{\"iss\":\"https://idp.example/realms/x\"}");
    properties.put(OidcCapabilityProperties.AUTHORIZATION_CUSTOM_PARAMS, "{\"prompt\":\"login\"}");
    properties.put(OidcCapabilityProperties.TOKEN_REQUEST_CUSTOM_PARAMS, "{\"audience\":\"api\"}");

    Secret secret = mock(Secret.class);
    when(secretsService.from(SECRET_TOKEN)).thenReturn(secret);
    when(secret.decrypt()).thenReturn("plain-secret".toCharArray());

    OidcConfiguration loaded = store.load();

    assertThat(loaded.isEnabled(), is(true));
    assertThat(loaded.getClientId(), is("test-client"));
    assertThat(loaded.getClientSecret(), is("plain-secret"));
    assertThat(loaded.getUsernameClaim(), is("preferred_username"));
    assertThat(loaded.getGroupsClaim(), is("groups"));
    assertThat(loaded.getExactMatchClaims().get("iss"), is("https://idp.example/realms/x"));
    assertThat(loaded.getAuthorizationCustomParams().get("prompt"), is("login"));
    assertThat(loaded.getTokenRequestCustomParams().get("audience"), is("api"));
  }

  @Test
  public void loadToleratesUndecryptableSecret() throws Exception {
    properties.put(OidcCapabilityProperties.CLIENT_ID, "test-client");
    properties.put(OidcCapabilityProperties.CLIENT_SECRET, SECRET_TOKEN);

    when(secretsService.from(SECRET_TOKEN)).thenThrow(new IllegalStateException("secret unavailable"));

    OidcConfiguration loaded = store.load();

    // the rest of the configuration must still load
    assertThat(loaded.getClientId(), is("test-client"));
    assertThat(loaded.getClientSecret(), nullValue());
  }

  @Test
  public void saveEncryptsNewSecretAndPersistsToken() throws Exception {
    Secret encrypted = mock(Secret.class);
    when(encrypted.getId()).thenReturn("stored-token-2");
    when(secretsService.encrypt(eq("oidc"), any(char[].class), isNull())).thenReturn(encrypted);

    OidcConfiguration configuration = store.newEntity();
    configuration.setEnabled(true);
    configuration.setClientId("test-client");
    configuration.setClientSecret("brand-new-secret");

    store.save(configuration);

    ArgumentCaptor<Map<String, String>> saved = mapCaptor();
    verify(capabilityRegistry).update(eq(new CapabilityIdentity("oidc.1")), eq(true), eq("notes"), saved.capture());

    Map<String, String> savedProperties = saved.getValue();
    assertThat(savedProperties.get(OidcCapabilityProperties.CLIENT_SECRET), is("stored-token-2"));
    assertThat(savedProperties.get(OidcCapabilityProperties.CLIENT_ID), is("test-client"));
    // the plain secret must never reach the capability store
    assertThat(savedProperties.containsValue("brand-new-secret"), is(false));
  }

  @Test
  public void saveWithPlaceholderKeepsStoredSecret() throws Exception {
    properties.put(OidcCapabilityProperties.CLIENT_SECRET, SECRET_TOKEN);

    OidcConfiguration configuration = store.newEntity();
    configuration.setClientId("test-client");
    configuration.setClientSecret(OidcConfigurationStoreImpl.CLIENT_SECRET_PLACEHOLDER);

    store.save(configuration);

    verify(secretsService, never()).encrypt(anyString(), any(char[].class), any());

    ArgumentCaptor<Map<String, String>> saved = mapCaptor();
    verify(capabilityRegistry).update(eq(new CapabilityIdentity("oidc.1")), eq(false), eq("notes"), saved.capture());
    assertThat(saved.getValue().get(OidcCapabilityProperties.CLIENT_SECRET), is(SECRET_TOKEN));
  }

  @Test
  public void saveWithBlankSecretKeepsStoredSecret() throws Exception {
    properties.put(OidcCapabilityProperties.CLIENT_SECRET, SECRET_TOKEN);

    OidcConfiguration configuration = store.newEntity();
    configuration.setClientId("test-client");
    configuration.setClientSecret("   ");

    store.save(configuration);

    verify(secretsService, never()).encrypt(anyString(), any(char[].class), any());

    ArgumentCaptor<Map<String, String>> saved = mapCaptor();
    verify(capabilityRegistry).update(eq(new CapabilityIdentity("oidc.1")), eq(false), eq("notes"), saved.capture());
    assertThat(saved.getValue().get(OidcCapabilityProperties.CLIENT_SECRET), is(SECRET_TOKEN));
  }

  @Test
  public void saveReplacingSecretRemovesPreviousToken() throws Exception {
    properties.put(OidcCapabilityProperties.CLIENT_SECRET, "stored-token-old");

    Secret previous = mock(Secret.class);
    when(secretsService.from("stored-token-old")).thenReturn(previous);

    Secret encrypted = mock(Secret.class);
    when(encrypted.getId()).thenReturn("stored-token-new");
    when(secretsService.encrypt(eq("oidc"), any(char[].class), isNull())).thenReturn(encrypted);

    OidcConfiguration configuration = store.newEntity();
    configuration.setClientId("test-client");
    configuration.setClientSecret("rotated-secret");

    store.save(configuration);

    verify(secretsService).remove(previous);

    ArgumentCaptor<Map<String, String>> saved = mapCaptor();
    verify(capabilityRegistry).update(eq(new CapabilityIdentity("oidc.1")), eq(false), eq("notes"), saved.capture());
    assertThat(saved.getValue().get(OidcCapabilityProperties.CLIENT_SECRET), is("stored-token-new"));
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private ArgumentCaptor<Map<String, String>> mapCaptor() {
    return ArgumentCaptor.forClass((Class) Map.class);
  }
}
