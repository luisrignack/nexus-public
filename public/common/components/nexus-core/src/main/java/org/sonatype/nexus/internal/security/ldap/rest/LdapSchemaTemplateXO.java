/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 */
package org.sonatype.nexus.internal.security.ldap.rest;

import java.util.ArrayList;
import java.util.List;

/**
 * A pre-filled LDAP form offered by the admin UI.
 */
public class LdapSchemaTemplateXO
{
  private String name;

  private LdapServerXO configuration;

  public LdapSchemaTemplateXO() {
    // deserialization
  }

  public LdapSchemaTemplateXO(final String name, final LdapServerXO configuration) {
    this.name = name;
    this.configuration = configuration;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public LdapServerXO getConfiguration() {
    return configuration;
  }

  public void setConfiguration(final LdapServerXO configuration) {
    this.configuration = configuration;
  }

  public static List<LdapSchemaTemplateXO> templates() {
    List<LdapSchemaTemplateXO> templates = new ArrayList<>();
    templates.add(new LdapSchemaTemplateXO("Active Directory", configuration("cn=users", false, "user", "sAMAccountName",
        "cn", "mail", true, "DYNAMIC", null, null, null, null, null, "memberOf")));
    templates.add(new LdapSchemaTemplateXO("Posix with Static Groups",
        configuration("ou=people", false, "posixAccount", "uid", "cn", "mail", true, "STATIC", "ou=groups",
            "posixGroup", "cn", "memberUid", "${username}", null)));
    templates.add(new LdapSchemaTemplateXO("Posix with Dynamic Groups",
        configuration("ou=people", false, "posixAccount", "uid", "cn", "mail", true, "DYNAMIC", null,
            null, null, null, null, "memberOf")));
    templates.add(new LdapSchemaTemplateXO("Generic Ldap Server", configuration(null, false, "inetOrgPerson", "uid",
        "cn", "mail", true, "DYNAMIC", null, null, null, null, null, "memberOf")));
    return templates;
  }

  private static LdapServerXO configuration(
      final String userBaseDn, final boolean userSubtree, final String userObjectClass,
      final String userIdAttribute, final String userRealNameAttribute, final String userEmailAttribute,
      final boolean ldapGroupsAsRoles, final String groupType, final String groupBaseDn,
      final String groupObjectClass, final String groupIdAttribute, final String groupMemberAttribute,
      final String groupMemberFormat, final String userMemberOfAttribute)
  {
    LdapServerXO xo = new LdapServerXO();
    xo.setProtocol("LDAP");
    xo.setPort(389);
    xo.setAuthScheme("NONE");
    xo.setUserBaseDn(userBaseDn);
    xo.setUserSubtree(userSubtree);
    xo.setUserObjectClass(userObjectClass);
    xo.setUserIdAttribute(userIdAttribute);
    xo.setUserRealNameAttribute(userRealNameAttribute);
    xo.setUserEmailAddressAttribute(userEmailAttribute);
    xo.setLdapGroupsAsRoles(ldapGroupsAsRoles);
    xo.setGroupType(groupType);
    xo.setGroupBaseDn(groupBaseDn);
    xo.setGroupObjectClass(groupObjectClass);
    xo.setGroupIdAttribute(groupIdAttribute);
    xo.setGroupMemberAttribute(groupMemberAttribute);
    xo.setGroupMemberFormat(groupMemberFormat);
    xo.setUserMemberOfAttribute(userMemberOfAttribute);
    return xo;
  }
}
