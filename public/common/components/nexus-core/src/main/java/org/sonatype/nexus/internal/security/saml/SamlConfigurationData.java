/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 */
package org.sonatype.nexus.internal.security.saml;

import org.sonatype.nexus.security.saml.SamlConfiguration;

/**
 * Mutable {@link SamlConfiguration}.
 */
public class SamlConfigurationData
    implements SamlConfiguration
{
  private boolean enabled;

  private String entityId;

  private String idpMetadata;

  private String usernameAttribute = "nameid";

  private String firstNameAttribute;

  private String lastNameAttribute;

  private String emailAttribute;

  private String groupsAttribute = "groups";

  private Boolean validateResponseSignature;

  private Boolean validateAssertionSignature;

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  @Override
  public String getEntityId() {
    return entityId;
  }

  @Override
  public void setEntityId(final String entityId) {
    this.entityId = entityId;
  }

  @Override
  public String getIdpMetadata() {
    return idpMetadata;
  }

  @Override
  public void setIdpMetadata(final String idpMetadata) {
    this.idpMetadata = idpMetadata;
  }

  @Override
  public String getUsernameAttribute() {
    return usernameAttribute;
  }

  @Override
  public void setUsernameAttribute(final String usernameAttribute) {
    this.usernameAttribute = usernameAttribute;
  }

  @Override
  public String getFirstNameAttribute() {
    return firstNameAttribute;
  }

  @Override
  public void setFirstNameAttribute(final String firstNameAttribute) {
    this.firstNameAttribute = firstNameAttribute;
  }

  @Override
  public String getLastNameAttribute() {
    return lastNameAttribute;
  }

  @Override
  public void setLastNameAttribute(final String lastNameAttribute) {
    this.lastNameAttribute = lastNameAttribute;
  }

  @Override
  public String getEmailAttribute() {
    return emailAttribute;
  }

  @Override
  public void setEmailAttribute(final String emailAttribute) {
    this.emailAttribute = emailAttribute;
  }

  @Override
  public String getGroupsAttribute() {
    return groupsAttribute;
  }

  @Override
  public void setGroupsAttribute(final String groupsAttribute) {
    this.groupsAttribute = groupsAttribute;
  }

  @Override
  public Boolean getValidateResponseSignature() {
    return validateResponseSignature;
  }

  @Override
  public void setValidateResponseSignature(final Boolean validateResponseSignature) {
    this.validateResponseSignature = validateResponseSignature;
  }

  @Override
  public Boolean getValidateAssertionSignature() {
    return validateAssertionSignature;
  }

  @Override
  public void setValidateAssertionSignature(final Boolean validateAssertionSignature) {
    this.validateAssertionSignature = validateAssertionSignature;
  }
}
