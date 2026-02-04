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
package org.sonatype.nexus.common.app;

import java.io.File;

/**
 * !!!! DEPRECATED in favor of {@link org.sonatype.nexus.bootstrap.entrypoint.configuration.ApplicationDirectories}
 * This class was moved to be loaded and usable in the first pass of injection. Left this deprecated class simply
 * because of the widespread usage. This class should be removed when the previous DI architecture is removed. Until
 * then changes should primarily be done on the newer impl, then only brought back to this
 * class if necessary
 * -------------------------------------------------------
 * Old javadoc
 * Provides access to application directories.
 * 
 * @since 2.8
 */
@Deprecated(since = "4/1/2025", forRemoval = true)
public interface ApplicationDirectories
{
  // TODO: Add nio path equivalents

  /**
   * Installation directory.
   */
  File getInstallDirectory();

  /**
   * Configuration directory.
   *
   * @param subsystem Sub-system name
   */
  File getConfigDirectory(String subsystem);

  /**
   * Temporary directory.
   */
  File getTemporaryDirectory();

  /**
   * Work directory.
   */
  File getWorkDirectory();

  /**
   * Work sub-directory.
   *
   * @param path Sub-directory path.
   * @param create True to create the directory if it does not exist.
   */
  File getWorkDirectory(String path, boolean create);

  /**
   * Work sub-directory.
   */
  File getWorkDirectory(String path);
}
