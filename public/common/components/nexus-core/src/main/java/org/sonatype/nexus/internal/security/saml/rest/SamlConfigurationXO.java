/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 */
package org.sonatype.nexus.internal.security.saml.rest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.sonatype.nexus.internal.security.saml.SamlConfigurationData;
import org.sonatype.nexus.security.saml.SamlConfiguration;

/**
 * JSON representation of the SAML configuration used by the admin UI.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SamlConfigurationXO
{
  private boolean enabled;

  private String entityId;

  private String idpMetadata;

  private String usernameAttribute;

  private String firstNameAttribute;

  private String lastNameAttribute;

  private String emailAttribute;

  private String groupsAttribute;

  private Boolean validateResponseSignature;

  private Boolean validateAssertionSignature;

  public static SamlConfigurationXO from(final SamlConfiguration configuration) {
    SamlConfigurationXO xo = new SamlConfigurationXO();
    xo.enabled = configuration.isEnabled();
    xo.entityId = configuration.getEntityId();
    xo.idpMetadata = configuration.getIdpMetadata();
    xo.usernameAttribute = configuration.getUsernameAttribute();
    xo.firstNameAttribute = configuration.getFirstNameAttribute();
    xo.lastNameAttribute = configuration.getLastNameAttribute();
    xo.emailAttribute = configuration.getEmailAttribute();
    xo.groupsAttribute = configuration.getGroupsAttribute();
    xo.validateResponseSignature = configuration.getValidateResponseSignature();
    xo.validateAssertionSignature = configuration.getValidateAssertionSignature();
    return xo;
  }

  public SamlConfiguration toConfiguration() {
    SamlConfigurationData data = new SamlConfigurationData();
    data.setEnabled(enabled);
    data.setEntityId(entityId);
    data.setIdpMetadata(idpMetadata);
    data.setUsernameAttribute(usernameAttribute);
    data.setFirstNameAttribute(firstNameAttribute);
    data.setLastNameAttribute(lastNameAttribute);
    data.setEmailAttribute(emailAttribute);
    data.setGroupsAttribute(groupsAttribute);
    data.setValidateResponseSignature(validateResponseSignature);
    data.setValidateAssertionSignature(validateAssertionSignature);
    return data;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public String getEntityId() {
    return entityId;
  }

  public void setEntityId(final String entityId) {
    this.entityId = entityId;
  }

  public String getIdpMetadata() {
    return idpMetadata;
  }

  public void setIdpMetadata(final String idpMetadata) {
    this.idpMetadata = idpMetadata;
  }

  public String getUsernameAttribute() {
    return usernameAttribute;
  }

  public void setUsernameAttribute(final String usernameAttribute) {
    this.usernameAttribute = usernameAttribute;
  }

  public String getFirstNameAttribute() {
    return firstNameAttribute;
  }

  public void setFirstNameAttribute(final String firstNameAttribute) {
    this.firstNameAttribute = firstNameAttribute;
  }

  public String getLastNameAttribute() {
    return lastNameAttribute;
  }

  public void setLastNameAttribute(final String lastNameAttribute) {
    this.lastNameAttribute = lastNameAttribute;
  }

  public String getEmailAttribute() {
    return emailAttribute;
  }

  public void setEmailAttribute(final String emailAttribute) {
    this.emailAttribute = emailAttribute;
  }

  public String getGroupsAttribute() {
    return groupsAttribute;
  }

  public void setGroupsAttribute(final String groupsAttribute) {
    this.groupsAttribute = groupsAttribute;
  }

  public Boolean getValidateResponseSignature() {
    return validateResponseSignature;
  }

  public void setValidateResponseSignature(final Boolean validateResponseSignature) {
    this.validateResponseSignature = validateResponseSignature;
  }

  public Boolean getValidateAssertionSignature() {
    return validateAssertionSignature;
  }

  public void setValidateAssertionSignature(final Boolean validateAssertionSignature) {
    this.validateAssertionSignature = validateAssertionSignature;
  }
}
