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
package org.sonatype.nexus.repository.helm;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Helper methods for Helm chart repositories.
 *
 * <p>A Helm repository exposes an index at {@code /index.yaml} and serves chart archives at {@code /charts/{name}-{version}.tgz}
 * or at any path; charts may also be accessed from custom paths.</p>
 */
public final class HelmCoordinatesHelper
{
  public static final String INDEX_YAML = "/index.yaml";

  public static final String CHARTS_PREFIX = "/charts";

  private HelmCoordinatesHelper() {
  }

  public static String stripLeadingSlash(final String path) {
    return path != null && path.startsWith("/") ? path.substring(1) : path;
  }

  public static String stripTrailingSlash(final String path) {
    if (path == null) {
      return "";
    }
    int end = path.length();
    while (end > 0 && path.charAt(end - 1) == '/') {
      end--;
    }
    return path.substring(0, end);
  }

  /**
   * Returns {@code true} if the path points to the repository index.
   */
  public static boolean isIndex(final String path) {
    String normalized = stripTrailingSlash(path);
    return INDEX_YAML.equals(normalized) || "/".equals(normalized) || "/index.yaml".equals(normalized);
  }

  /**
   * Returns the file name of the last segment of a path or URL.
   */
  public static String fileName(final String path) {
    if (path == null) {
      return "";
    }
    String withoutFragment = path;
    int fragment = withoutFragment.indexOf('#');
    if (fragment >= 0) {
      withoutFragment = withoutFragment.substring(0, fragment);
    }
    int query = withoutFragment.indexOf('?');
    if (query >= 0) {
      withoutFragment = withoutFragment.substring(0, query);
    }
    withoutFragment = stripTrailingSlash(withoutFragment);
    int slash = withoutFragment.lastIndexOf('/');
    return slash >= 0 ? withoutFragment.substring(slash + 1) : withoutFragment;
  }

  /**
   * Best-effort chart name from a filename like {@code nginx-1.2.3.tgz}.
   */
  public static String chartNameFromFileName(final String fileName) {
    String name = fileName(fileName);
    if (name.toLowerCase().endsWith(".tgz")) {
      name = name.substring(0, name.length() - 4);
    }
    int lastDash = name.lastIndexOf('-');
    if (lastDash <= 0) {
      return name;
    }
    // version usually starts with a digit after the last dash; be permissive
    return name.substring(0, lastDash);
  }

  /**
   * Best-effort chart version from a filename like {@code nginx-1.2.3.tgz}.
   */
  public static Optional<String> versionFromFileName(final String fileName) {
    String name = fileName(fileName);
    if (name.toLowerCase().endsWith(".tgz")) {
      name = name.substring(0, name.length() - 4);
    }
    int lastDash = name.lastIndexOf('-');
    if (lastDash <= 0 || lastDash == name.length() - 1) {
      return Optional.empty();
    }
    String version = name.substring(lastDash + 1);
    if (!Character.isDigit(version.charAt(0))) {
      return Optional.empty();
    }
    return Optional.of(version);
  }

  /**
   * URL-decodes a path segment.
   */
  public static String decode(final String value) {
    if (value == null) {
      return null;
    }
    try {
      return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name());
    }
    catch (UnsupportedEncodingException | IllegalArgumentException e) {
      return value;
    }
  }
}
