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
package org.sonatype.nexus.security.jwt;

import java.time.OffsetDateTime;

/**
 * Service for managing JWT session revocations.
 */
public interface JwtSessionRevocationService
{
  /**
   * Revoke a JWT session by recording its userSessionId.
   *
   * @param userSessionId the unique session identifier from JWT claim
   * @param username the username associated with the session
   * @param userSource the user source/realm (e.g., "default", "LDAP", "SAML")
   * @param expiresAt when the JWT expires
   */
  void revokeSession(String userSessionId, String username, String userSource, OffsetDateTime expiresAt);

  /**
   * Check if a JWT session is revoked.
   *
   * @param userSessionId the unique session identifier from JWT claim
   * @return true if the session is revoked, false otherwise
   */
  boolean isRevoked(String userSessionId);

  /**
   * Delete expired session revocations that are past their JWT expiration time.
   * This should be called periodically to prevent table bloat.
   *
   * @return the number of expired revocations deleted
   */
  int deleteExpiredSessions();
}
