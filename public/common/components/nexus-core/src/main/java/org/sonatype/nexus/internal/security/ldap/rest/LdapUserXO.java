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

import java.util.Set;

public class LdapUserXO
{
  private String userId;

  private String firstName;

  private String lastName;

  private String email;

  private Set<String> roles;

  public LdapUserXO() {
    // deserialization
  }

  public LdapUserXO(final String userId, final String name, final String email, final Set<String> roles) {
    this.userId = userId;
    this.email = email;
    this.roles = roles;
    if (name != null && name.contains(" ")) {
      String[] parts = name.split("\\s+", 2);
      this.firstName = parts[0];
      this.lastName = parts[1];
    }
    else {
      this.firstName = name;
    }
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(final String userId) {
    this.userId = userId;
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
}
