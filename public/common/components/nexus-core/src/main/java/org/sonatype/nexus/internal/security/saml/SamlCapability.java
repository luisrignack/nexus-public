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

import jakarta.inject.Inject;

import org.sonatype.nexus.capability.CapabilitySupport;
import org.sonatype.nexus.capability.CapabilityType;
import org.sonatype.nexus.security.authc.saml.SamlRealm;
import org.sonatype.nexus.security.realm.RealmManager;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Capability holding the SAML configuration and controlling the {@link SamlRealm}.
 */
@Component(SamlCapabilityDescriptor.TYPE_ID)
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class SamlCapability
    extends CapabilitySupport<Map<String, String>>
{
  public static final CapabilityType TYPE = SamlCapabilityDescriptor.TYPE;

  private final RealmManager realmManager;

  @Inject
  public SamlCapability(final RealmManager realmManager) {
    this.realmManager = checkNotNull(realmManager);
  }

  @Override
  protected Map<String, String> createConfig(final Map<String, String> properties) {
    return properties != null ? new HashMap<>(properties) : new HashMap<>();
  }

  @Override
  protected void onActivate(final Map<String, String> config) {
    realmManager.enableRealm(SamlRealm.REALM_NAME);
  }

  @Override
  protected void onPassivate(final Map<String, String> config) {
    realmManager.disableRealm(SamlRealm.REALM_NAME);
  }
}
