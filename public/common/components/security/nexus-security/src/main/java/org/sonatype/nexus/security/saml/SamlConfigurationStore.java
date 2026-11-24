/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 */
package org.sonatype.nexus.security.saml;

import javax.annotation.Nullable;

/**
 * Persistence contract for the SAML configuration.
 */
public interface SamlConfigurationStore
{
  SamlConfiguration newEntity();

  @Nullable
  SamlConfiguration load();

  void save(SamlConfiguration configuration);

  void delete();
}
