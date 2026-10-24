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
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.sonatype.nexus.security.oidc.OidcConfiguration;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OAuth2/OIDC client covering the authorization-code flow: building the authorization URL, exchanging
 * the code for tokens, and validating the resulting {@code id_token} against the IdP JWKS.
 *
 * <p>This class is deliberately free of Nexus-specific wiring so it can be unit tested against a
 * stubbed {@link HttpClient}. Only the {@code id_token} is consumed; the access token is ignored
 * because Nexus authorizes from the validated claims, not from resource-server calls.</p>
 *
 * @since 3.89
 */
public class OidcService
{
  private static final Logger log = LoggerFactory.getLogger(OidcService.class);

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final Duration TIMEOUT = Duration.ofSeconds(15);

  private final HttpClient httpClient;

  public OidcService(final HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  /**
   * Builds the authorization URL the browser is redirected to in order to start the flow.
   *
   * @param configuration active OIDC configuration
   * @param redirectUri the callback URI the IdP must return to
   * @param state opaque anti-CSRF value to round-trip through the IdP
   * @param nonce single-use value bound to the eventual id_token
   */
  public String buildAuthorizationUrl(
      final OidcConfiguration configuration,
      final String redirectUri,
      final String state,
      final String nonce) {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("response_type", "code");
    params.put("client_id", configuration.getClientId());
    params.put("redirect_uri", redirectUri);
    params.put("scope", "openid profile email");
    params.put("state", state);
    params.put("nonce", nonce);
    // Custom authorization parameters override the defaults when they collide.
    if (configuration.getAuthorizationCustomParams() != null) {
      params.putAll(configuration.getAuthorizationCustomParams());
    }

    return appendQuery(configuration.getIdpAuthorizationUrl(), params);
  }

  /**
   * Exchanges the authorization code for tokens and returns the raw {@code id_token} claim.
   */
  public String exchangeCodeForIdToken(
      final OidcConfiguration configuration,
      final String code,
      final String redirectUri) throws IOException, InterruptedException {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("grant_type", "authorization_code");
    form.put("code", code);
    form.put("redirect_uri", redirectUri);
    form.put("client_id", configuration.getClientId());
    String secret = configuration.getClientSecret();
    if (secret != null && !secret.isBlank()) {
      form.put("client_secret", secret);
    }
    if (configuration.getTokenRequestCustomParams() != null) {
      form.putAll(configuration.getTokenRequestCustomParams());
    }

    String body = form.entrySet().stream()
        .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
        .reduce((a, b) -> a + "&" + b)
        .orElse("");

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(configuration.getIdpTokenUrl()))
        .timeout(TIMEOUT)
        .header("Content-Type", "application/x-www-form-urlencoded")
        .header("Accept", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new IOException("Token endpoint returned HTTP " + response.statusCode());
    }

    JsonNode json = MAPPER.readTree(response.body());
    JsonNode idToken = json.get("id_token");
    if (idToken == null || idToken.isNull() || idToken.asText().isBlank()) {
      throw new IOException("Token response did not contain an id_token");
    }
    return idToken.asText();
  }

  /**
   * Validates a raw {@code id_token}: signature against the IdP JWKS, issuer, audience, expiry, the
   * nonce bound to the flow, and any configured exact-match claims.
   *
   * @return the validated claims keyed by claim name
   */
  public Map<String, Object> validateIdToken(
      final OidcConfiguration configuration,
      final String rawIdToken,
      @Nullable final String expectedNonce) throws IOException, InterruptedException {
    DecodedJWT jwt = JWT.decode(rawIdToken);

    RSAPublicKey publicKey = resolvePublicKey(configuration, jwt.getKeyId());
    String algorithmName = configuration.getIdpJwsAlgorithm() != null
        ? configuration.getIdpJwsAlgorithm()
        : "RS256";
    Algorithm algorithm = algorithmFor(algorithmName, publicKey);

    // The required issuer is taken from an explicit exact-match claim when the administrator supplied
    // one, because the authorization URL does not always follow the "{issuer}/authorize" convention
    // (Keycloak, for example, uses a deeper path). Otherwise it is derived from the authorization URL.
    String requiredIssuer = explicitIssuer(configuration);
    if (requiredIssuer == null) {
      requiredIssuer = issuerFrom(configuration.getIdpAuthorizationUrl());
    }

    var verifier = JWT.require(algorithm).acceptLeeway(60);
    if (requiredIssuer != null && !requiredIssuer.isBlank()) {
      verifier = verifier.withIssuer(requiredIssuer);
    }
    String clientId = configuration.getClientId();
    if (clientId != null && !clientId.isBlank()) {
      verifier = verifier.withAudience(clientId);
    }

    DecodedJWT verified = verifier.build().verify(jwt);

    // The nonce must match the value generated when the flow started.
    String nonce = stringClaim(verified, "nonce");
    if (expectedNonce != null && !expectedNonce.equals(nonce)) {
      throw new IOException("id_token nonce mismatch");
    }

    Map<String, Object> claims = new LinkedHashMap<>();
    verified.getClaims().forEach((name, claim) -> claims.put(name, claimValue(claim)));

    // Exact-match claims are an explicit allow-list enforced by the administrator.
    if (configuration.getExactMatchClaims() != null) {
      for (Map.Entry<String, String> entry : configuration.getExactMatchClaims().entrySet()) {
        Object actual = claims.get(entry.getKey());
        if (!entry.getValue().equals(actual == null ? null : String.valueOf(actual))) {
          throw new IOException("id_token claim '" + entry.getKey() + "' did not match the configured value");
        }
      }
    }

    return claims;
  }

