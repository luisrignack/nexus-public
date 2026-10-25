/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype Nexus" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.repository.nuget;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Helpers for NuGet package coordinates, storage paths and version (SemVer) comparison.
 *
 * <p>All helpers are pure/static so they can be unit tested without a repository context.</p>
 */
public final class NugetCoordinatesHelper
{
  public static final String INDEX_JSON = "index.json";

  public static final String FLAT_CONTAINER = "v3-flatcontainer";

  public static final String REGISTRATION = "v3/registration";

  public static final String QUERY = "v3/query";

  public static final String API_V2_PACKAGE = "api/v2/package";

  public static final String NUPKG_EXTENSION = ".nupkg";

  public static final String NUSPEC_EXTENSION = ".nuspec";

  private static final Pattern VERSION_PATTERN =
      Pattern.compile("\\d{1,10}(\\.\\d{1,10}){0,3}(-[0-9A-Za-z\\-\\.]{1,64})?");

  private NugetCoordinatesHelper() {
    // no instances
  }

  // ------------------------------------------------------------------
  // path helpers
  // ------------------------------------------------------------------

  public static String stripLeadingSlash(final String path) {
    if (path == null) {
      return "";
    }
    String value = path;
    while (value.startsWith("/")) {
      value = value.substring(1);
    }
    return value;
  }

  public static String stripTrailingSlash(final String path) {
    if (path == null) {
      return "";
    }
    String value = path;
    while (value.endsWith("/")) {
      value = value.substring(0, value.length() - 1);
    }
    return value;
  }

  public static String ensureTrailingSlash(final String path) {
    if (path == null || path.isEmpty()) {
      return path;
    }
    return path.endsWith("/") ? path : path + "/";
  }

  /**
   * Percent-decodes a path segment; never throws.
   */
  public static String decode(final String path) {
    if (path == null || path.isEmpty()) {
      return "";
    }
    try {
      return URLDecoder.decode(path, StandardCharsets.UTF_8);
    }
    catch (IllegalArgumentException e) {
      return path;
    }
  }

  /**
   * Normalizes a raw path to the canonical form used for stored assets: leading slash, no trailing slash
   * (except for the repository root, which is normalized to {@code "/"}).
   */
  public static String normalizePath(final String path) {
    String decoded = decode(path);
    String stripped = stripLeadingSlash(decoded);
    if (stripped.isEmpty()) {
      return "/";
    }
    return "/" + stripped;
  }

  // ------------------------------------------------------------------
  // coordinates
  // ------------------------------------------------------------------

  /**
   * Package ids are always lower cased in NuGet v3 "flat container" URLs.
   */
  public static String normalizeId(final String id) {
    if (id == null) {
      return "";
    }
    return id.trim().toLowerCase(Locale.ROOT);
  }

  /**
   * Normalizes a version: trims and drops any {@code +buildmetadata} suffix.
   */
  public static String normalizeVersion(final String version) {
    if (version == null) {
      return "";
    }
    String value = version.trim();
    int plus = value.indexOf('+');
    if (plus >= 0) {
      value = value.substring(0, plus);
    }
    return value.trim();
  }

