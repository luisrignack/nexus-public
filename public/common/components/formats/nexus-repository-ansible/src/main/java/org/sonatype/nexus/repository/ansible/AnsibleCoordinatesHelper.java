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
package org.sonatype.nexus.repository.ansible;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the Galaxy / Automation Hub URL space served by an Ansible repository and derives the
 * canonical storage path of a collection tarball.
 *
 * <p>Endpoints understood by this format (all relative to the repository base URL):</p>
 * <pre>
 *   /api/v3/                                                                          API root
 *   /api/v3/plugin/ansible/content/published/collections/index/                       all collections
 *   /api/v3/plugin/ansible/content/published/collections/index/{ns}/                  one namespace
 *   /api/v3/plugin/ansible/content/published/collections/index/{ns}/{name}/           one collection
 *   /api/v3/plugin/ansible/content/published/collections/index/{ns}/{name}/versions/  its versions
 *   /api/v3/plugin/ansible/content/published/collections/index/{ns}/{name}/versions/{v}/
 *   /api/v3/collections/{ns}/{name}/{v}/download/                                     tarball download
 *   /api/v2/collections/…                                                             Galaxy v2 compatibility
 *   /collections/{ns}/{name}/{v}/{ns}.{name}-{v}.tar.gz                               stored artifact
 * </pre>
 */
public final class AnsibleCoordinatesHelper
{
  public static final String API_V3 = "/api/v3";

  public static final String API_V2 = "/api/v2";

  public static final String V3_INDEX = "/api/v3/plugin/ansible/content/published/collections/index";

  public static final String V3_DOWNLOAD = "/api/v3/collections";

  public static final String V2_COLLECTIONS = "/api/v2/collections";

  /**
   * Directory holding the tarballs of every collection in this repository.
   */
  public static final String CONTENT_DIR = "/collections";

  private static final Pattern INDEX_ALL =
      Pattern.compile("^/api/v3/plugin/ansible/content/published/collections/index/?$");

  private static final Pattern INDEX_NAMESPACE =
      Pattern.compile("^/api/v3/plugin/ansible/content/published/collections/index/([^/]+)/?$");

  private static final Pattern INDEX_COLLECTION =
      Pattern.compile("^/api/v3/plugin/ansible/content/published/collections/index/([^/]+)/([^/]+)/?$");

  private static final Pattern INDEX_VERSIONS =
      Pattern.compile("^/api/v3/plugin/ansible/content/published/collections/index/([^/]+)/([^/]+)/versions/?$");

  private static final Pattern INDEX_VERSION =
      Pattern.compile(
          "^/api/v3/plugin/ansible/content/published/collections/index/([^/]+)/([^/]+)/versions/([^/]+)/?$");

  private static final Pattern DOWNLOAD =
      Pattern.compile("^/api/v3/collections/([^/]+)/([^/]+)/([^/]+)/download/?$");

  private static final Pattern V2_COLLECTION =
      Pattern.compile("^/api/v2/collections/([^/]+)/([^/]+)/?$");

  private static final Pattern V2_VERSIONS =
      Pattern.compile("^/api/v2/collections/([^/]+)/([^/]+)/versions/?$");

  private static final Pattern V2_VERSION =
      Pattern.compile("^/api/v2/collections/([^/]+)/([^/]+)/versions/([^/]+)/?$");

  /**
   * Kind of request encoded in a repository path.
   */
  public enum Kind
  {
    /** Repository root or API root. */
    ROOT,
    /** Paged list of every collection. */
    INDEX_ALL,
    /** List of the collections of one namespace. */
    INDEX_NAMESPACE,
    /** Detail of one collection. */
    INDEX_COLLECTION,
    /** Versions of one collection. */
    INDEX_VERSIONS,
    /** Detail of one version. */
    INDEX_VERSION,
    /** Tarball download. */
    DOWNLOAD,
    /** Not a Galaxy endpoint; served from the blob store as a plain asset. */
    ASSET,
    /** Galaxy-shaped path that cannot be served. */
    UNKNOWN
  }

  /**
   * A parsed Galaxy request.
   */
  public static final class GalaxyRequest
  {
    private final Kind kind;

    private final String namespace;

    private final String name;

    private final String version;

    private final String path;

    private GalaxyRequest(final Kind kind, final String namespace, final String name, final String version,
                          final String path)
    {
      this.kind = kind;
      this.namespace = namespace;
      this.name = name;
      this.version = version;
      this.path = path;
    }

    static GalaxyRequest of(final Kind kind, final String path) {
      return new GalaxyRequest(kind, null, null, null, path);
    }

    static GalaxyRequest of(final Kind kind, final String namespace, final String path) {
      return new GalaxyRequest(kind, namespace, null, null, path);
    }

    static GalaxyRequest of(final Kind kind, final String namespace, final String name, final String path) {
      return new GalaxyRequest(kind, namespace, name, null, path);
    }

    static GalaxyRequest of(final Kind kind, final String namespace, final String name, final String version,
                            final String path)
    {
      return new GalaxyRequest(kind, namespace, name, version, path);
    }

    public Kind getKind() {
      return kind;
    }

    public String getNamespace() {
      return namespace;
    }

    public String getName() {
      return name;
    }

    public String getVersion() {
      return version;
    }

    public String getPath() {
      return path;
    }