  /**
   * Extracts group memberships from a validated claim map, tolerating both a JSON array of strings and
   * a single delimited string value.
   */
  @SuppressWarnings("unchecked")
  public List<String> extractGroups(final Map<String, Object> claims, @Nullable final String groupsClaim) {
    List<String> groups = new ArrayList<>();
    if (groupsClaim == null || groupsClaim.isBlank()) {
      return groups;
    }
    Object value = claims.get(groupsClaim);
    if (value instanceof List) {
      ((List<Object>) value).forEach(item -> {
        if (item != null) {
          groups.add(String.valueOf(item));
        }
      });
    }
    else if (value instanceof String && !((String) value).isBlank()) {
      for (String part : ((String) value).split("[,\\s]+")) {
        if (!part.isBlank()) {
          groups.add(part);
        }
      }
    }
    return groups;
  }

  private RSAPublicKey resolvePublicKey(final OidcConfiguration configuration, @Nullable final String keyId)
      throws IOException, InterruptedException {
    String jwksDocument = configuration.getIdpJwks();
    if (jwksDocument == null || jwksDocument.isBlank()) {
      jwksDocument = fetch(configuration.getIdpJwksUrl());
    }
    return selectKey(jwksDocument, keyId);
  }

  private String fetch(final String url) throws IOException, InterruptedException {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(TIMEOUT)
        .header("Accept", "application/json")
        .GET()
        .build();
    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new IOException("JWKS endpoint returned HTTP " + response.statusCode());
    }
    return response.body();
  }

  /**
   * Picks the RSA signing key from a JWKS document by {@code kid}, falling back to the first RSA key
   * when the document carries keys but the token carries no {@code kid}.
   */
  private RSAPublicKey selectKey(final String jwksDocument, @Nullable final String keyId) throws IOException {
    JsonNode keys = MAPPER.readTree(jwksDocument).path("keys");
    if (!keys.isArray() || keys.isEmpty()) {
      throw new IOException("JWKS document contains no keys");
    }

    JsonNode selected = null;
    for (JsonNode key : keys) {
      if (!"RSA".equals(key.path("kty").asText())) {
        continue;
      }
      if (keyId != null && keyId.equals(key.path("kid").asText())) {
        selected = key;
        break;
      }
      if (selected == null) {
        selected = key;
      }
    }
    if (selected == null) {
      throw new IOException("JWKS document contains no RSA key");
    }

    try {
      BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(selected.path("n").asText()));
      BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(selected.path("e").asText()));
      return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, exponent));
    }
    catch (Exception e) {
      throw new IOException("Unable to build the RSA public key from the JWKS document", e);
    }
  }

  private static Algorithm algorithmFor(final String name, final RSAPublicKey publicKey) throws IOException {
    switch (name.toUpperCase()) {
      case "RS256":
        return Algorithm.RSA256(publicKey, null);
      case "RS384":
        return Algorithm.RSA384(publicKey, null);
      case "RS512":
        return Algorithm.RSA512(publicKey, null);
      default:
        throw new IOException("Unsupported JWS algorithm: " + name);
    }
  }

  /**
   * Returns an explicitly configured issuer from the exact-match claims, or {@code null} when the
   * administrator did not pin one.
   */
  @Nullable
  private static String explicitIssuer(final OidcConfiguration configuration) {
    if (configuration.getExactMatchClaims() == null) {
      return null;
    }
    String issuer = configuration.getExactMatchClaims().get("iss");
    return issuer != null && !issuer.isBlank() ? issuer : null;
  }

  /**
   * Derives the issuer URL from the authorization endpoint, which lives under the issuer path in the
   * OIDC discovery convention ({@code {issuer}/authorize}).
   */
  private static String issuerFrom(@Nullable final String authorizationUrl) {
    if (authorizationUrl == null) {
      return null;
    }
    int idx = authorizationUrl.lastIndexOf('/');
    return idx > 0 ? authorizationUrl.substring(0, idx) : authorizationUrl;
  }

  @Nullable
  private static String stringClaim(final DecodedJWT jwt, final String name) {
    Claim claim = jwt.getClaim(name);
    return claim != null ? claim.asString() : null;
  }

  private static Object claimValue(final Claim claim) {
    if (claim == null || claim.isNull()) {
      return null;
    }
    // as(Object.class) yields the natural value (String, Number, Boolean, List or Map). The typed
    // accessors below only return null for a mismatched shape, so they act as fallbacks, not probes.
    try {
      Object value = claim.as(Object.class);
      if (value != null) {
        return value;
      }
    }
    catch (Exception ignored) {
      // fall through to the typed accessors
    }
    try {
      Map<String, Object> map = claim.asMap();
      if (map != null) {
        return map;
      }
    }
    catch (Exception ignored) {
      // not an object claim
    }
    try {
      List<Object> list = claim.asList(Object.class);
      if (list != null) {
        return list;
      }
    }
    catch (Exception ignored) {
      // not an array claim
    }
    return claim.asString();
  }

  private static String appendQuery(final String baseUrl, final Map<String, String> params) {
    StringBuilder sb = new StringBuilder(baseUrl);
    char separator = baseUrl.contains("?") ? '&' : '?';
    for (Map.Entry<String, String> entry : params.entrySet()) {
      sb.append(separator).append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
      separator = '&';
    }
    return sb.toString();
  }

  private static String encode(final String value) {
    return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
  }
}
