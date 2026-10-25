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
package org.sonatype.nexus.internal.security.ldap;

/**
 * Property keys used by the {@code ldap} capability: every LDAP server configuration is one
 * capability instance, so all fields are flat scalar properties.
 *
 * @since 3.89
 */
public final class LdapCapabilityProperties
{
  public static final String SERVER_NAME = "name";

  public static final String PROTOCOL = "protocol";

  public static final String USE_TRUST_STORE = "useTrustStore";

  public static final String HOST = "host";

  public static final String PORT = "port";

  public static final String SEARCH_BASE = "searchBase";

  public static final String AUTH_SCHEME = "authScheme";

  public static final String AUTH_REALM = "authRealm";

  public static final String AUTH_USERNAME = "authUsername";

  /** Opaque {@link org.sonatype.nexus.crypto.secrets.Secret} token of the encrypted bind password. */
  public static final String AUTH_PASSWORD = "authPassword";

  public static final String CONNECTION_TIMEOUT_SECONDS = "connectionTimeoutSeconds";

  public static final String CONNECTION_RETRY_DELAY_SECONDS = "connectionRetryDelaySeconds";

  public static final String MAX_INCIDENTS_COUNT = "maxIncidentsCount";

  public static final String USER_BASE_DN = "userBaseDn";

  public static final String USER_SUBTREE = "userSubtree";

  public static final String USER_OBJECT_CLASS = "userObjectClass";

  public static final String USER_LDAP_FILTER = "userLdapFilter";

  public static final String USER_ID_ATTRIBUTE = "userIdAttribute";

  public static final String USER_REAL_NAME_ATTRIBUTE = "userRealNameAttribute";

  public static final String USER_EMAIL_ADDRESS_ATTRIBUTE = "userEmailAddressAttribute";

  public static final String USER_PASSWORD_ATTRIBUTE = "userPasswordAttribute";

  public static final String LDAP_GROUPS_AS_ROLES = "ldapGroupsAsRoles";

  public static final String GROUP_TYPE = "groupType";

  public static final String GROUP_BASE_DN = "groupBaseDn";

  public static final String GROUP_SUBTREE = "groupSubtree";

  public static final String GROUP_OBJECT_CLASS = "groupObjectClass";

  public static final String GROUP_ID_ATTRIBUTE = "groupIdAttribute";

  public static final String GROUP_MEMBER_ATTRIBUTE = "groupMemberAttribute";

  public static final String GROUP_MEMBER_FORMAT = "groupMemberFormat";

  public static final String USER_MEMBER_OF_ATTRIBUTE = "userMemberOfAttribute";

  public static final String ORDER = "order";

  private LdapCapabilityProperties() {
    // constants only
  }
}