    public boolean isApi() {
      return kind != Kind.ASSET;
    }

    /**
     * {@code true} when the request addresses a JSON document of the Galaxy API. Tarball
     * downloads and plain assets are excluded: their bytes must never be rewritten as text.
     */
    public boolean isDocument() {
      return kind != Kind.ASSET && kind != Kind.DOWNLOAD;
    }
  }

  private AnsibleCoordinatesHelper() {
  }

  /**
   * Classifies a decoded repository path (leading slash optional).
   */
  public static GalaxyRequest parse(final String rawPath) {
    String path = normalize(rawPath);
    if (path.isEmpty() || "/".equals(path) || (API_V3 + "/").equals(path) || API_V3.equals(path)) {
      return GalaxyRequest.of(Kind.ROOT, path);
    }
    if (API_V2.equals(path) || (API_V2 + "/").equals(path)) {
      return GalaxyRequest.of(Kind.ROOT, path);
    }

    Matcher matcher;
    if ((matcher = INDEX_VERSION.matcher(path)).matches()) {
      return GalaxyRequest.of(Kind.INDEX_VERSION, group(matcher, 1), group(matcher, 2), group(matcher, 3), path);
    }
    if ((matcher = INDEX_VERSIONS.matcher(path)).matches()) {
      return GalaxyRequest.of(Kind.INDEX_VERSIONS, group(matcher, 1), group(matcher, 2), path);
    }
    if ((matcher = INDEX_COLLECTION.matcher(path)).matches()) {
      return GalaxyRequest.of(Kind.INDEX_COLLECTION, group(matcher, 1), group(matcher, 2), path);
    }
    if ((matcher = INDEX_NAMESPACE.matcher(path)).matches()) {
      return GalaxyRequest.of(Kind.INDEX_NAMESPACE, group(matcher, 1), path);
    }
    if (INDEX_ALL.matcher(path).matches()) {
      return GalaxyRequest.of(Kind.INDEX_ALL, path);
    }
    if ((matcher = DOWNLOAD.matcher(path)).matches()) {
      return GalaxyRequest.of(Kind.DOWNLOAD, group(matcher, 1), group(matcher, 2), group(matcher, 3), path);
    }
    if ((matcher = V2_VERSION.matcher(path)).matches()) {
      return GalaxyRequest.of(Kind.INDEX_VERSION, group(matcher, 1), group(matcher, 2), group(matcher, 3), path);
    }
    if ((matcher = V2_VERSIONS.matcher(path)).matches()) {
      return GalaxyRequest.of(Kind.INDEX_VERSIONS, group(matcher, 1), group(matcher, 2), path);
    }
    if ((matcher = V2_COLLECTION.matcher(path)).matches()) {
      return GalaxyRequest.of(Kind.INDEX_COLLECTION, group(matcher, 1), group(matcher, 2), path);
    }
    if (path.startsWith(API_V2) || path.startsWith(API_V3)) {
      return GalaxyRequest.of(Kind.UNKNOWN, path);
    }
    if (path.startsWith(CONTENT_DIR + "/") || path.endsWith(".tar.gz")) {
      return GalaxyRequest.of(Kind.ASSET, path);
    }
    return GalaxyRequest.of(Kind.ASSET, path);
  }

  /**
   * Canonical storage path of a collection tarball.
   */
  public static String canonicalPath(final String namespace, final String name, final String version) {
    return CONTENT_DIR + "/" + namespace + "/" + name + "/" + version + "/" + fileName(namespace, name, version);
  }

  /**
   * File name used for the tarball of a collection.
   */
  public static String fileName(final String namespace, final String name, final String version) {
    return namespace + "." + name + "-" + version + ".tar.gz";
  }

  /**
   * Returns the coordinates encoded in a stored path, or {@code null} when the path is not a collection.
   */
  public static String[] pathCoordinates(final String path) {
    String normalized = normalize(path);
    if (!normalized.startsWith(CONTENT_DIR + "/")) {
      return null;
    }
    String[] segments = normalized.substring((CONTENT_DIR + "/").length()).split("/");
    if (segments.length < 3) {
      return null;
    }
    return new String[]{segments[0], segments[1], segments[2]};
  }

  /**
   * {@code true} when the path addresses the Galaxy API rather than a stored asset.
   */
  public static boolean isApiPath(final String path) {
    return parse(path).isApi();
  }

  public static String normalize(final String path) {
    String value = stripLeadingSlash(path);
    if (value == null || value.isEmpty()) {
      return "/";
    }
    value = stripTrailingSlash("/" + value);
    return value.isEmpty() ? "/" : value;
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
   * File name of the last segment of a path or URL, without query or fragment.
   */
  public static String fileName(final String path) {
    if (path == null) {
      return "";
    }
    String value = path;
    int fragment = value.indexOf('#');
    if (fragment >= 0) {
      value = value.substring(0, fragment);
    }
    int query = value.indexOf('?');
    if (query >= 0) {
      value = value.substring(0, query);
    }
    value = stripTrailingSlash(value);
    int slash = value.lastIndexOf('/');
    return slash >= 0 ? value.substring(slash + 1) : value;
  }

  /**
   * URL-decodes a path segment, tolerating malformed input.
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

  private static String group(final Matcher matcher, final int index) {
    return decode(matcher.group(index));
  }
}
