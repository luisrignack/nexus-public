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
package org.sonatype.nexus.internal.security.ldap.rest;

import javax.annotation.Nullable;

/**
 * Transfer object mirroring the JSON shape the LDAP admin UI forms read and write.
 *
 * <p>Values in this XO use the UI conventions: {@code protocol} is one of {@code LDAP} or
 * {@code LDAPS}, {@code authScheme} one of {@code NONE}/{@code SIMPLE}/{@code DIGEST_MD5}/{@code
 * CRAM_MD5} and {@code groupType} one of {@code STATIC}/{@code DYNAMIC}.</p>
 *
 * @since 3.89
 */
public class LdapServerXO
{
  private String id;

  private Integer order;

  private String name;

  private String protocol;

  private Boolean useTrustStore;

  private String host;

  private Integer port;

  private String searchBase;

  private String authScheme;

  private String authRealm;

  private String authUsername;

  private String authPassword;

  private Integer connectionTimeoutSeconds;

  private Integer connectionRetryDelaySeconds;

  private Integer maxIncidentsCount;

  private String userBaseDn;

  private Boolean userSubtree;

  private String userObjectClass;

  private String userLdapFilter;

  private String userIdAttribute;

  private String userRealNameAttribute;

  private String userEmailAddressAttribute;

  private String userPasswordAttribute;

  private Boolean ldapGroupsAsRoles;

  private String groupType;

  private String groupBaseDn;

  private Boolean groupSubtree;

  private String groupObjectClass;

  private String groupIdAttribute;

  private String groupMemberAttribute;

  private String groupMemberFormat;

  private String userMemberOfAttribute;

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public Integer getOrder() {
    return order;
  }

  public void setOrder(final Integer order) {
    this.order = order;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public String getProtocol() {
    return protocol;
  }

  public void setProtocol(final String protocol) {
    this.protocol = protocol;
  }

  public Boolean getUseTrustStore() {
    return useTrustStore;
  }

  public void setUseTrustStore(final Boolean useTrustStore) {
    this.useTrustStore = useTrustStore;
  }

  public String getHost() {
    return host;
  }

  public void setHost(final String host) {
    this.host = host;
  }

  public Integer getPort() {
    return port;
  }

  public void setPort(final Integer port) {
    this.port = port;
  }

  public String getSearchBase() {
    return searchBase;
  }

  public void setSearchBase(final String searchBase) {
    this.searchBase = searchBase;
  }

  public String getAuthScheme() {
    return authScheme;
  }

  public void setAuthScheme(final String authScheme) {
    this.authScheme = authScheme;
  }

  @Nullable
  public String getAuthRealm() {
    return authRealm;
  }

  public void setAuthRealm(final String authRealm) {
    this.authRealm = authRealm;
  }

  @Nullable
  public String getAuthUsername() {
    return authUsername;
  }

  public void setAuthUsername(final String authUsername) {
    this.authUsername = authUsername;
  }

  @Nullable
  public String getAuthPassword() {
    return authPassword;
  }

  public void setAuthPassword(final String authPassword) {
    this.authPassword = authPassword;
  }

  public Integer getConnectionTimeoutSeconds() {
    return connectionTimeoutSeconds;
  }

  public void setConnectionTimeoutSeconds(final Integer connectionTimeoutSeconds) {
    this.connectionTimeoutSeconds = connectionTimeoutSeconds;
  }

  public Integer getConnectionRetryDelaySeconds() {
    return connectionRetryDelaySeconds;
  }

  public void setConnectionRetryDelaySeconds(final Integer connectionRetryDelaySeconds) {
    this.connectionRetryDelaySeconds = connectionRetryDelaySeconds;
  }

  public Integer getMaxIncidentsCount() {
    return maxIncidentsCount;
  }

  public void setMaxIncidentsCount(final Integer maxIncidentsCount) {
    this.maxIncidentsCount = maxIncidentsCount;
  }

  public String getUserBaseDn() {
    return userBaseDn;
  }

  public void setUserBaseDn(final String userBaseDn) {
    this.userBaseDn = userBaseDn;
  }

  public Boolean getUserSubtree() {
    return userSubtree;
  }

  public void setUserSubtree(final Boolean userSubtree) {
    this.userSubtree = userSubtree;
  }

  public String getUserObjectClass() {
    return userObjectClass;
  }

  public void setUserObjectClass(final String userObjectClass) {
    this.userObjectClass = userObjectClass;
  }

  @Nullable
  public String getUserLdapFilter() {
    return userLdapFilter;
  }

  public void setUserLdapFilter(final String userLdapFilter) {
    this.userLdapFilter = userLdapFilter;
  }

  public String getUserIdAttribute() {
    return userIdAttribute;
  }

  public void setUserIdAttribute(final String userIdAttribute) {
    this.userIdAttribute = userIdAttribute;
  }

  public String getUserRealNameAttribute() {
    return userRealNameAttribute;
  }

  public void setUserRealNameAttribute(final String userRealNameAttribute) {
    this.userRealNameAttribute = userRealNameAttribute;
  }

  public String getUserEmailAddressAttribute() {
    return userEmailAddressAttribute;
  }

  public void setUserEmailAddressAttribute(final String userEmailAddressAttribute) {
    this.userEmailAddressAttribute = userEmailAddressAttribute;
  }

  @Nullable
  public String getUserPasswordAttribute() {
    return userPasswordAttribute;
  }

  public void setUserPasswordAttribute(final String userPasswordAttribute) {
    this.userPasswordAttribute = userPasswordAttribute;
  }

  public Boolean getLdapGroupsAsRoles() {
    return ldapGroupsAsRoles;
  }

  public void setLdapGroupsAsRoles(final Boolean ldapGroupsAsRoles) {
    this.ldapGroupsAsRoles = ldapGroupsAsRoles;
  }

  public String getGroupType() {
    return groupType;
  }

  public void setGroupType(final String groupType) {
    this.groupType = groupType;
  }

  @Nullable
  public String getGroupBaseDn() {
    return groupBaseDn;
  }

  public void setGroupBaseDn(final String groupBaseDn) {
    this.groupBaseDn = groupBaseDn;
  }

  public Boolean getGroupSubtree() {
    return groupSubtree;
  }

  public void setGroupSubtree(final Boolean groupSubtree) {
    this.groupSubtree = groupSubtree;
  }

  @Nullable
  public String getGroupObjectClass() {
    return groupObjectClass;
  }

  public void setGroupObjectClass(final String groupObjectClass) {
    this.groupObjectClass = groupObjectClass;
  }

  @Nullable
  public String getGroupIdAttribute() {
    return groupIdAttribute;
  }

  public void setGroupIdAttribute(final String groupIdAttribute) {
    this.groupIdAttribute = groupIdAttribute;
  }

  @Nullable
  public String getGroupMemberAttribute() {
    return groupMemberAttribute;
  }

  public void setGroupMemberAttribute(final String groupMemberAttribute) {
    this.groupMemberAttribute = groupMemberAttribute;
  }

  @Nullable
  public String getGroupMemberFormat() {
    return groupMemberFormat;
  }

  public void setGroupMemberFormat(final String groupMemberFormat) {
    this.groupMemberFormat = groupMemberFormat;
  }

  @Nullable
  public String getUserMemberOfAttribute() {
    return userMemberOfAttribute;
  }

  public void setUserMemberOfAttribute(final String userMemberOfAttribute) {
    this.userMemberOfAttribute = userMemberOfAttribute;
  }
}
