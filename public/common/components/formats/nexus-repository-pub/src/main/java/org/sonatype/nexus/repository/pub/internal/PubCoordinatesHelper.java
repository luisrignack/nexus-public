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
package org.sonatype.nexus.repository.pub.internal;

import java.util.Optional;

/**
 * Paths of the Hosted Pub Repository Specification v2.
 *
 * <p>Pub uses two path families: metadata under {@code /api/packages/...} and archives under
 * {@code /packages/<name>/versions/<version>.tar.gz}. Keeping them in one place avoids the
 * duplicated string handling that otherwise creeps into handlers, facets and tests.</p>
 */
public final class PubCoordinatesHelper
{
  public static final String API_PREFIX = "/api/packages/";

  public static final String ARCHIVE_PREFIX = "/packages/";

  public static final String ARCHIVE_SUFFIX = ".tar.gz";

  public static final String NEW_UPLOAD_PATH = "/api/packages/versions/new";

  public static final String UPLOAD_PATH = "/api/packages/versions/upload";

  public static final String FINALIZE_PATH = "/api/packages/versions/finalize";

  private PubCoordinatesHelper() {
    // utility class
  }

  public static boolean isMetadata(final String path) {
    return path != null && path.startsWith(API_PREFIX);
  }

  public static boolean isArchive(final String path) {
    return path != null && path.startsWith(ARCHIVE_PREFIX) && path.endsWith(ARCHIVE_SUFFIX);
  }

  /**
   * Package name of a metadata request, if the path is a package metadata path.
   * The reserved {@code versions} endpoints are deliberately not treated as packages.
   */
  public static Optional<String> packageFromMetadata(final String path) {
    if (!isMetadata(path)) {
      return Optional.empty();
    }
    String remainder = path.substring(API_PREFIX.length());
    if (remainder.isEmpty() || remainder.startsWith("versions/")) {
      return Optional.empty();
    }
    int slash = remainder.indexOf('/');
    String name = slash < 0 ? remainder : remainder.substring(0, slash);
    return name.isEmpty() ? Optional.empty() : Optional.of(name);
  }

  public static String archivePath(final String name, final String version) {
    return ARCHIVE_PREFIX + name + "/versions/" + version + ARCHIVE_SUFFIX;
  }

  /**
   * Splits {@code /packages/<name>/versions/<version>.tar.gz} into its coordinates.
   */
  public static Optional<String[]> fromArchivePath(final String path) {
    if (!isArchive(path)) {
      return Optional.empty();
    }
    String remainder = path.substring(ARCHIVE_PREFIX.length(), path.length() - ARCHIVE_SUFFIX.length());
    String[] parts = remainder.split("/", -1);
    if (parts.length != 3 || !"versions".equals(parts[1]) || parts[0].isEmpty() || parts[2].isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new String[] {parts[0], parts[2]});
  }

  public static String validatePackageName(final String name) {
    if (name == null || !name.matches("[a-z0-9_]+")) {
      throw new IllegalArgumentException("Invalid Pub package name: " + name);
    }
    return name;
  }

  public static String validateVersion(final String version) {
    if (version == null || version.isEmpty() || version.contains("/") || version.contains(" ")) {
      throw new IllegalArgumentException("Invalid Pub package version: " + version);
    }
    return version;
  }
}
