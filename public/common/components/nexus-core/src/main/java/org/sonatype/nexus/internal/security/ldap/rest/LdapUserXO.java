/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 */
package org.sonatype.nexus.internal.security.ldap.rest;

import java.util.Set;

/**
 * Result of the "verify user mapping" task. Both the legacy field names and the names used by the
 * current admin UI are exposed so either client can consume the response.
 */
public class LdapUserXO
{
  private String userId;

  private String firstName;

  private String lastName;

  private String company;

  private String realName;

  private String email;

  private Set<String> roles;

  public LdapUserXO() {
    // deserialization
  }

  public LdapUserXO(
      final String userId,
      final String realName,
      final String lastName,
      final String company,
      final String email,
      final Set<String> roles)
  {
    this.userId = userId;
    this.realName = realName;
    this.lastName = lastName;
    this.company = company;
    this.email = email;
    this.roles = roles;
    this.firstName = realName;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(final String userId) {
    this.userId = userId;
  }

  /** Alias used by the current admin UI. */
  public String getUsername() {
    return userId;
  }

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(final String firstName) {
    this.firstName = firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public void setLastName(final String lastName) {
    this.lastName = lastName;
  }

  public String getCompany() {
    return company;
  }

  public void setCompany(final String company) {
    this.company = company;
  }

  public String getRealName() {
    return realName;
  }

  public void setRealName(final String realName) {
    this.realName = realName;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(final String email) {
    this.email = email;
  }

  public Set<String> getRoles() {
    return roles;
  }

  public void setRoles(final Set<String> roles) {
    this.roles = roles;
  }

  /** Alias used by the current admin UI for the mapped groups. */
  public Set<String> getMembership() {
    return roles;
  }
}
