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
package org.sonatype.nexus.capability;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.sonatype.nexus.formfields.FormField;
import org.sonatype.nexus.formfields.NumberTextFormField;
import org.sonatype.nexus.formfields.Selectable;

import static com.google.common.base.Preconditions.checkNotNull;

public class CapabilityDTO
{
  public static final String PASSWORD_PLACEHOLDER = "#~NXRM~PLACEHOLDER~PASSWORD~#";

  private String id;

  private String type;

  private String notes;

  private boolean enabled;

  private Map<String, String> properties;

  public CapabilityDTO() {
    // deserialization and tests
  }

  public CapabilityDTO(final CapabilityReference reference) {
    checkNotNull(reference);
    CapabilityContext context = checkNotNull(reference.context());

    id = context.id().toString();
    type = context.type().toString();
    enabled = context.isEnabled();
    notes = context.notes();
    properties = filterProperties(context.properties(), reference.capability());
  }

  public String getId() {
    return id;
  }

  public String getNotes() {
    return notes;
  }

  public Map<String, String> getProperties() {
    return properties;
  }

  public String getType() {
    return type;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public void setNotes(final String notes) {
    this.notes = notes;
  }

  public void setProperties(final Map<String, String> properties) {
    this.properties = properties;
  }

  public void setType(final String type) {
    this.type = type;
  }

  public static CapabilityTypeDTO fromCapabilityDescriptor(final CapabilityDescriptor capabilityDescriptor) {
    CapabilityTypeDTO dto = new CapabilityTypeDTO();
    dto.setId(capabilityDescriptor.type().toString());
    dto.setName(capabilityDescriptor.name());
    dto.setAbout(capabilityDescriptor.about());
    if (capabilityDescriptor.formFields() != null) {
      dto.setFormFields(
          capabilityDescriptor.formFields()
              .stream()
              .map(CapabilityDTO::toFormFieldDTO)
              .toList());
    }
    return dto;
  }

  private static FormFieldDTO toFormFieldDTO(final FormField<?> source) {
    FormFieldDTO dto = new FormFieldDTO();
    dto.setId(source.getId());
    dto.setType(source.getType());
    dto.setLabel(source.getLabel());
    dto.setHelpText(source.getHelpText());
    dto.setRequired(source.isRequired());
    dto.setDisabled(source.isDisabled());
    dto.setReadOnly(source.isReadOnly());
    dto.setRegexValidation(source.getRegexValidation());
    dto.setInitialValue(Optional.ofNullable(source.getInitialValue()).map(Objects::toString).orElse(null));
    dto.setAttributes(source.getAttributes());

    if (source instanceof NumberTextFormField ntf) {
      dto.setMinimumValue(Optional.ofNullable(ntf.getMinimumValue()).map(Object::toString).orElse(null));
      dto.setMaximumValue(Optional.ofNullable(ntf.getMaximumValue()).map(Object::toString).orElse(null));
    }

    if (source instanceof Selectable selectable) {
      dto.setStoreApi(selectable.getStoreApi());
      dto.setStoreFilters(selectable.getStoreFilters());
      dto.setAllowAutocomplete(source.getAllowAutocomplete());
      dto.setIdMapping(selectable.getIdMapping());
      dto.setNameMapping(selectable.getNameMapping());
    }

    return dto;
  }

  private static Map<String, String> filterProperties(
      final Map<String, String> properties,
      final Capability capability)
  {
    return properties.entrySet()
        .stream()
        .collect(Collectors.toMap(Entry::getKey, entry -> {
          if (capability.isPasswordProperty(entry.getKey())) {
            if ("PKI".equals(properties.get("authenticationType"))) {
              return "";
            }
            else {
              return PASSWORD_PLACEHOLDER;
            }
          }
          return entry.getValue() != null ? entry.getValue() : ""; // ensure no null values
        }));
  }
}
