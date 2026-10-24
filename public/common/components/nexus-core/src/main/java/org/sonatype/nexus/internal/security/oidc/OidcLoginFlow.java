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

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.sonatype.nexus.security.authc.oidc.OidcAuthenticationToken;
import org.sonatype.nexus.security.oidc.OidcConfiguration;
import org.sonatype.nexus.security.oidc.OidcConfigurationStore;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Browser flow for OAuth2/OIDC single sign-on: starting the authorization-code flow, consuming its
 * callback, provisioning the user through the OAuth2 realm and landing the browser back in the
 * application.
 *
 * <p>The flow is transport agnostic on purpose; the servlet that serves the {@code /oidc/login} and
 * {@code /oidc/callback} application paths maps {@link Outcome}s onto HTTP responses.</p>
 *
 * <p>Both endpoints are reachable anonymously; the callback carries no credentials of its own and is
 * protected by the one-time {@code state} value plus the signed, nonce-bound {@code id_token}.</p>
 *
 * @since 3.89
 */
@Component
@Singleton
public class OidcLoginFlow
{
  public static final String LOGIN_PATH = "/oidc/login";

  public static final String CALLBACK_PATH = "/oidc/callback";

  private static final Logger log = LoggerFactory.getLogger(OidcLoginFlow.class);

  private static final Duration STATE_TTL = Duration.ofMinutes(5);

  private final OidcConfigurationStore store;

  private final OidcService oidcService;

  private final SecureRandom random = new SecureRandom();

  /**
   * Pending authorizations keyed by the one-time {@code state} value. Entries expire on their own so a
   * lost or replayed state cannot be used after the TTL.
   */
  private final Cache<String, PendingAuthorization> pending =
      CacheBuilder.newBuilder().expireAfterWrite(STATE_TTL).build();

  @Inject
  public OidcLoginFlow(final OidcConfigurationStore store, final OidcService oidcService) {
    this.store = checkNotNull(store);
    this.oidcService = checkNotNull(oidcService);
  }

  /**
   * Starts the flow: records a one-time {@code state}/{@code nonce} pair and returns the IdP
   * authorization URL the browser must be redirected to.
   *
   * @param hash optional base64 encoded return destination the application supplied with the login
   *             request, carried through the flow and resolved once login succeeds
   */
  public Outcome begin(final HttpServletRequest request, @Nullable final String hash) {
    OidcConfiguration configuration = store.load();
    if (configuration == null || !configuration.isEnabled()) {
      return Outcome.error(503, "OAuth2/OIDC single sign-on is not enabled");
    }

    try {
      String state = randomValue();
      String nonce = randomValue();
      pending.put(state, new PendingAuthorization(nonce, hash));

      String authorizationUrl =
          oidcService.buildAuthorizationUrl(configuration, callbackUri(request), state, nonce);
      log.debug("Starting OIDC login, redirecting to the IdP");
      return Outcome.redirect(authorizationUrl);
    }
    catch (Exception e) {
      log.warn("Unable to start OIDC login", e);
      return Outcome.error(500, "Unable to start OIDC login: " + e.getMessage());
    }
  }

  /**
   * Consumes the IdP redirect: exchanges the code, validates the {@code id_token}, provisions the user
   * and returns the destination the browser should be sent to.
   */
  public Outcome complete(
      final HttpServletRequest request,
      @Nullable final String code,
      @Nullable final String state,
      @Nullable final String error) {
    if (error != null && !error.isBlank()) {
      return Outcome.error(502, "The identity provider returned an error: " + error);
    }
    if (code == null || code.isBlank() || state == null || state.isBlank()) {
      return Outcome.error(400, "Missing authorization code or state");
    }

    PendingAuthorization authorization = pending.getIfPresent(state);
    if (authorization == null) {
      return Outcome.error(400, "Unknown or expired authorization state");
    }
    // Consume the state so it cannot be replayed.
    pending.invalidate(state);

    OidcConfiguration configuration = store.load();
    if (configuration == null || !configuration.isEnabled()) {
      return Outcome.error(503, "OAuth2/OIDC single sign-on is not enabled");
    }

    try {
      String redirectUri = callbackUri(request);
      String rawIdToken = oidcService.exchangeCodeForIdToken(configuration, code, redirectUri);
      Map<String, Object> claims = oidcService.validateIdToken(configuration, rawIdToken, authorization.nonce);

      String username = username(configuration, claims);
      List<String> groups = oidcService.extractGroups(claims, configuration.getGroupsClaim());

      Subject subject = SecurityUtils.getSubject();
      subject.login(new OidcAuthenticationToken(username, claims, groups));

      log.info("OIDC login succeeded for user '{}'", username);
      return Outcome.redirect(destination(request, authorization.hash));
    }
    catch (AuthenticationException e) {
      log.warn("OIDC login rejected", e);
      return Outcome.error(401, "OIDC login failed: " + e.getMessage());
    }
    catch (Exception e) {
      log.warn("OIDC login failed", e);
      return Outcome.error(502, "OIDC login failed: " + e.getMessage());
    }
  }

