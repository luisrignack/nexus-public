/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 */
package org.sonatype.nexus.coreui.internal.saml;

import java.util.Map;

import jakarta.inject.Inject;

import org.sonatype.nexus.rapture.StateContributor;
import org.sonatype.nexus.security.authc.saml.SamlRealm;
import org.sonatype.nexus.security.realm.RealmManager;

import com.google.common.collect.ImmutableMap;
import org.springframework.stereotype.Component;

/**
 * Publishes the SAML realm state consumed by the login page and the settings tile.
 */
@Component
public class SamlStateContributor
    implements StateContributor
{
  private final RealmManager realmManager;

  @Inject
  public SamlStateContributor(final RealmManager realmManager) {
    this.realmManager = realmManager;
  }

  @Override
  public Map<String, Object> getState() {
    return ImmutableMap.of("samlEnabled", realmManager.isRealmEnabled(SamlRealm.REALM_NAME));
  }
}
