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
package org.sonatype.nexus.security.ldap;

import java.util.Set;

import javax.annotation.Nullable;

/**
 * Operations against an LDAP directory for a given {@link LdapConfiguration}.
 *
 * <p>The interface lives in {@code nexus-security} because both the {@code LdapRealm} (authc) and
 * the admin UI back ends (connection/user-mapping verification) consume it; the JNDI-based
 * implementation is provided by {@code nexus-core}, the same split used for the configuration
 * store.</p>
 *
 * @since 3.89
 */
public interface LdapDirectory
{
  /**
   * Verifies that a connection can be established and the configured bind (if any) succeeds.
   *
   * @throws Exception when the connection or bind fails, with a human-readable message
   */
  void verifyConnection(LdapConfiguration configuration) throws Exception;

  /**
   * Authenticates a user: finds the user entry under the configured user base, binds as the user
   * (or compares the {@code userPasswordAttribute} as a fallback) and collects the mapped group
   * identifiers.
   *
   * @throws Exception when the user cannot be found or bound, with a human-readable message
   */
  LdapUserInfo authenticate(LdapConfiguration configuration, String username, char[] password) throws Exception;

  /**
   * Resolves users for the admin UI "verify user mapping" check without authenticating them.
   */
  Set<LdapUserInfo> verifyUserMapping(LdapConfiguration configuration) throws Exception;

  /**
   * Collected information about an LDAP user.
   */
  interface LdapUserInfo
  {
    String getUsername();

    @Nullable
    String getRealName();

    @Nullable
    String getEmailAddress();

    /** Group identifiers (values of the configured {@code groupIdAttribute}), may be empty. */
    Set<String> getGroups();
  }
}
