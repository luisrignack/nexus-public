/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 */
package org.sonatype.nexus.internal.security.saml;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.inject.Singleton;

import org.sonatype.nexus.capability.CapabilityDescriptorSupport;
import org.sonatype.nexus.capability.CapabilityType;
import org.sonatype.nexus.capability.Tag;
import org.sonatype.nexus.capability.Taggable;
import org.sonatype.nexus.common.i18n.I18N;
import org.sonatype.nexus.common.i18n.MessageBundle;
import org.sonatype.nexus.common.upgrade.AvailabilityVersion;
import org.sonatype.nexus.formfields.FormField;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import static org.sonatype.nexus.capability.Tag.tags;

/**
 * Descriptor for the hidden {@code saml} capability managed by the configuration REST endpoint.
 */
@AvailabilityVersion(from = "1.0")
@Component
@Qualifier(SamlCapabilityDescriptor.TYPE_ID)
@Singleton
public class SamlCapabilityDescriptor
    extends CapabilityDescriptorSupport<Map<String, String>>
    implements Taggable
{
  public static final String TYPE_ID = "saml";

  public static final CapabilityType TYPE = CapabilityType.capabilityType(TYPE_ID);

  private interface Messages
      extends MessageBundle
  {
    @DefaultMessage("SAML Single Sign-On")
    String name();

    @DefaultMessage("Security")
    String category();
  }

  private static final Messages messages = I18N.create(Messages.class);

  public SamlCapabilityDescriptor() {
    setExposed(false);
    setHidden(true);
  }

  @Override
  public CapabilityType type() {
    return TYPE;
  }

  @Override
  public String name() {
    return messages.name();
  }

  @Override
  public List<FormField> formFields() {
    return Collections.emptyList();
  }

  @Override
  public Set<Tag> getTags() {
    return tags(Tag.categoryTag(messages.category()));
  }

  @Override
  protected Map<String, String> createConfig(final Map<String, String> properties) {
    return properties != null ? new java.util.HashMap<>(properties) : new java.util.HashMap<>();
  }
}
