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
package org.sonatype.nexus.repository.npm.internal;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helpers to derive npm coordinates from request paths.
 */
public final class NpmCoordinatesHelper
{
  private static final Pattern TARBALL = Pattern.compile("^.+-((\\d+\\.\\d+\\.\\d+)(?:[-+][0-9A-Za-z.-]+)?)\\.(?:tgz|tar\\.gz)$");

  private static final Pattern VERSION = Pattern.compile("^\\d+\\.\\d+\\.\\d+(?:[-+][0-9A-Za-z.-]+)?$");

  private NpmCoordinatesHelper() {
  }

  /**
   * Decodes a URL path, turning {@code %40scope%2Fname} into {@code @scope/name}.
   */
  public static String decode(final String path) {
    try {
      return URLDecoder.decode(path.replace("+", "%2B"), StandardCharsets.UTF_8.name());
    }
    catch (UnsupportedEncodingException | IllegalArgumentException e) {
      return path;
    }
  }

  /**
   * Component path is everything before the {@code /-/} separator, e.g.
   * {@code @scope/name/-/name-1.0.0.tgz} becomes {@code @scope/name}.
   */
  public static String getComponentPath(final String path) {
    String decoded = stripLeadingSlash(decode(path));
    int index = decoded.indexOf("/-/");
    return index >= 0 ? decoded.substring(0, index) : decoded;
  }

  public static String stripLeadingSlash(final String path) {
    return path != null && path.startsWith("/") ? path.substring(1) : path;
  }

  public static String getComponentGroup(final String path) {
    String componentPath = getComponentPath(path);
    int index = componentPath.lastIndexOf('/');
    return index >= 0 ? componentPath.substring(0, index) : "";
  }

  public static String getComponentName(final String path) {
    String componentPath = getComponentPath(path);
    int index = componentPath.lastIndexOf('/');
    return index >= 0 ? componentPath.substring(index + 1) : componentPath;
  }

  /**
   * Version encoded in the tarball name, e.g. {@code name/-/name-1.2.3-beta.1.tgz} yields
   * {@code 1.2.3-beta.1}. Packument paths have no version and return {@code null}.
   */
  public static String getComponentVersion(final String path) {
    String decoded = decode(path);
    int index = decoded.lastIndexOf("/-/");
    if (index < 0) {
      return null;
    }
    Matcher matcher = TARBALL.matcher(decoded.substring(index + 3));
    return matcher.matches() ? matcher.group(1) : null;
  }

  /**
   * Returns the package path when the given path is a version manifest request
   * ({@code name/1.0.0} or {@code @scope/name/1.0.0}), otherwise {@code null}.
   */
  public static String getPackagePathForManifest(final String path) {
    String decoded = stripLeadingSlash(decode(path));
    if (decoded.startsWith("-/") || decoded.contains("/-/")) {
      return null;
    }
    int index = decoded.lastIndexOf('/');
    if (index < 0) {
      return null;
    }
    String packagePath = decoded.substring(0, index);
    if (packagePath.endsWith("/-")) {
      return null;
    }
    return VERSION.matcher(decoded.substring(index + 1)).matches() ? packagePath : null;
  }

  public static boolean isVersion(final String value) {
    return value != null && VERSION.matcher(value).matches();
  }

  /**
   * Returns the version encoded in a tarball file name, or {@code null} when it cannot be determined.
   */
  public static String getVersionFromTarballName(final String fileName) {
    if (fileName == null) {
      return null;
    }
    int slash = fileName.lastIndexOf('/');
    String name = slash >= 0 ? fileName.substring(slash + 1) : fileName;
    Matcher matcher = TARBALL.matcher(name);
    return matcher.matches() ? matcher.group(1) : null;
  }

