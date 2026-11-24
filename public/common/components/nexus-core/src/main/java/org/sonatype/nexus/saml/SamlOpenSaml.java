/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 */
package org.sonatype.nexus.saml;

import java.util.concurrent.atomic.AtomicBoolean;

import org.opensaml.core.config.InitializationService;

/**
 * Idempotent OpenSAML bootstrap shared by every SAML entry point.
 */
public final class SamlOpenSaml
{
  private static final AtomicBoolean INITIALIZED = new AtomicBoolean();

  private SamlOpenSaml() {
    // utility class
  }

  public static void initialize() {
    if (INITIALIZED.compareAndSet(false, true)) {
      try {
        InitializationService.initialize();
      }
      catch (Exception e) {
        INITIALIZED.set(false);
        throw new IllegalStateException("Unable to initialize OpenSAML", e);
      }
    }
  }
}
