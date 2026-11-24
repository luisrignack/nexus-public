/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 */
package org.sonatype.nexus.internal.security.ldap.rest;

/**
 * Payload sent by the admin UI when verifying a login: credentials are Base64 encoded so they do not
 * appear verbatim in browser debugging tools.
 */
public class LdapVerifyLoginXO
{
  private String base64Username;

  private String base64Password;

  private LdapServerXO ldapServer;

  public String getBase64Username() {
    return base64Username;
  }

  public void setBase64Username(final String base64Username) {
    this.base64Username = base64Username;
  }

  public String getBase64Password() {
    return base64Password;
  }

  public void setBase64Password(final String base64Password) {
    this.base64Password = base64Password;
  }

  public LdapServerXO getLdapServer() {
    return ldapServer;
  }

  public void setLdapServer(final LdapServerXO ldapServer) {
    this.ldapServer = ldapServer;
  }
}
