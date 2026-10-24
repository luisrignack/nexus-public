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

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThrows;

/**
 * Exercises {@link OidcService} against a real local HTTP server acting as the IdP, with a real RSA key
 * signing the {@code id_token}. This validates the whole client path end to end without any external
 * dependency: authorization URL, code exchange, JWKS resolution, and claim verification.
 */
public class OidcServiceTest
{
  private HttpServer idp;

  private String baseUrl;

  private RSAPublicKey publicKey;

  private RSAPrivateKey privateKey;

  private static final String KEY_ID = "test-key-1";

  private OidcService service;

  @Before
  public void setUp() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair keyPair = generator.generateKeyPair();
    publicKey = (RSAPublicKey) keyPair.getPublic();
    privateKey = (RSAPrivateKey) keyPair.getPrivate();

    idp = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    idp.start();
    baseUrl = "http://127.0.0.1:" + idp.getAddress().getPort();

    service = new OidcService(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
  }

  @After
  public void tearDown() {
    if (idp != null) {
      idp.stop(0);
    }
  }

  @Test
  public void buildAuthorizationUrl_includesFlowParameters() {
    OidcConfigurationData configuration = configuration();
    configuration.setAuthorizationCustomParams(Map.of("prompt", "login", "audience", "https://api.example"));

    String url = service.buildAuthorizationUrl(configuration, "https://nexus.example/service/rest/v1/security/oidc/callback",
        "state-123", "nonce-456");

    assertThat(url, containsString(baseUrl + "/authorize?"));
    assertThat(url, containsString("response_type=code"));
    assertThat(url, containsString("client_id=test-client"));
    assertThat(url, containsString("state=state-123"));
    assertThat(url, containsString("nonce=nonce-456"));
    assertThat(url, containsString("scope=openid"));
    // custom params are appended and override defaults on collision
    assertThat(url, containsString("prompt=login"));
    assertThat(url, containsString("audience=https%3A%2F%2Fapi.example"));
  }

  @Test
  public void validateIdToken_acceptsSignedTokenWithMatchingNonce() throws Exception {
    String jwks = jwksDocument();
    stub("/jwks", jwks);

    String raw = signToken("alice", "nonce-456", jwks);
    OidcConfigurationData configuration = configuration();
    configuration.setIdpJwks(jwks);

    Map<String, Object> claims = service.validateIdToken(configuration, raw, "nonce-456");

    assertThat(claims.get("sub"), is("alice"));
    assertThat(claims.get("nonce"), is("nonce-456"));
  }

  @Test
  public void validateIdToken_fetchesJwksFromUrlWhenInlineAbsent() throws Exception {
    String jwks = jwksDocument();
    stub("/jwks", jwks);

    String raw = signToken("bob", "n1", jwks);
    OidcConfigurationData configuration = configuration();
    configuration.setIdpJwks(null);
    configuration.setIdpJwksUrl(baseUrl + "/jwks");

    Map<String, Object> claims = service.validateIdToken(configuration, raw, "n1");
    assertThat(claims.get("sub"), is("bob"));
  }

  @Test
  public void validateIdToken_rejectsNonceMismatch() throws Exception {
    String jwks = jwksDocument();
    String raw = signToken("alice", "expected-nonce", jwks);
    OidcConfigurationData configuration = configuration();
    configuration.setIdpJwks(jwks);

    IOException failure = assertThrows(IOException.class,
        () -> service.validateIdToken(configuration, raw, "different-nonce"));
    assertThat(failure.getMessage(), containsString("nonce"));
  }

  @Test
  public void validateIdToken_rejectsWrongAudience() throws Exception {
    String jwks = jwksDocument();
    // sign with a different audience than the configured client id
    String raw = JWT.create()
        .withKeyId(KEY_ID)
        .withIssuer(baseUrl)
        .withAudience("someone-else")
        .withSubject("alice")
        .withClaim("nonce", "n1")
        .withExpiresAt(Date.from(Instant.now().plus(Duration.ofMinutes(5))))
        .sign(Algorithm.RSA256(publicKey, privateKey));

    OidcConfigurationData configuration = configuration();
    configuration.setIdpJwks(jwks);

    assertThrows(Exception.class, () -> service.validateIdToken(configuration, raw, "n1"));
  }

