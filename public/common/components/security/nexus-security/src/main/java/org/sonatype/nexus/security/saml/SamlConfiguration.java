/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 */
package org.sonatype.nexus.security.saml;

/**
 * Configuration of the SAML 2.0 service provider.
 *
 * <p>The XML metadata of the identity provider is kept as-is; the signing credentials and the
 * single sign-on endpoints are derived from it on demand. The entity id and the assertion consumer
 * service URL are exposed as SP metadata so the IdP can be configured from a single place.</p>
 */
public interface SamlConfiguration
{
  boolean isEnabled();

  void setEnabled(boolean enabled);

  String getEntityId();

  void setEntityId(String entityId);

  String getIdpMetadata();

  void setIdpMetadata(String idpMetadata);

  String getUsernameAttribute();

  void setUsernameAttribute(String usernameAttribute);

  String getFirstNameAttribute();

  void setFirstNameAttribute(String firstNameAttribute);

  String getLastNameAttribute();

  void setLastNameAttribute(String lastNameAttribute);

  String getEmailAttribute();

  void setEmailAttribute(String emailAttribute);

  String getGroupsAttribute();

  void setGroupsAttribute(String groupsAttribute);

  Boolean getValidateResponseSignature();

  void setValidateResponseSignature(Boolean validateResponseSignature);

  Boolean getValidateAssertionSignature();

  void setValidateAssertionSignature(Boolean validateAssertionSignature);
}
