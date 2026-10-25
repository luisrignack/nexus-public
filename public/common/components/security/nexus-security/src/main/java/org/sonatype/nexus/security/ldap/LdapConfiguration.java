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

import javax.annotation.Nullable;

/**
 * Configuration of a single LDAP connection, mirroring the fields the admin UI form submits.
 *
 * @since 3.89
 */
public interface LdapConfiguration
{
  /**
   * Stable identifier of this server configuration (the backing capability id).
   */
  String getId();

  void setId(String id);

  /**
   * Relative position of the server when authenticating users across all configured servers.
   */
  int getOrder();

  void setOrder(int order);

  /** Display name of the server. */
  String getName();

  /** One of {@code LDAP} or {@code LDAPS}. */
  String getProtocol();

  /** Whether connections should trust the Nexus trust store for {@code LDAPS}. */
  boolean isUseTrustStore();

  String getHost();

  int getPort();

  /** Search base DN appended to the connection URL, e.g. {@code dc=example,dc=com}. */
  String getSearchBase();

  /** One of {@code NONE}, {@code SIMPLE}, {@code DIGEST_MD5} or {@code CRAM_MD5}. */
  String getAuthScheme();

  /** SASL realm, only used by the SASL authentication schemes. */
  @Nullable
  String getAuthRealm();

  @Nullable
  String getAuthUsername();

  /** Plain bind password; may be {@code null} when the scheme allows none. */
  @Nullable
  String getAuthPassword();

  int getConnectionTimeoutSeconds();

  int getConnectionRetryDelaySeconds();

  int getMaxIncidentsCount();

  // --- user mapping ---

  String getUserBaseDn();

  boolean isUserSubtree();

  String getUserObjectClass();

  @Nullable
  String getUserLdapFilter();

  String getUserIdAttribute();

  String getUserRealNameAttribute();

  String getUserEmailAddressAttribute();

  @Nullable
  String getUserPasswordAttribute();

  /** Whether LDAP group memberships should be mapped to Nexus roles. */
  boolean isLdapGroupsAsRoles();

  /** One of {@code STATIC} or {@code DYNAMIC}. */
  String getGroupType();

  // --- group mapping (only meaningful for STATIC) ---

  @Nullable
  String getGroupBaseDn();

  boolean isGroupSubtree();

  @Nullable
  String getGroupObjectClass();

  @Nullable
  String getGroupIdAttribute();

  @Nullable
  String getGroupMemberAttribute();

  @Nullable
  String getGroupMemberFormat();

  /**
   * The user attribute returning group memberships, only meaningful when {@link #getGroupType()} is
   * {@code DYNAMIC}.
   */
  @Nullable
  String getUserMemberOfAttribute();
}