  @Test
  public void validateIdToken_enforcesExactMatchClaims() throws Exception {
    String jwks = jwksDocument();
    String raw = signToken("alice", "n1", jwks);
    OidcConfigurationData configuration = configuration();
    configuration.setIdpJwks(jwks);
    configuration.setExactMatchClaims(Map.of("department", "security"));

    IOException failure = assertThrows(IOException.class,
        () -> service.validateIdToken(configuration, raw, "n1"));
    assertThat(failure.getMessage(), containsString("department"));

    // when the claim matches, validation passes
    configuration.setExactMatchClaims(Map.of("department", "it"));
    Map<String, Object> claims = service.validateIdToken(configuration, raw, "n1");
    assertThat(claims.get("department"), is("it"));
  }

  @Test
  public void validateIdToken_rejectsTokenSignedByAnotherKey() throws Exception {
    // the IdP serves our public key, but the token is signed by a different key pair
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair other = generator.generateKeyPair();

    String jwks = jwksDocument();
    String raw = JWT.create()
        .withKeyId(KEY_ID)
        .withIssuer(baseUrl)
        .withAudience("test-client")
        .withSubject("mallory")
        .withClaim("nonce", "n1")
        .withExpiresAt(Date.from(Instant.now().plus(Duration.ofMinutes(5))))
        .sign(Algorithm.RSA256((RSAPublicKey) other.getPublic(), (RSAPrivateKey) other.getPrivate()));

    OidcConfigurationData configuration = configuration();
    configuration.setIdpJwks(jwks);

    assertThrows(Exception.class, () -> service.validateIdToken(configuration, raw, "n1"));
  }

  @Test
  public void extractGroups_handlesArrayAndDelimitedString() {
    OidcService svc = new OidcService(HttpClient.newHttpClient());

    List<String> fromArray = svc.extractGroups(Map.of("groups", List.of("a", "b")), "groups");
    assertThat(fromArray, hasItem("a"));
    assertThat(fromArray, hasItem("b"));

    List<String> fromString = svc.extractGroups(Map.of("groups", "a,b c"), "groups");
    assertThat(fromString.size(), is(3));

    assertThat(svc.extractGroups(Map.of(), "groups").isEmpty(), is(true));
    assertThat(svc.extractGroups(Map.of("groups", List.of("a")), null).isEmpty(), is(true));
  }

  private OidcConfigurationData configuration() {
    OidcConfigurationData configuration = new OidcConfigurationData();
    configuration.setEnabled(true);
    configuration.setClientId("test-client");
    configuration.setClientSecret("test-secret");
    configuration.setIdpAuthorizationUrl(baseUrl + "/authorize");
    configuration.setIdpTokenUrl(baseUrl + "/token");
    configuration.setIdpJwksUrl(baseUrl + "/jwks");
    configuration.setIdpJwsAlgorithm("RS256");
    configuration.setUsernameClaim("sub");
    return configuration;
  }

  private String signToken(final String subject, final String nonce, final String jwks) {
    return JWT.create()
        .withKeyId(KEY_ID)
        .withIssuer(baseUrl)
        .withAudience("test-client")
        .withSubject(subject)
        .withClaim("nonce", nonce)
        .withClaim("department", "it")
        .withExpiresAt(Date.from(Instant.now().plus(Duration.ofMinutes(5))))
        .sign(Algorithm.RSA256(publicKey, privateKey));
  }

  private String jwksDocument() {
    String n = Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey.getModulus().toByteArray());
    String e = Base64.getUrlEncoder().withoutPadding().encodeToString(
        publicKey.getPublicExponent().toByteArray());
    return "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"" + KEY_ID + "\",\"alg\":\"RS256\",\"use\":\"sig\","
        + "\"n\":\"" + n + "\",\"e\":\"" + e + "\"}]}";
  }

  private void stub(final String path, final String body) {
    idp.createContext(path, exchange -> {
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    });
  }
}
