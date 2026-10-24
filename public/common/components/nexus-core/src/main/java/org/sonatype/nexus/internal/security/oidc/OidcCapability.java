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
package org.sonatype.nexus.internal.security.oidc;

import java.util.HashMap;
import java.util.Map;

import jakarta.inject.Inject;

import org.sonatype.nexus.capability.CapabilitySupport;
import org.sonatype.nexus.capability.CapabilityType;
import org.sonatype.nexus.security.authc.oidc.OidcRealm;
import org.sonatype.nexus.security.realm.RealmManager;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Capability holding the OAuth2/OIDC single sign-on configuration.
 *
 * <p>Keeping it as a capability makes the feature state visible to the UI, lets the configuration
 * participate in the normal backup/restore and audit flows, and gives a single activation point that
 * installs the {@link OidcRealm} on demand.</p>
 *
 * @since 3.89
 */
@Component(OidcCapabilityDescriptor.TYPE_ID)
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class OidcCapability
    extends CapabilitySupport<Map<String, String>>
{
  public static final CapabilityType TYPE = OidcCapabilityDescriptor.TYPE;

  private final RealmManager realmManager;

  @Inject
  public OidcCapability(final RealmManager realmManager) {
    this.realmManager = checkNotNull(realmManager);
  }

  @Override
  protected Map<String, String> createConfig(final Map<String, String> properties) {
    // The raw properties are the configuration; no derived model is needed.
    return properties != null ? new HashMap<>(properties) : new HashMap<>();
  }

  @Override
  protected void onActivate(final Map<String, String> config) {
    // Install the realm on demand; the realm itself refuses logins while the capability is inactive.
    realmManager.enableRealm(OidcRealm.REALM_NAME);
  }

  @Override
  protected void onPassivate(final Map<String, String> config) {
    realmManager.disableRealm(OidcRealm.REALM_NAME);
  }
}
