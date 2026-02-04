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
package org.sonatype.nexus.repository.importtask;

/**
 * Utility methods for working with asset paths during import operations.
 */
public final class AssetPathUtils
{
  private AssetPathUtils() {
    // utility class
  }

  /**
   * Extracts the filename from an asset path.
   * <p>
   * This method extracts the last path component from an asset path by finding the last
   * forward slash and returning everything after it. If no slash is found, returns the
   * entire path as the filename.
   *
   * @param assetPath the full asset path (e.g., "path/to/file.gem" or "file.rpm")
   * @return the extracted filename (e.g., "file.gem" or "file.rpm")
   */
  public static String extractFilename(final String assetPath) {
    if (assetPath == null) {
      return null;
    }

    int lastSlash = assetPath.lastIndexOf('/');
    if (lastSlash >= 0) {
      return assetPath.substring(lastSlash + 1);
    }

    return assetPath;
  }
}
