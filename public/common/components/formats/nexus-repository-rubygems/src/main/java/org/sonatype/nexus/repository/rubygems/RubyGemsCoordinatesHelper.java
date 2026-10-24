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
package org.sonatype.nexus.repository.rubygems;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;

/**
 * Helper methods for RubyGems.
 *
 * <p>Supports the RubyGems API endpoints: {@code /api/v1/gems} (push), {@code /gems/{name}-{version}.gem},
 * and index files like {@code /specs.4.8.gz}, {@code /latest_specs.4.8.gz}, {@code /prerelease_specs.4.8.gz},
 * and the marshalled API index {@code /api/v1/dependencies}.</p>
 */
public final class RubyGemsCoordinatesHelper
{
  public static final String GEMS_PREFIX = "/gems";

  public static final String QUICK_PREFIX = "/quick/Marshal.4.8";

  public static final String API_V1_GEMS = "/api/v1/gems";

  public static final String API_V1_DEPENDENCIES = "/api/v1/dependencies";

  private RubyGemsCoordinatesHelper() {
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
   * Best-effort gem name from a filename like {@code rails-6.1.0.gem}.
   */
  public static String gemNameFromFileName(final String fileName) {
    String name = fileName(fileName);
    if (name.toLowerCase().endsWith(".gem")) {
      name = name.substring(0, name.length() - 4);
    }
    int lastDash = name.lastIndexOf('-');
    if (lastDash <= 0) {
      return name;
    }
    String version = name.substring(lastDash + 1);
    if (Character.isDigit(version.charAt(0))) {
      return name.substring(0, lastDash);
    }
    // try earlier dash for gems with pre-release versions containing multiple dashes
    int dash2 = name.lastIndexOf('-', lastDash - 1);
    if (dash2 > 0 && Character.isDigit(name.charAt(dash2 + 1))) {
      return name.substring(0, dash2);
    }
    return name.substring(0, lastDash);
  }

  /**
   * Best-effort gem version from a filename like {@code rails-6.1.0.gem}.
   */
  public static Optional<String> versionFromFileName(final String fileName) {
    String name = fileName(fileName);
    if (name.toLowerCase().endsWith(".gem")) {
      name = name.substring(0, name.length() - 4);
    }
    int lastDash = name.lastIndexOf('-');
    if (lastDash <= 0 || lastDash == name.length() - 1) {
      return Optional.empty();
    }
    String version = name.substring(lastDash + 1);
    if (Character.isDigit(version.charAt(0))) {
      return Optional.of(version);
    }
    int dash2 = name.lastIndexOf('-', lastDash - 1);
    if (dash2 > 0 && dash2 < name.length() - 1 && Character.isDigit(name.charAt(dash2 + 1))) {
      return Optional.of(name.substring(dash2 + 1));
    }
    return Optional.empty();
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

  /**
   * Base64url (unpadded) encoding of an upstream URL.
   */
  public static String encodeUpstreamUrl(final String url) {
    return Base64.getUrlEncoder().withoutPadding()
        .encodeToString(url.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Decodes a base64url token produced by {@link #encodeUpstreamUrl(String)}.
   */
  public static Optional<String> decodeUpstreamUrl(final String token) {
    try {
      byte[] decoded = Base64.getUrlDecoder().decode(token);
      return Optional.of(new String(decoded, StandardCharsets.UTF_8));
    }
    catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
