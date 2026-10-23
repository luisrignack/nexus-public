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
package org.sonatype.nexus.repository.pypi;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper methods for PyPI paths, project names and package file names.
 *
 * <p>Project names are normalized following PEP 503: lower case with runs of {@code -, _, .}
 * replaced by a single {@code -}.</p>
 */
public final class PyPiCoordinatesHelper
{
  public static final String SIMPLE_PREFIX = "/simple";

  public static final String PACKAGES_PREFIX = "/packages";

  private static final Pattern NORMALIZE = Pattern.compile("[-_.]+");

  private static final Pattern SDIST = Pattern.compile("^(.+)-(\\d[^-]*)\\.(?:tar\\.gz|tar\\.bz2|zip)$");

  private static final Pattern WHEEL = Pattern.compile("^(.+)-(\\d[^-]*)-[^-]+-[^-]+-[^-]+\\.whl$");

  private static final Pattern EGG = Pattern.compile("^(.+)-(\\d[^-]*)-py[^-]+\\.egg$");

  private PyPiCoordinatesHelper() {
  }

  /**
   * PEP 503 normalization of a project name.
   */
  public static String normalizeProjectName(final String name) {
    if (name == null) {
      return "";
    }
    return NORMALIZE.matcher(name.trim().toLowerCase(Locale.ROOT)).replaceAll("-");
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
   * Extracts the project token from a {@code /simple/{project}/} request path, or returns empty.
   */
  public static Optional<String> projectFromIndexPath(final String path) {
    String normalized = stripTrailingSlash(path);
    if (!normalized.startsWith(SIMPLE_PREFIX + "/")) {
      return Optional.empty();
    }
    String project = normalized.substring((SIMPLE_PREFIX + "/").length());
    if (project.isEmpty() || project.indexOf('/') >= 0) {
      return Optional.empty();
    }
    return Optional.of(normalizeProjectName(decode(project)));
  }

  public static boolean isSimpleIndex(final String path) {
    String normalized = stripTrailingSlash(path);
    return SIMPLE_PREFIX.equals(normalized) || "".equals(normalized);
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

  /**
   * Extracts the base64url token from a {@code /packages/{token}/{filename}} path.
   */
  public static Optional<String> upstreamTokenFromPackagePath(final String path) {
    String stripped = stripLeadingSlash(path);
    if (!stripped.startsWith("packages/")) {
      return Optional.empty();
    }
    String rest = stripped.substring("packages/".length());
    int slash = rest.indexOf('/');
    if (slash <= 0 || slash == rest.length() - 1) {
      return Optional.empty();
    }
    return Optional.of(rest.substring(0, slash));
  }

  /**
   * Removes any leading path component from a client supplied file name.
   */
  public static String sanitizeFileName(final String fileName) {
    if (fileName == null || fileName.isEmpty()) {
      return "package.bin";
    }
    String name = decode(fileName);
    int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
    if (slash >= 0) {
      name = name.substring(slash + 1);
    }
    if (".".equals(name) || "..".equals(name) || name.isEmpty()) {
      return "package.bin";
    }
    return name;
  }

  /**
   * Best-effort project name from a distribution file name.
   */
  public static String projectFromFileName(final String fileName) {
    String name = fileName(fileName);
    name = name.replaceAll("(?i)\\.(?:tar\\.gz|tar\\.bz2|zip|whl|egg)$", "");
    int dash = name.indexOf('-');
    return dash > 0 ? normalizeProjectName(name.substring(0, dash)) : normalizeProjectName(name);
  }

  /**
   * Best-effort version from a distribution file name, if it starts with a digit.
   */
  public static Optional<String> versionFromFileName(final String fileName) {
    String name = fileName(fileName);
    Matcher matcher = WHEEL.matcher(name);
    if (!matcher.matches()) {
      matcher = SDIST.matcher(name);
    }
    if (!matcher.matches()) {
      matcher = EGG.matcher(name);
    }
    return matcher.matches() ? Optional.of(matcher.group(2)) : Optional.empty();
  }

  /**
   * Escapes text for inclusion in HTML.
   */
  public static String escapeHtml(final String value) {
    if (value == null) {
      return "";
    }
    return value.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }
}