  public static boolean isValidId(final String id) {
    if (id == null) {
      return false;
    }
    String value = id.trim();
    if (value.isEmpty()) {
      return false;
    }
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
          || c == '.' || c == '-' || c == '_';
      if (!ok) {
        return false;
      }
    }
    return true;
  }

  public static boolean isValidVersion(final String version) {
    String value = normalizeVersion(version);
    if (value.isEmpty()) {
      return false;
    }
    return VERSION_PATTERN.matcher(value).matches();
  }

  // ------------------------------------------------------------------
  // semver
  // ------------------------------------------------------------------

  /**
   * Compares two versions using SemVer semantics (build metadata is ignored, pre-release sorts before release).
   */
  public static int compareVersions(final String left, final String right) {
    String[] a = splitPreRelease(normalizeVersion(left));
    String[] b = splitPreRelease(normalizeVersion(right));

    int core = compareCore(a[0], b[0]);
    if (core != 0) {
      return core;
    }

    boolean aRelease = a.length < 2 || a[1].isEmpty();
    boolean bRelease = b.length < 2 || b[1].isEmpty();
    if (aRelease && bRelease) {
      return 0;
    }
    if (aRelease) {
      return 1; // release has higher precedence
    }
    if (bRelease) {
      return -1;
    }
    return comparePreRelease(a[1], b[1]);
  }

  /**
   * Returns a new list of versions, normalized and sorted ascending using SemVer semantics.
   */
  public static List<String> sortVersions(final Collection<String> versions) {
    List<String> sorted = new ArrayList<>();
    if (versions != null) {
      for (String version : versions) {
        String normalized = normalizeVersion(version);
        if (!normalized.isEmpty() && !sorted.contains(normalized)) {
          sorted.add(normalized);
        }
      }
    }
    sorted.sort(NugetCoordinatesHelper::compareVersions);
    return sorted;
  }

  private static String[] splitPreRelease(final String version) {
    int dash = version.indexOf('-');
    if (dash < 0) {
      return new String[]{version};
    }
    return new String[]{version.substring(0, dash), version.substring(dash + 1)};
  }

  private static int compareCore(final String left, final String right) {
    String[] a = left.split("\\.");
    String[] b = right.split("\\.");
    int count = Math.max(a.length, b.length);
    for (int i = 0; i < count; i++) {
      String x = i < a.length ? a[i] : "0";
      String y = i < b.length ? b[i] : "0";
      int result = compareNumeric(x, y);
      if (result != 0) {
        return result;
      }
    }
    return 0;
  }

  private static int comparePreRelease(final String left, final String right) {
    String[] a = left.split("\\.");
    String[] b = right.split("\\.");
    int count = Math.min(a.length, b.length);
    for (int i = 0; i < count; i++) {
      String x = a[i];
      String y = b[i];
      boolean xNumber = isNumeric(x);
      boolean yNumber = isNumeric(y);
      int result;
      if (xNumber && yNumber) {
        result = compareNumeric(x, y);
      }
      else if (xNumber) {
        return -1; // numeric identifiers always have lower precedence
      }
      else if (yNumber) {
        return 1;
      }
      else {
        result = x.compareTo(y);
      }
      if (result != 0) {
        return result;
      }
    }
    return Integer.compare(a.length, b.length);
  }

  private static boolean isNumeric(final String value) {
    if (value.isEmpty()) {
      return false;
    }
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c < '0' || c > '9') {
        return false;
      }
    }
    return true;
  }

  private static int compareNumeric(final String left, final String right) {
    if (isNumeric(left) && isNumeric(right)) {
      String a = stripLeadingZeros(left);
      String b = stripLeadingZeros(right);
      if (a.length() != b.length()) {
        return Integer.compare(a.length(), b.length());
      }
      return a.compareTo(b);
    }
    return left.compareTo(right);
  }

  private static String stripLeadingZeros(final String value) {
    int i = 0;
    while (i < value.length() - 1 && value.charAt(i) == '0') {
      i++;
    }
    return value.substring(i);
  }

  // ------------------------------------------------------------------
  // storage layout
  // ------------------------------------------------------------------

  /**
   * {@code v3-flatcontainer/<id-lowercase>/<version>/} (no leading slash).
   */
  public static String flatContainerDirectory(final String id, final String version) {
    return FLAT_CONTAINER + "/" + normalizeId(id) + "/" + normalizeVersion(version) + "/";
  }

  /**
   * Canonical asset path of a package: {@code /v3-flatcontainer/<id>/<version>/<id>.<version>.nupkg}.
   */
  public static String nupkgPath(final String id, final String version) {
    return "/" + flatContainerDirectory(id, version)
        + normalizeId(id) + "." + normalizeVersion(version) + NUPKG_EXTENSION;
  }

  /**
   * Canonical asset path of the extracted manifest:
   * {@code /v3-flatcontainer/<id>/<version>/<id>.<version>.nuspec}.
   */
  public static String nuspecPath(final String id, final String version) {
    return "/" + flatContainerDirectory(id, version)
        + normalizeId(id) + "." + normalizeVersion(version) + NUSPEC_EXTENSION;
  }

  /**
   * Absolute package content URL for the given repository base URL.
   */
  public static String packageContentUrl(final String baseUrl, final String id, final String version) {
    return ensureTrailingSlash(baseUrl) + stripLeadingSlash(nupkgPath(id, version));
  }
}
