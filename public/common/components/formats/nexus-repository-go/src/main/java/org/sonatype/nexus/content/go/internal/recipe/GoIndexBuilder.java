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
package org.sonatype.nexus.content.go.internal.recipe;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

import org.sonatype.nexus.content.go.GoContentFacet;

/**
 * Builds the documents of the Go module proxy protocol (version lists and version metadata).
 */
public final class GoIndexBuilder
{
  private static final Comparator<String> VERSION_COMPARATOR = GoIndexBuilder::compareVersions;

  private GoIndexBuilder() {
  }

  /**
   * Builds the {@code /{module}/@v/list} document from the stored versions of a module.
   */
  public static byte[] buildVersionList(final GoContentFacet content, final String module) {
    TreeSet<String> versions = new TreeSet<>(VERSION_COMPARATOR);
    for (String version : content.versions(module)) {
      if (version != null && !version.isEmpty()) {
        versions.add(version);
      }
    }

    StringBuilder result = new StringBuilder();
    for (String version : versions) {
      result.append(version).append('\n');
    }
    return result.toString().getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Merges the version lists returned by several repositories.
   */
  public static byte[] mergeVersionLists(final List<String> bodies) {
    TreeSet<String> versions = new TreeSet<>(VERSION_COMPARATOR);
    for (String body : bodies) {
      if (body == null) {
        continue;
      }
      for (String line : body.split("\\R")) {
        String version = line.trim();
        if (!version.isEmpty()) {
          versions.add(version);
        }
      }
    }

    StringBuilder result = new StringBuilder();
    for (String version : versions) {
      result.append(version).append('\n');
    }
    return result.toString().getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Chooses the latest version from a version list body, preferring stable versions over pre-releases.
   */
  public static String latestVersion(final List<String> bodies) {
    TreeSet<String> versions = new TreeSet<>(VERSION_COMPARATOR);
    for (String body : bodies) {
      if (body == null) {
        continue;
      }
      for (String line : body.split("\\R")) {
        String version = line.trim();
        if (!version.isEmpty()) {
          versions.add(version);
        }
      }
    }

    String latestStable = null;
    String latest = null;
    for (String version : versions) {
      latest = version;
      if (!isPrerelease(version)) {
        latestStable = version;
      }
    }
    return latestStable != null ? latestStable : latest;
  }

  /**
   * Builds the JSON returned for a {@code .info} request.
   */
  public static byte[] buildInfo(final String module, final String version) {
    return info(version, Instant.now()).getBytes(StandardCharsets.UTF_8);
  }

  private static String info(final String version, final Instant time) {
    return "{\"Version\":\"" + escapeJson(version) + "\",\"Time\":\"" + time + "\"}\n";
  }

  /**
   * Builds the {@code go.mod} returned for a {@code .mod} request when it was not uploaded explicitly.
   */
  public static byte[] buildMod(final String module) {
    return ("module " + module + "\n").getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Escapes a value for inclusion in a JSON string.
   */
  public static String escapeJson(final String value) {
    if (value == null) {
      return "";
    }
    StringBuilder escaped = new StringBuilder();
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"':
          escaped.append("\\\"");
          break;
        case '\\':
          escaped.append("\\\\");
          break;
        case '\b':
          escaped.append("\\b");
          break;
        case '\f':
          escaped.append("\\f");
          break;
        case '\n':
          escaped.append("\\n");
          break;
        case '\r':
          escaped.append("\\r");
          break;
        case '\t':
          escaped.append("\\t");
          break;
        default:
          if (c < 0x20) {
            escaped.append(String.format("\\u%04x", (int) c));
          }
          else {
            escaped.append(c);
          }
      }
    }
    return escaped.toString();
  }

  private static boolean isPrerelease(final String version) {
    return version.indexOf('-') >= 0;
  }

  /**
   * Compares two module versions treating numeric components numerically ("v1.10.0" after "v1.9.0").
   */
  static int compareVersions(final String left, final String right) {
    List<String> leftParts = split(left);
    List<String> rightParts = split(right);
    int size = Math.max(leftParts.size(), rightParts.size());
    for (int i = 0; i < size; i++) {
      String leftPart = i < leftParts.size() ? leftParts.get(i) : "0";
      String rightPart = i < rightParts.size() ? rightParts.get(i) : "0";

      boolean leftNumeric = leftPart.matches("\\d+");
      boolean rightNumeric = rightPart.matches("\\d+");
      int comparison;
      if (leftNumeric && rightNumeric) {
        comparison = new java.math.BigInteger(leftPart).compareTo(new java.math.BigInteger(rightPart));
      }
      else if (leftNumeric != rightNumeric) {
        // a numeric part sorts before a pre-release suffix
        comparison = leftNumeric ? 1 : -1;
      }
      else {
        comparison = leftPart.compareTo(rightPart);
      }
      if (comparison != 0) {
        return comparison;
      }
    }
    return left.compareTo(right);
  }

  private static List<String> split(final String version) {
    List<String> parts = new ArrayList<>();
    if (version == null) {
      return parts;
    }
    String value = version.startsWith("v") ? version.substring(1) : version;
    value = value.replace('+', '-');
    for (String part : value.split("[.-]")) {
      if (!part.isEmpty()) {
        parts.add(part);
      }
    }
    return parts;
  }
}
