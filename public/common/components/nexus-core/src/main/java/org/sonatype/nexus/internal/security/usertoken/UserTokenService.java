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
import java.util.Optional;

import org.apache.shiro.subject.PrincipalCollection;

/**
 * Manages user tokens and their configuration.
 *
 * @since 3.89
 */
public interface UserTokenService
{
  /**
   * Domain used to store user tokens in the shared API key tables.
   */
  String DOMAIN = "usertoken";

  /**
   * Current feature configuration.
   */
  UserTokenConfiguration getConfiguration();

  /**
   * Persists the feature configuration.
   */
  UserTokenConfiguration setConfiguration(UserTokenConfiguration configuration);

  /**
   * Whether the feature is currently enabled.
   */
  boolean isEnabled();

  /**
   * Whether repository clients must authenticate with a user token instead of a password.
   */
  boolean isProtectContent();

  /**
   * Returns the token assigned to the given principals, if any.
   */
  Optional<UserToken> getToken(PrincipalCollection principals);

  /**
   * Returns the existing token for the given principals, creating a new one when missing or expired.
   */
  UserToken getOrCreateToken(PrincipalCollection principals);

  /**
   * Replaces the token assigned to the given principals with a fresh one.
   */
  UserToken createToken(PrincipalCollection principals);

  /**
   * Deletes the token assigned to the given principals.
   */
  boolean deleteToken(PrincipalCollection principals);

  /**
   * Deletes every token assigned to the given user, regardless of realm.
   */
  int deleteTokensForUser(String userId);

  /**
   * Deletes every user token in the system.
   */
  int deleteAllTokens();

  /**
   * Resolves the principals behind a token, when the feature is enabled and the token has not expired.
   */
  Optional<PrincipalCollection> authenticate(String token);

  /**
   * Value object describing the two halves of a user token.
   */
  record UserToken(String nameCode, String passCode, OffsetDateTime created, OffsetDateTime expiresAt)
  {
    public boolean isExpired() {
      return expiresAt != null && expiresAt.isBefore(OffsetDateTime.now());
    }

    public String combined() {
      return nameCode + passCode;
    }
  }
}
