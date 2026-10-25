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

import java.util.Arrays;
import java.util.Objects;

/**
 * Parsed form of a Conan HTTP API path such as
 * {@code /v2/conans/zlib/1.2.13/_/_/revisions/0a1b/files/conanfile.py}.
 *
 * <p>The parser is deliberately permissive: it accepts both the {@code v1/} and the {@code v2/} dialects, the
 * {@code _} placeholders written by Conan clients and references where the user and the channel are omitted
 * entirely (in which case both default to {@code _}). Paths that belong to the Conan API but are not implemented
 * yield {@link Kind#UNKNOWN}, which the content handler turns into a 404.</p>
 *
 * @since 3.89
 */
public final class ConanApiPath
{
  /**
   * The endpoint a request path maps to.
   */
  public enum Kind
  {
    /**
     * {@code GET ping}, {@code GET v1/ping} or {@code GET v2/ping}.
     */
    PING,

    /**
     * {@code GET v1/conans/search?q=...} (also served for the {@code v2} prefix).
     */
    SEARCH,

    /**
     * {@code GET .../revisions} - recipe revision list of a reference.
     */
    RECIPE_REVISIONS,

    /**
     * {@code GET .../revisions/{rrev}/files} - file list of a recipe revision.
     */
    RECIPE_FILES,

    /**
     * {@code GET|PUT .../revisions/{rrev}/files/{filename}} - one recipe file.
     */
    RECIPE_FILE,

    /**
     * {@code GET .../revisions/{rrev}/packages/{pkgid}/revisions} - package revision list.
     */
    PACKAGE_REVISIONS,

    /**
     * {@code GET .../revisions/{rrev}/packages/{pkgid}/revisions/{prev}/files} - package file list.
     */
    PACKAGE_FILES,

    /**
     * {@code GET|PUT .../packages/{pkgid}/revisions/{prev}/files/{filename}} - one package binary file.
     */
    PACKAGE_FILE,

    /**
     * {@code GET v1/conans/{name}/{version}/{user}/{channel}/download_urls}.
     */
    V1_DOWNLOAD_URLS,

    /**
     * {@code GET v1/conans/{name}/{version}/{user}/{channel}/packages/{pkgid}/download_urls}.
     */
    V1_PACKAGE_DOWNLOAD_URLS,

    /**
     * A path inside the Conan namespace that this repository does not serve.
     */
    UNKNOWN
  }

  /**
   * Segments that terminate the reference part of a path.
   */
  private static final String[] KEYWORDS = {"revisions", "packages", "files", "download_urls"};

  private final Kind kind;

  private final ConanCoordinates coordinates;

  private final String revision;

  private final String packageId;

  private final String packageRevision;

  private final String filename;

  private ConanApiPath(
      final Kind kind,
      final ConanCoordinates coordinates,
      final String revision,
      final String packageId,
      final String packageRevision,
      final String filename)
  {
    this.kind = kind;
    this.coordinates = coordinates;
    this.revision = revision;
    this.packageId = packageId;
    this.packageRevision = packageRevision;
    this.filename = filename;
  }

  /**
   * Parses a raw request path; never returns {@code null}, unparseable paths map to {@link Kind#UNKNOWN}.
   */
  public static ConanApiPath parse(final String rawPath) {
    String normalized = ConanCoordinates.normalize(rawPath);
    String[] segments = normalized.isEmpty() ? new String[0] : normalized.split("/");

    int index = 0;
    if (index < segments.length && ("v1".equals(segments[index]) || "v2".equals(segments[index]))) {
      index++;
    }

    if (index < segments.length && "ping".equals(segments[index]) && index == segments.length - 1) {
      return new ConanApiPath(Kind.PING, null, null, null, null, null);
    }

    if (index >= segments.length || !"conans".equals(segments[index])) {
      return unknown();
    }
    index++;

    // v1 style search, without any reference
    if (index < segments.length && "search".equals(segments[index]) && index == segments.length - 1) {
      return new ConanApiPath(Kind.SEARCH, null, null, null, null, null);
    }

    if (segments.length - index < 2) {
      return unknown();
    }
    String name = segments[index++];
    String version = segments[index++];

    int suffix = index;
    while (suffix < segments.length && !isKeyword(segments[suffix])) {
      suffix++;
    }

    int referenceSegments = suffix - index;
    if (referenceSegments > 2) {
      return unknown();
    }
    String user = referenceSegments >= 1 ? segments[index] : ConanCoordinates.DEFAULT_USER;
    String channel = referenceSegments == 2 ? segments[index + 1] : ConanCoordinates.DEFAULT_CHANNEL;
    ConanCoordinates coordinates = ConanCoordinates.of(name, version, user, channel);

    String[] rest = Arrays.copyOfRange(segments, suffix, segments.length);
    return dispatch(coordinates, rest);
  }

