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
package org.sonatype.nexus.coreui.internal.oidc;

import java.util.Map;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.rapture.StateContributor;
import org.sonatype.nexus.security.oidc.OidcConfiguration;
import org.sonatype.nexus.security.oidc.OidcConfigurationStore;

import com.google.common.collect.ImmutableMap;
import org.springframework.stereotype.Component;

/**
 * Publishes {@code oauth2Enabled} so the login page knows whether to render the SSO button, mirroring
 * the state the SAML entry point provides for its own flow.
 *
 * @since 3.89
 */
@Component
@Singleton
public class OidcStateContributor
    extends ComponentSupport
    implements StateContributor
{
  private final OidcConfigurationStore store;

  @Inject
  public OidcStateContributor(final OidcConfigurationStore store) {
    this.store = store;
  }

  @Override
  public Map<String, Object> getState() {
    OidcConfiguration configuration = store.load();
    return ImmutableMap.of("oauth2Enabled", configuration != null && configuration.isEnabled());
  }
}
