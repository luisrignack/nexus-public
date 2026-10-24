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
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Property keys and (de)serialization helpers shared by the OIDC capability and its store.
 *
 * <p>Scalar fields are stored as plain strings. The three map fields are stored as JSON objects so the
 * capability properties stay a flat {@code Map<String, String>} while round-tripping structured values.</p>
 *
 * @since 3.89
 */
public final class OidcCapabilityProperties
{
  public static final String ENABLED = "enabled";

  public static final String CLIENT_ID = "clientId";

  public static final String CLIENT_SECRET = "clientSecret";

  public static final String IDP_AUTHORIZATION_URL = "idpAuthorizationUrl";

  public static final String IDP_LOGOUT_URL = "idpLogoutUrl";

  public static final String IDP_TOKEN_URL = "idpTokenUrl";

  public static final String IDP_JWKS_URL = "idpJwksUrl";

  public static final String IDP_JWKS = "idpJwks";

  public static final String IDP_JWS_ALGORITHM = "idpJwsAlgorithm";

  public static final String USERNAME_CLAIM = "usernameClaim";

  public static final String FIRST_NAME_CLAIM = "firstNameClaim";

  public static final String LAST_NAME_CLAIM = "lastNameClaim";

  public static final String EMAIL_CLAIM = "emailClaim";

  public static final String GROUPS_CLAIM = "groupsClaim";

  public static final String EXACT_MATCH_CLAIMS = "exactMatchClaims";

  public static final String AUTHORIZATION_CUSTOM_PARAMS = "authorizationCustomParams";

  public static final String TOKEN_REQUEST_CUSTOM_PARAMS = "tokenRequestCustomParams";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() { };

  private OidcCapabilityProperties() {
    // constants only
  }

  /**
   * Reads a map field from its JSON representation, tolerating blank or malformed values.
   */
  public static Map<String, String> readMap(final Map<String, String> properties, final String key) {
    String json = properties.get(key);
    if (json == null || json.isBlank()) {
      return Collections.emptyMap();
    }
    try {
      Map<String, String> value = MAPPER.readValue(json, MAP_TYPE);
      return value != null ? value : Collections.emptyMap();
    }
    catch (IOException e) {
      // A malformed stored value must not prevent the rest of the configuration from loading.
      return Collections.emptyMap();
    }
  }

  /**
   * Writes a map field as JSON into the properties.
   */
  public static void writeMap(
      final Map<String, String> properties,
      final String key,
      final Map<String, String> value) {
    try {
      properties.put(key, MAPPER.writeValueAsString(value != null ? value : Collections.emptyMap()));
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
