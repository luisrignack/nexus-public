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

import javax.annotation.Nullable;

import org.sonatype.nexus.security.ldap.LdapConfiguration;

/**
 * Mutable {@link LdapConfiguration} backing the capability properties.
 *
 * @since 3.89
 */
public class LdapConfigurationData
    implements LdapConfiguration
{
  private String id;

  private int order;

  private String name;

  private String protocol;

  private boolean useTrustStore;

  private String host;

  private int port;

  private String searchBase;

  private String authScheme;

  private String authRealm;

  private String authUsername;

  private String authPassword;

  private int connectionTimeoutSeconds;

  private int connectionRetryDelaySeconds;

  private int maxIncidentsCount;

  private String userBaseDn;

  private boolean userSubtree;

  private String userObjectClass;

  private String userLdapFilter;

  private String userIdAttribute;

  private String userRealNameAttribute;

  private String userEmailAddressAttribute;

  private String userPasswordAttribute;

  private boolean ldapGroupsAsRoles;

  private String groupType;

  private String groupBaseDn;

  private boolean groupSubtree;

  private String groupObjectClass;

  private String groupIdAttribute;

  private String groupMemberAttribute;

  private String groupMemberFormat;

  private String userMemberOfAttribute;

  @Override
  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  @Override
  public int getOrder() {
    return order;
  }

  public void setOrder(final int order) {
    this.order = order;
  }

  @Override
  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  @Override
  public String getProtocol() {
    return protocol;
  }

  public void setProtocol(final String protocol) {
    this.protocol = protocol;
  }

  @Override
  public boolean isUseTrustStore() {
    return useTrustStore;
  }

  public void setUseTrustStore(final boolean useTrustStore) {
    this.useTrustStore = useTrustStore;
  }

  @Override
  public String getHost() {
    return host;
  }

  public void setHost(final String host) {
    this.host = host;
  }

  @Override
  public int getPort() {
    return port;
  }

  public void setPort(final int port) {
    this.port = port;
  }

  @Override
  public String getSearchBase() {
    return searchBase;
  }

  public void setSearchBase(final String searchBase) {
    this.searchBase = searchBase;
  }

  @Override
  public String getAuthScheme() {
    return authScheme;
  }

  public void setAuthScheme(final String authScheme) {
    this.authScheme = authScheme;
  }

  @Nullable
  @Override
  public String getAuthRealm() {
    return authRealm;
  }

  public void setAuthRealm(final String authRealm) {
    this.authRealm = authRealm;
  }

  @Nullable
  @Override
  public String getAuthUsername() {
    return authUsername;
  }

  public void setAuthUsername(final String authUsername) {
    this.authUsername = authUsername;
  }

  @Nullable
  @Override
  public String getAuthPassword() {
    return authPassword;
  }

  public void setAuthPassword(final String authPassword) {
    this.authPassword = authPassword;
  }

  @Override
  public int getConnectionTimeoutSeconds() {
    return connectionTimeoutSeconds;
  }

  public void setConnectionTimeoutSeconds(final int connectionTimeoutSeconds) {
    this.connectionTimeoutSeconds = connectionTimeoutSeconds;
  }

  @Override
  public int getConnectionRetryDelaySeconds() {
    return connectionRetryDelaySeconds;
  }

  public void setConnectionRetryDelaySeconds(final int connectionRetryDelaySeconds) {
    this.connectionRetryDelaySeconds = connectionRetryDelaySeconds;
  }

  @Override
  public int getMaxIncidentsCount() {
    return maxIncidentsCount;
  }

  public void setMaxIncidentsCount(final int maxIncidentsCount) {
    this.maxIncidentsCount = maxIncidentsCount;
  }

  @Override
  public String getUserBaseDn() {
    return userBaseDn;
  }

  public void setUserBaseDn(final String userBaseDn) {
    this.userBaseDn = userBaseDn;
  }

  @Override
  public boolean isUserSubtree() {
    return userSubtree;
  }

  public void setUserSubtree(final boolean userSubtree) {
    this.userSubtree = userSubtree;
  }

  @Override
  public String getUserObjectClass() {
    return userObjectClass;
  }

  public void setUserObjectClass(final String userObjectClass) {
    this.userObjectClass = userObjectClass;
  }

  @Nullable
  @Override
  public String getUserLdapFilter() {
    return userLdapFilter;
  }

  public void setUserLdapFilter(final String userLdapFilter) {
    this.userLdapFilter = userLdapFilter;
  }

  @Override
  public String getUserIdAttribute() {
    return userIdAttribute;
  }

  public void setUserIdAttribute(final String userIdAttribute) {
    this.userIdAttribute = userIdAttribute;
  }

  @Override
  public String getUserRealNameAttribute() {
    return userRealNameAttribute;
  }

  public void setUserRealNameAttribute(final String userRealNameAttribute) {
    this.userRealNameAttribute = userRealNameAttribute;
  }

  @Override
  public String getUserEmailAddressAttribute() {
    return userEmailAddressAttribute;
  }

  public void setUserEmailAddressAttribute(final String userEmailAddressAttribute) {
    this.userEmailAddressAttribute = userEmailAddressAttribute;
  }

  @Nullable
  @Override
  public String getUserPasswordAttribute() {
    return userPasswordAttribute;
  }

  public void setUserPasswordAttribute(final String userPasswordAttribute) {
    this.userPasswordAttribute = userPasswordAttribute;
  }

  @Override
  public boolean isLdapGroupsAsRoles() {
    return ldapGroupsAsRoles;
  }

  public void setLdapGroupsAsRoles(final boolean ldapGroupsAsRoles) {
    this.ldapGroupsAsRoles = ldapGroupsAsRoles;
  }

  @Override
  public String getGroupType() {
    return groupType;
  }

  public void setGroupType(final String groupType) {
    this.groupType = groupType;
  }

  @Nullable
  @Override
  public String getGroupBaseDn() {
    return groupBaseDn;
  }

  public void setGroupBaseDn(final String groupBaseDn) {
    this.groupBaseDn = groupBaseDn;
  }

  @Override
  public boolean isGroupSubtree() {
    return groupSubtree;
  }

  public void setGroupSubtree(final boolean groupSubtree) {
    this.groupSubtree = groupSubtree;
  }

  @Nullable
  @Override
  public String getGroupObjectClass() {
    return groupObjectClass;
  }

  public void setGroupObjectClass(final String groupObjectClass) {
    this.groupObjectClass = groupObjectClass;
  }

  @Nullable
  @Override
  public String getGroupIdAttribute() {
    return groupIdAttribute;
  }

  public void setGroupIdAttribute(final String groupIdAttribute) {
    this.groupIdAttribute = groupIdAttribute;
  }

  @Nullable
  @Override
  public String getGroupMemberAttribute() {
    return groupMemberAttribute;
  }

  public void setGroupMemberAttribute(final String groupMemberAttribute) {
    this.groupMemberAttribute = groupMemberAttribute;
  }

  @Nullable
  @Override
  public String getGroupMemberFormat() {
    return groupMemberFormat;
  }

  public void setGroupMemberFormat(final String groupMemberFormat) {
    this.groupMemberFormat = groupMemberFormat;
  }

  @Nullable
  @Override
  public String getUserMemberOfAttribute() {
    return userMemberOfAttribute;
  }

  public void setUserMemberOfAttribute(final String userMemberOfAttribute) {
    this.userMemberOfAttribute = userMemberOfAttribute;
  }
}