  private static ConanApiPath dispatch(final ConanCoordinates coordinates, final String[] rest) {
    if (rest.length == 0) {
      return unknown();
    }

    switch (rest[0]) {
      case "search":
        return rest.length == 1
            ? new ConanApiPath(Kind.SEARCH, null, null, null, null, null)
            : unknown();

      case "revisions":
        return revisions(coordinates, rest);

      case "download_urls":
        return rest.length == 1
            ? new ConanApiPath(Kind.V1_DOWNLOAD_URLS, coordinates, null, null, null, null)
            : unknown();

      case "packages":
        if (rest.length == 3 && "download_urls".equals(rest[2])) {
          return new ConanApiPath(Kind.V1_PACKAGE_DOWNLOAD_URLS, coordinates, null, rest[1], null, null);
        }
        return unknown();

      default:
        return unknown();
    }
  }

  private static ConanApiPath revisions(final ConanCoordinates coordinates, final String[] rest) {
    if (rest.length == 1) {
      return new ConanApiPath(Kind.RECIPE_REVISIONS, coordinates, null, null, null, null);
    }
    if (rest.length < 3) {
      return unknown();
    }
    String revision = rest[1];

    if ("files".equals(rest[2])) {
      if (rest.length == 3) {
        return new ConanApiPath(Kind.RECIPE_FILES, coordinates, revision, null, null, null);
      }
      return new ConanApiPath(Kind.RECIPE_FILE, coordinates, revision, null, null, join(rest, 3));
    }

    if ("packages".equals(rest[2])) {
      if (rest.length < 5 || !"revisions".equals(rest[4])) {
        return unknown();
      }
      String packageId = rest[3];
      if (rest.length == 5) {
        return new ConanApiPath(Kind.PACKAGE_REVISIONS, coordinates, revision, packageId, null, null);
      }
      String packageRevision = rest[5];
      if (rest.length >= 7 && "files".equals(rest[6])) {
        if (rest.length == 7) {
          return new ConanApiPath(Kind.PACKAGE_FILES, coordinates, revision, packageId, packageRevision, null);
        }
        return new ConanApiPath(Kind.PACKAGE_FILE, coordinates, revision, packageId, packageRevision, join(rest, 7));
      }
      return unknown();
    }

    return unknown();
  }

  private static boolean isKeyword(final String segment) {
    for (String keyword : KEYWORDS) {
      if (keyword.equals(segment)) {
        return true;
      }
    }
    // "search" only terminates a reference when it is the last segment of a v1 search request
    return "search".equals(segment);
  }

  private static String join(final String[] segments, final int from) {
    return String.join("/", Arrays.copyOfRange(segments, from, segments.length));
  }

  private static ConanApiPath unknown() {
    return new ConanApiPath(Kind.UNKNOWN, null, null, null, null, null);
  }

  public Kind kind() {
    return kind;
  }

  /**
   * The parsed reference; {@code null} for {@link Kind#PING} and {@link Kind#SEARCH}.
   */
  public ConanCoordinates coordinates() {
    return coordinates;
  }

  /**
   * Recipe revision ({@code rrev}) of the request.
   */
  public String revision() {
    return revision;
  }

  public String packageId() {
    return packageId;
  }

  /**
   * Package revision ({@code prev}) of the request.
   */
  public String packageRevision() {
    return packageRevision;
  }

  /**
   * File name of a file endpoint, may contain slashes; {@code null} for list endpoints.
   */
  public String filename() {
    return filename;
  }

  /**
   * Storage prefix listing the content addressed by this path: the revision list of a reference, the package
   * revision list of a recipe revision or the file list of a revision. Only valid for list endpoints.
   */
  public String contentPrefix() {
    switch (kind) {
      case RECIPE_REVISIONS:
        return coordinates.revisionsPrefix();
      case RECIPE_FILES:
        return coordinates.recipeRevisionPrefix(revision);
      case PACKAGE_REVISIONS:
        return coordinates.packageRevisionPrefix(revision, packageId);
      case PACKAGE_FILES:
        return coordinates.packageFilesPrefix(revision, packageId, packageRevision);
      default:
        return null;
    }
  }

  /**
   * Storage path of the file addressed by a file endpoint; {@code null} for other endpoints.
   */
  public String filePath() {
    switch (kind) {
      case RECIPE_FILE:
        return coordinates.recipeFilePath(revision, filename);
      case PACKAGE_FILE:
        return coordinates.packageFilePath(revision, packageId, packageRevision, filename);
      default:
        return null;
    }
  }

  /**
   * Whether this path addresses a single file that can be uploaded with PUT.
   */
  public boolean isUploadable() {
    return kind == Kind.RECIPE_FILE || kind == Kind.PACKAGE_FILE;
  }

  @Override
  public boolean equals(final Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof ConanApiPath)) {
      return false;
    }
    ConanApiPath that = (ConanApiPath) other;
    return kind == that.kind
        && Objects.equals(coordinates, that.coordinates)
        && Objects.equals(revision, that.revision)
        && Objects.equals(packageId, that.packageId)
        && Objects.equals(packageRevision, that.packageRevision)
        && Objects.equals(filename, that.filename);
  }

  @Override
  public int hashCode() {
    return Objects.hash(kind, coordinates, revision, packageId, packageRevision, filename);
  }

  @Override
  public String toString() {
    return kind + (coordinates != null ? "(" + coordinates.reference() + ")" : "");
  }
}