  /**
   * Compares two npm (semver) versions. Build metadata is ignored; prerelease versions sort before the
   * corresponding release, following the semver precedence rules.
   */
  public static int compareVersions(final String left, final String right) {
    if (left == null && right == null) {
      return 0;
    }
    if (left == null) {
      return -1;
    }
    if (right == null) {
      return 1;
    }

    String[] leftParts = splitVersion(left);
    String[] rightParts = splitVersion(right);

    int core = compareCore(leftParts[0], rightParts[0]);
    if (core != 0) {
      return core;
    }

    String leftPre = leftParts[1];
    String rightPre = rightParts[1];
    if (leftPre == null && rightPre == null) {
      return 0;
    }
    if (leftPre == null) {
      return 1;
    }
    if (rightPre == null) {
      return -1;
    }

    String[] leftIds = leftPre.split("\\.");
    String[] rightIds = rightPre.split("\\.");
    int length = Math.max(leftIds.length, rightIds.length);
    for (int i = 0; i < length; i++) {
      if (i >= leftIds.length) {
        return -1;
      }
      if (i >= rightIds.length) {
        return 1;
      }
      int result = compareIdentifier(leftIds[i], rightIds[i]);
      if (result != 0) {
        return result;
      }
    }
    return 0;
  }

  /**
   * Returns {@code true} when the version has a prerelease component (for example {@code 1.0.0-beta.1}).
   */
  public static boolean isPrerelease(final String version) {
    if (version == null) {
      return false;
    }
    String value = version.trim();
    int plus = value.indexOf('+');
    if (plus >= 0) {
      value = value.substring(0, plus);
    }
    return value.indexOf('-') >= 0;
  }

  /**
   * Returns the highest non-prerelease version of the given collection, or {@code null} when there is none.
   */
  public static String highestStableVersion(final Collection<String> versions) {
    String highest = null;
    if (versions != null) {
      for (String version : versions) {
        if (version != null && !version.isEmpty() && !isPrerelease(version)
            && (highest == null || compareVersions(version, highest) > 0)) {
          highest = version;
        }
      }
    }
    return highest;
  }

  /**
   * Returns the highest version of the given collection, or {@code null} when empty.
   */
  public static String highestVersion(final Collection<String> versions) {
    String highest = null;
    if (versions != null) {
      for (String version : versions) {
        if (version != null && !version.isEmpty() && (highest == null || compareVersions(version, highest) > 0)) {
          highest = version;
        }
      }
    }
    return highest;
  }

  private static String[] splitVersion(final String version) {
    String value = version.trim();
    int plus = value.indexOf('+');
    if (plus >= 0) {
      value = value.substring(0, plus);
    }
    int dash = value.indexOf('-');
    if (dash >= 0) {
      return new String[]{value.substring(0, dash), value.substring(dash + 1)};
    }
    return new String[]{value, null};
  }

  private static int compareCore(final String left, final String right) {
    String[] leftParts = left.split("\\.");
    String[] rightParts = right.split("\\.");
    int length = Math.max(leftParts.length, rightParts.length);
    for (int i = 0; i < length; i++) {
      int leftNumber = parseNumber(i < leftParts.length ? leftParts[i] : "0");
      int rightNumber = parseNumber(i < rightParts.length ? rightParts[i] : "0");
      if (leftNumber != rightNumber) {
        return Integer.compare(leftNumber, rightNumber);
      }
    }
    return 0;
  }

  private static int parseNumber(final String value) {
    try {
      return Integer.parseInt(value);
    }
    catch (NumberFormatException e) {
      return 0;
    }
  }

  private static int compareIdentifier(final String left, final String right) {
    boolean leftNumeric = isNumeric(left);
    boolean rightNumeric = isNumeric(right);
    if (leftNumeric && rightNumeric) {
      return Integer.compare(parseNumber(left), parseNumber(right));
    }
    if (leftNumeric) {
      return -1;
    }
    if (rightNumeric) {
      return 1;
    }
    return left.compareTo(right);
  }

  private static boolean isNumeric(final String value) {
    if (value.isEmpty()) {
      return false;
    }
    for (int i = 0; i < value.length(); i++) {
      if (!Character.isDigit(value.charAt(i))) {
        return false;
      }
    }
    return true;
  }
}
