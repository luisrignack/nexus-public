/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 */
package org.sonatype.nexus.internal.security.saml;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.sonatype.nexus.capability.CapabilityReference;
import org.sonatype.nexus.capability.CapabilityReferenceFilterBuilder;
import org.sonatype.nexus.capability.CapabilityRegistry;
import org.sonatype.nexus.security.saml.SamlConfiguration;
import org.sonatype.nexus.security.saml.SamlConfigurationStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * {@link SamlConfigurationStore} backed by the hidden {@code saml} capability.
 */
@Component
@Singleton
public class SamlConfigurationStoreImpl
    implements SamlConfigurationStore
{
  private static final Logger log = LoggerFactory.getLogger(SamlConfigurationStoreImpl.class);

  private final CapabilityRegistry capabilityRegistry;

  @Inject
  public SamlConfigurationStoreImpl(final CapabilityRegistry capabilityRegistry) {
    this.capabilityRegistry = checkNotNull(capabilityRegistry);
  }

  @Override
  public SamlConfiguration newEntity() {
    SamlConfigurationData data = new SamlConfigurationData();
    data.setEnabled(false);
    data.setUsernameAttribute("nameid");
    data.setGroupsAttribute("groups");
    return data;
  }

  @Nullable
  @Override
  public SamlConfiguration load() {
    return findCapability().map(this::toConfiguration).orElse(null);
  }

  @Override
  public void save(final SamlConfiguration configuration) {
    checkNotNull(configuration);

    Map<String, String> properties = new HashMap<>();
    putIfNotNull(properties, SamlCapabilityProperties.ENTITY_ID, configuration.getEntityId());
    putIfNotNull(properties, SamlCapabilityProperties.IDP_METADATA, configuration.getIdpMetadata());
    putIfNotNull(properties, SamlCapabilityProperties.USERNAME_ATTRIBUTE, configuration.getUsernameAttribute());
    putIfNotNull(properties, SamlCapabilityProperties.FIRST_NAME_ATTRIBUTE, configuration.getFirstNameAttribute());
    putIfNotNull(properties, SamlCapabilityProperties.LAST_NAME_ATTRIBUTE, configuration.getLastNameAttribute());
    putIfNotNull(properties, SamlCapabilityProperties.EMAIL_ATTRIBUTE, configuration.getEmailAttribute());
    putIfNotNull(properties, SamlCapabilityProperties.GROUPS_ATTRIBUTE, configuration.getGroupsAttribute());
    if (configuration.getValidateResponseSignature() != null) {
      properties.put(SamlCapabilityProperties.VALIDATE_RESPONSE_SIGNATURE,
          configuration.getValidateResponseSignature().toString());
    }
    if (configuration.getValidateAssertionSignature() != null) {
      properties.put(SamlCapabilityProperties.VALIDATE_ASSERTION_SIGNATURE,
          configuration.getValidateAssertionSignature().toString());
    }

    Optional<CapabilityReference> existing = findCapability();
    if (existing.isPresent()) {
      capabilityRegistry.update(existing.get().context().id(), configuration.isEnabled(),
          existing.get().context().notes(), properties);
    }
    else {
      capabilityRegistry.add(SamlCapabilityDescriptor.TYPE, configuration.isEnabled(), null, properties);
    }
    log.info("SAML configuration saved (enabled={})", configuration.isEnabled());
  }

  @Override
  public void delete() {
    findCapability().ifPresent(reference -> capabilityRegistry.remove(reference.context().id()));
  }

  private SamlConfiguration toConfiguration(final CapabilityReference reference) {
    Map<String, String> properties = reference.context().properties();
    SamlConfigurationData data = new SamlConfigurationData();
    data.setEnabled(reference.context().isActive());
    data.setEntityId(properties.get(SamlCapabilityProperties.ENTITY_ID));
    data.setIdpMetadata(properties.get(SamlCapabilityProperties.IDP_METADATA));
    data.setUsernameAttribute(orDefault(properties.get(SamlCapabilityProperties.USERNAME_ATTRIBUTE), "nameid"));
    data.setGroupsAttribute(orDefault(properties.get(SamlCapabilityProperties.GROUPS_ATTRIBUTE), "groups"));
    data.setFirstNameAttribute(properties.get(SamlCapabilityProperties.FIRST_NAME_ATTRIBUTE));
    data.setLastNameAttribute(properties.get(SamlCapabilityProperties.LAST_NAME_ATTRIBUTE));
    data.setEmailAttribute(properties.get(SamlCapabilityProperties.EMAIL_ATTRIBUTE));
    data.setValidateResponseSignature(parseBoolean(properties.get(SamlCapabilityProperties.VALIDATE_RESPONSE_SIGNATURE)));
    data.setValidateAssertionSignature(parseBoolean(properties.get(SamlCapabilityProperties.VALIDATE_ASSERTION_SIGNATURE)));
    return data;
  }

  private Optional<CapabilityReference> findCapability() {
    return capabilityRegistry.get(CapabilityReferenceFilterBuilder.capabilities()
            .withType(SamlCapabilityDescriptor.TYPE)
            .includeNotExposed())
        .stream()
        .map(CapabilityReference.class::cast)
        .findFirst();
  }

  private static void putIfNotNull(final Map<String, String> properties, final String key, final String value) {
    if (value != null) {
      properties.put(key, value);
    }
  }

  private static String orDefault(final String value, final String fallback) {
    return value == null || value.isEmpty() ? fallback : value;
  }

  private static Boolean parseBoolean(final String value) {
    return value == null ? null : Boolean.valueOf(value);
  }
}