  /**
   * Builds the absolute callback URI the IdP must redirect back to, derived from the current request.
   */
  private static String callbackUri(final HttpServletRequest request) {
    String scheme = request.getScheme();
    String host = request.getServerName();
    int port = request.getServerPort();
    String context = request.getContextPath() == null ? "" : request.getContextPath();

    boolean defaultPort = ("http".equalsIgnoreCase(scheme) && port == 80)
        || ("https".equalsIgnoreCase(scheme) && port == 443);
    String portPart = defaultPort || port <= 0 ? "" : ":" + port;

    return scheme + "://" + host + portPart + context + CALLBACK_PATH;
  }

  /**
   * Where the browser lands after a successful login: the requested application fragment when one was
   * carried through the flow, the application root otherwise.
   */
  private static String destination(final HttpServletRequest request, @Nullable final String hash) {
    String context = request.getContextPath() == null ? "" : request.getContextPath();
    String fragment = fragmentFrom(hash);
    return fragment != null ? context + "/" + fragment : context + "/";
  }

  /**
   * Resolves the return destination the application supplied at login time. The login page encodes it
   * as base64 of a hash URL ({@code btoa('#' + url)}), while a raw hash value is accepted as is.
   */
  @Nullable
  private static String fragmentFrom(@Nullable final String hash) {
    if (hash == null || hash.isBlank()) {
      return null;
    }
    String value = hash.trim();
    if (value.startsWith("#")) {
      return value;
    }
    for (Base64.Decoder decoder : new Base64.Decoder[]{Base64.getDecoder(), Base64.getUrlDecoder()}) {
      try {
        String decoded = new String(decoder.decode(value), StandardCharsets.UTF_8).trim();
        if (decoded.startsWith("#")) {
          return decoded;
        }
      }
      catch (IllegalArgumentException ignored) {
        // not base64; try the next decoder
      }
    }
    return null;
  }

  /**
   * Resolves the username from the configured claim, falling back to the standard {@code sub} claim.
   */
  private static String username(final OidcConfiguration configuration, final Map<String, Object> claims) {
    String claimName = configuration.getUsernameClaim();
    if (claimName != null && !claimName.isBlank()) {
      Object value = claims.get(claimName);
      if (value instanceof String && !((String) value).isBlank()) {
        return (String) value;
      }
    }
    Object sub = claims.get("sub");
    if (sub instanceof String && !((String) sub).isBlank()) {
      return (String) sub;
    }
    throw new AuthenticationException("The id_token does not contain a usable username claim");
  }

  private String randomValue() {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /**
   * Transport neutral result of a flow step: either a redirect to follow or an error to render.
   */
  public static final class Outcome
  {
    private final int status;

    private final String location;

    private final String message;

    private Outcome(final int status, @Nullable final String location, @Nullable final String message) {
      this.status = status;
      this.location = location;
      this.message = message;
    }

    public static Outcome redirect(final String location) {
      return new Outcome(303, location, null);
    }

    public static Outcome error(final int status, final String message) {
      return new Outcome(status, null, message);
    }

    public boolean isRedirect() {
      return location != null;
    }

    public int getStatus() {
      return status;
    }

    @Nullable
    public String getLocation() {
      return location;
    }

    @Nullable
    public String getMessage() {
      return message;
    }
  }

  /**
   * A one-time authorization awaiting its callback, carrying the nonce bound to the flow and the
   * optional return destination requested at login time.
   */
  private static final class PendingAuthorization
  {
    private final String nonce;

    @Nullable
    private final String hash;

    private PendingAuthorization(final String nonce, @Nullable final String hash) {
      this.nonce = nonce;
      this.hash = hash;
    }
  }
}
