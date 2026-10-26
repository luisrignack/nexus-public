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
package org.sonatype.nexus.repository.conan;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A Conan package reference and its location in the storage tree.
 *
 * <p>A reference is {@code name/version@user/channel}; when a client omits the user and the channel (Conan 2 style
 * references such as {@code zlib/1.2.13}) both default to {@code _}, so the canonical reference becomes
 * {@code zlib/1.2.13@_/_} and the canonical storage path
 * {@code conans/zlib/1.2.13/_/_/revisions/<rrev>/exports/...}.</p>
 *
 * @since 3.89
 */
public final class ConanCoordinates
{
  /**
   * Placeholder used by Conan for an absent user.
   */
  public static final String DEFAULT_USER = "_";

  /**
   * Placeholder used by Conan for an absent channel.
   */
  public static final String DEFAULT_CHANNEL = "_";

  /**
   * Root of every Conan storage tree inside a blob store.
   */
  public static final String STORAGE_ROOT = "conans";

  private final String name;

  private final String version;

  private final String user;

  private final String channel;

  private ConanCoordinates(final String name, final String version, final String user, final String channel) {
    this.name = name;
    this.version = version;
    this.user = user;
    this.channel = channel;
  }

  /**
   * Creates coordinates applying the {@code _} defaults for a missing user or channel.
   */
  public static ConanCoordinates of(
      final String name,
      final String version,
      final String user,
      final String channel)
  {
    checkNotNull(name, "name");
    checkNotNull(version, "version");
    return new ConanCoordinates(
        name,
        version,
        isBlank(user) ? DEFAULT_USER : user,
        isBlank(channel) ? DEFAULT_CHANNEL : channel);
  }

  public String name() {
    return name;
  }

  public String version() {
    return version;
  }

  public String user() {
    return user;
  }

  public String channel() {
    return channel;
  }

  /**
   * Component namespace, {@code user/channel}.
   */
  public String namespace() {
    return user + "/" + channel;
  }

  /**
   * Parses the coordinates embedded in a storage path, or {@code null} when the path does not follow the canonical
   * {@code conans/{name}/{version}/{user}/{channel}/...} layout.
   */
  public static ConanCoordinates fromStoragePath(final String path) {
    List<String> segments = segments(path);
    if (segments.size() < 5 || !STORAGE_ROOT.equals(segments.get(0))) {
      return null;
    }
    try {
      return of(segments.get(1), segments.get(2), segments.get(3), segments.get(4));
    }
    catch (final RuntimeException e) {
      return null;
    }
  }

  /**
   * Human readable reference {@code name/version@user/channel}.
   */
  public String reference() {
    return name + "/" + version + "@" + user + "/" + channel;
  }

  /**
   * Storage path of the reference itself: {@code conans/{name}/{version}/{user}/{channel}}.
   * The same segments are used by the {@code v2/conans/...} API paths.
   */
  public String storagePath() {
    return STORAGE_ROOT + "/" + name + "/" + version + "/" + user + "/" + channel;
  }

  /**
   * Prefix holding every recipe revision of this reference.
   */
  public String revisionsPrefix() {
    return storagePath() + "/revisions/";
  }

  /**
   * Prefix holding the recipe files of one revision.
   */
  public String recipeRevisionPrefix(final String revision) {
    return revisionsPrefix() + revision + "/exports/";
  }

  /**
   * Prefix holding the package revisions of one recipe revision and package id.
   */
  public String packageRevisionPrefix(final String revision, final String packageId) {
    return revisionsPrefix() + revision + "/packages/" + packageId + "/revisions/";
  }

  /**
   * Prefix holding the binary files of one package revision.
   */
  public String packageFilesPrefix(final String revision, final String packageId, final String packageRevision) {
    return packageRevisionPrefix(revision, packageId) + packageRevision + "/";
  }

  /**
   * Absolute (from the repository root) storage path of a recipe file.
   */
  public String recipeFilePath(final String revision, final String filename) {
    return recipeRevisionPrefix(revision) + filename;
  }

  /**
   * Absolute (from the repository root) storage path of a package binary file.
   */
  public String packageFilePath(
      final String revision,
      final String packageId,
      final String packageRevision,
      final String filename)
  {
    return packageFilesPrefix(revision, packageId, packageRevision) + filename;
  }

  /**
   * API path, relative to the repository root and to the {@code v2/} prefix, listing the files of a recipe revision.
   */
  public String filesApiPath(final String revision) {
    return storagePath() + "/revisions/" + revision + "/files";
  }

  /**
   * API path, relative to the repository root and to the {@code v2/} prefix, listing the files of a package revision.
   */
  public String packageFilesApiPath(
      final String revision,
      final String packageId,
      final String packageRevision)
  {
    return storagePath() + "/revisions/" + revision + "/packages/" + packageId + "/revisions/" + packageRevision
        + "/files";
  }

  private static boolean isBlank(final String value) {
    return value == null || value.trim().isEmpty();
  }

  //
  // path helpers
  //

  /**
   * Percent-decodes a path segment without treating {@code +} as a space (Conan versions such as {@code 1.0+build}
   * are legal).
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
   * Normalizes a raw request path: percent-decodes it and strips leading slashes so paths can be compared with the
   * storage paths, which never start with a slash.
   */
  public static String normalize(final String rawPath) {
    String decoded = decode(rawPath == null ? "" : rawPath);
    int start = 0;
    while (start < decoded.length() && decoded.charAt(start) == '/') {
      start++;
    }
    return decoded.substring(start);
  }

  /**
   * Converts any path to the absolute form required by {@code assets().path(...)}.
   */
  public static String assetPath(final String rawPath) {
    String normalized = normalize(rawPath);
    return normalized.isEmpty() ? "/" : "/" + normalized;
  }

  /**
   * Splits a storage path into its segments, ignoring empty ones.
   */
  public static List<String> segments(final String path) {
    List<String> segments = new ArrayList<>();
    if (path == null) {
      return segments;
    }
    for (String segment : path.split("/")) {
      if (!segment.isEmpty()) {
        segments.add(segment);
      }
    }
    return segments;
  }

  /**
   * Extracts the recipe revision from a storage path, or {@code null} when the path holds no revision.
   */
  public static String revisionFromPath(final String path) {
    List<String> segments = segments(path);
    int index = segments.indexOf("revisions");
    return segmentAfter(segments, index);
  }

  /**
   * Extracts the package id from a storage path, or {@code null} when the path holds no package id.
   */
  public static String packageIdFromPath(final String path) {
    List<String> segments = segments(path);
    int index = segments.indexOf("packages");
    return segmentAfter(segments, index);
  }

  /**
   * Extracts the package revision from a storage path (the last {@code revisions} segment), or {@code null}.
   */
  public static String packageRevisionFromPath(final String path) {
    List<String> segments = segments(path);
    int index = segments.lastIndexOf("revisions");
    return segmentAfter(segments, index);
  }

  private static String segmentAfter(final List<String> segments, final int index) {
    if (index < 0 || index + 1 >= segments.size()) {
      return null;
    }
    return segments.get(index + 1);
  }

  @Override
  public boolean equals(final Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof ConanCoordinates)) {
      return false;
    }
    ConanCoordinates that = (ConanCoordinates) other;
    return name.equals(that.name)
        && version.equals(that.version)
        && user.equals(that.user)
        && channel.equals(that.channel);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, version, user, channel);
  }

  @Override
  public String toString() {
    return reference();
  }
}
