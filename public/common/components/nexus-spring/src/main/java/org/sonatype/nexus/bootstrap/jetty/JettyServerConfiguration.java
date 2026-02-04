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
package org.sonatype.nexus.bootstrap.jetty;

import java.util.ArrayList;
import java.util.List;

/**
 * Jetty server default configuration.
 */
public class JettyServerConfiguration
{
  private final List<ConnectorConfiguration> defaultConnectors;

  private final List<ConnectorConfiguration> customConnectors;

  public JettyServerConfiguration(final List<ConnectorConfiguration> defaultConnectors) {
    this(defaultConnectors, new ArrayList<>());
  }

  public JettyServerConfiguration(
      final List<ConnectorConfiguration> defaultConnectors,
      final List<ConnectorConfiguration> customConnectors)
  {
    this.defaultConnectors = defaultConnectors;
    this.customConnectors = customConnectors;
  }

  public List<ConnectorConfiguration> defaultConnectors() {
    return defaultConnectors;
  }

  public List<ConnectorConfiguration> customConnectors() {
    return customConnectors;
  }
}
