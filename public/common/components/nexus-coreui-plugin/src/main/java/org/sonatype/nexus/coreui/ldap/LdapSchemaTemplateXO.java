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
package org.sonatype.nexus.coreui.ldap;

import java.util.List;

import javax.annotation.Nullable;

/**
 * Schema template offered by the LDAP "User and Group" configuration form: a named starting point
 * for the user and group mapping attributes.
 *
 * <p>The field names are the contract with the React admin UI
 * ({@code LdapServersUserAndGroupMachine#setData}); they mirror the corresponding
 * {@code LdapServerXO} fields so a template can be merged straight into the form state.</p>
 *
 * @since 3.89
 */
public class LdapSchemaTemplateXO
{
  private String name;

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

  public LdapSchemaTemplateXO() {
    // deserialization
  }

  public static List<LdapSchemaTemplateXO> templates() {
    return List.of(
        template(
            "Active Directory", "cn=users", false, "user", null, "sAMAccountName", "cn", "mail", null,
            true, "DYNAMIC", null, false, null, null, null, null, "memberOf"),
        template(
            "Posix with Static Groups", "ou=people", false, "posixAccount", null, "uid", "cn", "mail", null,
            true, "STATIC", "ou=groups", false, "posixGroup", "cn", "memberUid", "${username}", null),
        template(
            "Posix with Dynamic Groups", "ou=people", false, "posixAccount", null, "uid", "cn", "mail", null,
            true, "DYNAMIC", null, false, null, null, null, null, "memberOf"),
        template(
            "Generic Ldap Server", null, false, "inetOrgPerson", null, "uid", "cn", "mail", null,
            true, "DYNAMIC", null, false, null, null, null, null, "memberOf"));
  }

  @SuppressWarnings("checkstyle:parameterNumber")
  private static LdapSchemaTemplateXO template(
      final String name,
      final String userBaseDn,
      final boolean userSubtree,
      final String userObjectClass,
      final String userLdapFilter,
      final String userIdAttribute,
      final String userRealNameAttribute,
      final String userEmailAddressAttribute,
      final String userPasswordAttribute,
      final boolean ldapGroupsAsRoles,
      final String groupType,
      final String groupBaseDn,
      final boolean groupSubtree,
      final String groupObjectClass,
      final String groupIdAttribute,
      final String groupMemberAttribute,
      final String groupMemberFormat,
      final String userMemberOfAttribute)
  {
    LdapSchemaTemplateXO template = new LdapSchemaTemplateXO();
    template.name = name;
    template.userBaseDn = userBaseDn;
    template.userSubtree = userSubtree;
    template.userObjectClass = userObjectClass;
    template.userLdapFilter = userLdapFilter;
    template.userIdAttribute = userIdAttribute;
    template.userRealNameAttribute = userRealNameAttribute;
    template.userEmailAddressAttribute = userEmailAddressAttribute;
    template.userPasswordAttribute = userPasswordAttribute;
    template.ldapGroupsAsRoles = ldapGroupsAsRoles;
    template.groupType = groupType;
    template.groupBaseDn = groupBaseDn;
    template.groupSubtree = groupSubtree;
    template.groupObjectClass = groupObjectClass;
    template.groupIdAttribute = groupIdAttribute;
    template.groupMemberAttribute = groupMemberAttribute;
    template.groupMemberFormat = groupMemberFormat;
    template.userMemberOfAttribute = userMemberOfAttribute;
    return template;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  @Nullable
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

  @Nullable
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

  @Nullable
  public String getUserIdAttribute() {
    return userIdAttribute;
  }

  public void setUserIdAttribute(final String userIdAttribute) {
    this.userIdAttribute = userIdAttribute;
  }

  @Nullable
  public String getUserRealNameAttribute() {
    return userRealNameAttribute;
  }

  public void setUserRealNameAttribute(final String userRealNameAttribute) {
    this.userRealNameAttribute = userRealNameAttribute;
  }

  @Nullable
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

  @Nullable
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
