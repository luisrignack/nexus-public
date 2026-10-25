/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. All other trademarks
 * of their respective owners.
 */
package org.sonatype.nexus.internal.security.ldap.rest;

import org.sonatype.nexus.internal.security.ldap.LdapConfigurationData;
import org.sonatype.nexus.internal.security.ldap.LdapConfigurationStoreImpl;
import org.sonatype.nexus.security.ldap.LdapConfiguration;
import org.sonatype.nexus.security.ldap.LdapDirectory.LdapUserInfo;

/**
 * Conversions between the JSON representation ({@link LdapServerXO}) and the store model
 * ({@link LdapConfiguration}).
 *
 * <p>Shared by the REST resource and the Ext.Direct component that backs the admin UI, so both
 * speak exactly the same field mapping (including the {@code null} defaults).</p>
 *
 * @since 3.89
 */
public final class LdapServerXoMapper
{
  private LdapServerXoMapper() {
    // no instances
  }

  public static LdapConfiguration toConfiguration(final LdapServerXO xo) {
    LdapConfigurationData data = new LdapConfigurationData();
    data.setId(xo.getId());
    // null on create/verify: the store assigns the order itself
    data.setOrder(xo.getOrder() != null ? xo.getOrder() : 0);
    data.setName(xo.getName());
    data.setProtocol(xo.getProtocol() != null ? xo.getProtocol() : "LDAP");
    data.setUseTrustStore(xo.getUseTrustStore() != null && xo.getUseTrustStore());
    data.setHost(xo.getHost());
    data.setPort(xo.getPort() != null ? xo.getPort() : 389);
    data.setSearchBase(xo.getSearchBase());
    data.setAuthScheme(xo.getAuthScheme() != null ? xo.getAuthScheme() : "NONE");
    data.setAuthRealm(xo.getAuthRealm());
    data.setAuthUsername(xo.getAuthUsername());
    data.setAuthPassword(xo.getAuthPassword());
    data.setConnectionTimeoutSeconds(xo.getConnectionTimeoutSeconds() != null ? xo.getConnectionTimeoutSeconds() : 30);
    data.setConnectionRetryDelaySeconds(
        xo.getConnectionRetryDelaySeconds() != null ? xo.getConnectionRetryDelaySeconds() : 30);
    data.setMaxIncidentsCount(xo.getMaxIncidentsCount() != null ? xo.getMaxIncidentsCount() : 30);
    data.setUserBaseDn(xo.getUserBaseDn());
    data.setUserSubtree(xo.getUserSubtree() == null || xo.getUserSubtree());
    data.setUserObjectClass(xo.getUserObjectClass());
    data.setUserLdapFilter(xo.getUserLdapFilter());
    data.setUserIdAttribute(xo.getUserIdAttribute());
    data.setUserRealNameAttribute(xo.getUserRealNameAttribute());
    data.setUserEmailAddressAttribute(xo.getUserEmailAddressAttribute());
    data.setUserPasswordAttribute(xo.getUserPasswordAttribute());
    data.setLdapGroupsAsRoles(xo.getLdapGroupsAsRoles() != null && xo.getLdapGroupsAsRoles());
    data.setGroupType(xo.getGroupType() != null ? xo.getGroupType() : "DYNAMIC");
    data.setGroupBaseDn(xo.getGroupBaseDn());
    data.setGroupSubtree(xo.getGroupSubtree() == null || xo.getGroupSubtree());
    data.setGroupObjectClass(xo.getGroupObjectClass());
    data.setGroupIdAttribute(xo.getGroupIdAttribute());
    data.setGroupMemberAttribute(xo.getGroupMemberAttribute());
    data.setGroupMemberFormat(xo.getGroupMemberFormat());
    data.setUserMemberOfAttribute(xo.getUserMemberOfAttribute());
    return data;
  }

  public static LdapServerXO toXO(final LdapConfiguration configuration) {
    LdapServerXO xo = new LdapServerXO();
    xo.setId(configuration.getId());
    xo.setOrder(configuration.getOrder());
    xo.setName(configuration.getName());
    xo.setProtocol(configuration.getProtocol());
    xo.setUseTrustStore(configuration.isUseTrustStore());
    xo.setHost(configuration.getHost());
    xo.setPort(configuration.getPort());
    xo.setSearchBase(configuration.getSearchBase());
    xo.setAuthScheme(configuration.getAuthScheme());
    xo.setAuthRealm(configuration.getAuthRealm());
    xo.setAuthUsername(configuration.getAuthUsername());
    xo.setAuthPassword(LdapConfigurationStoreImpl.PASSWORD_PLACEHOLDER);
    xo.setConnectionTimeoutSeconds(configuration.getConnectionTimeoutSeconds());
    xo.setConnectionRetryDelaySeconds(configuration.getConnectionRetryDelaySeconds());
    xo.setMaxIncidentsCount(configuration.getMaxIncidentsCount());
    xo.setUserBaseDn(configuration.getUserBaseDn());
    xo.setUserSubtree(configuration.isUserSubtree());
    xo.setUserObjectClass(configuration.getUserObjectClass());
    xo.setUserLdapFilter(configuration.getUserLdapFilter());
    xo.setUserIdAttribute(configuration.getUserIdAttribute());
    xo.setUserRealNameAttribute(configuration.getUserRealNameAttribute());
    xo.setUserEmailAddressAttribute(configuration.getUserEmailAddressAttribute());
    xo.setUserPasswordAttribute(configuration.getUserPasswordAttribute());
    xo.setLdapGroupsAsRoles(configuration.isLdapGroupsAsRoles());
    xo.setGroupType(configuration.getGroupType());
    xo.setGroupBaseDn(configuration.getGroupBaseDn());
    xo.setGroupSubtree(configuration.isGroupSubtree());
    xo.setGroupObjectClass(configuration.getGroupObjectClass());
    xo.setGroupIdAttribute(configuration.getGroupIdAttribute());
    xo.setGroupMemberAttribute(configuration.getGroupMemberAttribute());
    xo.setGroupMemberFormat(configuration.getGroupMemberFormat());
    xo.setUserMemberOfAttribute(configuration.getUserMemberOfAttribute());
    return xo;
  }

  public static LdapUserXO toUserXO(final LdapUserInfo userInfo) {
    return new LdapUserXO(
        userInfo.getUsername(),
        userInfo.getRealName(),
        userInfo.getEmailAddress(),
        userInfo.getGroups());
  }
}
