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
package org.sonatype.nexus.bootstrap.entrypoint.configuration;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Do not add logging to this class, it is used early in Nexus startup
 */
public final class NexusDirectoryConfiguration
{
  private static String compute(
      final String systemPropertyName,
      final String defaultValue)
  {
    String configuredValue = System.getProperty(systemPropertyName, defaultValue);

    // Compute the absolute resolved path without .. or . segments
    String value = Paths.get(configuredValue).toAbsolutePath().normalize().toString();

    // Set the system property so it is available in the rest of Nexus
    System.setProperty(systemPropertyName, value);

    return value;
  }

  /**
   * Key for the system property defining {@link BASEDIR}
   */
  public static final String BASEDIR_SYS_PROP = "karaf.base";

  /**
   * Key for the system property defining {@link DATADIR}
   */
  public static final String DATADIR_SYS_PROP = "karaf.data";

  /**
   * String representation of the base path of nexus.
   *
   * launch scripts set basedir to "../"
   */
  public static final String BASEDIR = compute(BASEDIR_SYS_PROP, "");

  /**
   * String representation of the data directory (e.g. where we write blobs, logs, etc.)
   */
  public static final String DATADIR = compute(DATADIR_SYS_PROP, "../sonatype-work/nexus3");

  public static Path getBasePath(final String... segments) {
    return Paths.get(BASEDIR, segments);
  }

  public static Path getDataPath(final String... segments) {
    return Paths.get(DATADIR, segments);
  }

  /**
   * Ensures the properties are loaded
   */
  public static void load() {
    // no-op we rely on the static constants being triggered during class loading.
  }

  private NexusDirectoryConfiguration() {
    // private
  }
}
