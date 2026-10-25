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
package org.sonatype.nexus.repository.cargo;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import javax.annotation.Nullable;

import org.sonatype.nexus.repository.view.Parameters;

/**
 * Path and coordinate helpers for the cargo (Rust crates) format.
 *
 * <p>Repository relative paths handled by the format:</p>
 * <ul>
 *   <li>{@code index/config.json} - sparse registry configuration</li>
 *   <li>{@code index/{prefix}/{crate}} - newline delimited JSON index of a crate</li>
 *   <li>{@code crates/{crate}/{crate}-{version}.crate} - uploaded crate archive</li>
 *   <li>{@code api/v1/crates} - crate search</li>
 *   <li>{@code api/v1/crates/{crate}} - crate detail ({@code cargo info})</li>
 *   <li>{@code api/v1/crates/{crate}/{version}/download} - crate download</li>
 *   <li>{@code api/v1/crates/{crate}/{version}} and {@code .../upload} - publish (PUT)</li>
 * </ul>
 *
 * @since 3.89
 */
public final class CargoCoordinatesHelper
{
  public static final String INDEX_DIR = "index";

  public static final String CONFIG_PATH = "index/config.json";

  public static final String CRATES_DIR = "crates";

  public static final String API_CRATES_PATH = "api/v1/crates";

  public static final String API_ROOT = "api";

  public static final String DOWNLOAD_SUFFIX = "/download";

  public static final String UPLOAD_SUFFIX = "/upload";

  private CargoCoordinatesHelper() {
    // no instances
  }

  /**
   * Normalizes any (possibly encoded, possibly leading slashed) view path to a repository relative path without a
   * leading slash. Returns {@code "/"} when there is nothing to normalize.
   */
  public static String normalize(@Nullable final String path) {
    String decoded = decode(path);
    if (decoded == null || decoded.isEmpty()) {
      return "/";
    }
    decoded = stripLeadingSlash(decoded);
    if (decoded.isEmpty()) {
      return "/";
    }
    return decoded;
  }

  @Nullable
  public static String decode(@Nullable final String path) {
    if (path == null) {
      return null;
    }
    try {
      return URLDecoder.decode(path, StandardCharsets.UTF_8.name());
    }
    catch (UnsupportedEncodingException | IllegalArgumentException e) {
      return path;
    }
  }

  public static String stripLeadingSlash(final String path) {
    String result = path;
    while (result.startsWith("/")) {
      result = result.substring(1);
    }
    return result;
  }

  public static String stripTrailingSlash(final String path) {
    String result = path;
    while (result.endsWith("/")) {
      result = result.substring(0, result.length() - 1);
    }
    return result;
  }

  /**
   * Crate names are case insensitive in cargo; storage paths always use the lower case form.
   */
  public static String lower(final String crateName) {
    return crateName.toLowerCase(Locale.ROOT);
  }

  /**
   * Sparse index directory of a crate name, all rules operate on the lower cased name.
   *
   * <ul>
   *   <li>length 1 -&gt; {@code 1/{name}}</li>
   *   <li>length 2 -&gt; {@code 2/{name}}</li>
   *   <li>length 3 -&gt; {@code 3/{first char}}</li>
   *   <li>length &gt;= 4 -&gt; {@code {c0}{c1}/{c2}{c3}}</li>
   * </ul>
   */
  public static String prefix(final String crateName) {
    String name = lower(crateName);
    switch (name.length()) {
      case 0:
        return "";
      case 1:
        return "1/" + name;
      case 2:
        return "2/" + name;
      case 3:
        return "3/" + name.charAt(0);
      default:
        return name.substring(0, 2) + "/" + name.substring(2, 4);
    }
  }

  /**
   * Repository relative path of the sparse index file of the given crate, e.g. {@code index/se/rd/serde}.
   */
  public static String indexPath(final String crateName) {
    String name = lower(crateName);
    if (name.isEmpty()) {
      return INDEX_DIR;
    }
    String prefix = prefix(name);
    // for one and two character names the prefix already contains the crate name
    if (name.length() <= 2) {
      return INDEX_DIR + "/" + prefix;
    }
    return INDEX_DIR + "/" + prefix + "/" + name;
  }

  /**
   * Repository relative path of the stored {@code .crate} archive.
   */
  public static String cratePath(final String crateName, final String version) {
    String name = lower(crateName);
    return CRATES_DIR + "/" + name + "/" + name + "-" + version + ".crate";
  }

  public static boolean isConfigPath(final String path) {
    return CONFIG_PATH.equals(path);
  }

  /**
   * {@code index/{prefix}/{crate}}: at least two segments after the {@code index} directory.
   */
  public static boolean isIndexPath(final String path) {
    if (!path.startsWith(INDEX_DIR + "/")) {
      return false;
    }
    String rest = path.substring(INDEX_DIR.length() + 1);
    String[] segments = rest.split("/");
    return segments.length >= 2 && !segments[0].isEmpty() && !segments[1].isEmpty();
  }

  /**
   * Crate name served by an index path, lower cased by the storage layout.
   */
  public static Optional<String> crateFromIndexPath(final String path) {
    if (!isIndexPath(path)) {
      return Optional.empty();
    }
    String[] segments = path.split("/");
    String crate = segments[segments.length - 1];
    return crate.isEmpty() ? Optional.empty() : Optional.of(crate);
  }

  /**
   * {@code api/v1/crates} with no further segment (optionally trailing slash): crates.io search.
   */
  public static boolean isSearchPath(final String path) {
    return API_CRATES_PATH.equals(path) || (API_CRATES_PATH + "/").equals(path);
  }

  /**
   * Parses {@code api/v1/crates/{crate}} (crate detail used by {@code cargo info}).
   */
  public static Optional<String> crateFromDetailPath(final String path) {
    if (!path.startsWith(API_CRATES_PATH + "/")) {
      return Optional.empty();
    }
    String rest = path.substring(API_CRATES_PATH.length() + 1);
    if (rest.isEmpty() || rest.contains("/")) {
      return Optional.empty();
    }
    return Optional.of(rest);
  }

  /**
   * Parses a download path {@code api/v1/crates/{crate}/{version}/download}.
   */
  public static Optional<CrateCoordinates> parseDownload(final String path) {
    Optional<CrateCoordinates> coordinates = parsePublish(path);
    if (coordinates.isPresent() && path.endsWith(DOWNLOAD_SUFFIX)) {
      return coordinates;
    }
    return Optional.empty();
  }

  /**
   * Parses a publish path: {@code api/v1/crates/{crate}/{version}} or {@code api/v1/crates/{crate}/{version}/upload}.
   */
  public static Optional<CrateCoordinates> parsePublish(final String path) {
    if (!path.startsWith(API_CRATES_PATH + "/")) {
      return Optional.empty();
    }
    String rest = path.substring(API_CRATES_PATH.length() + 1);
    if (rest.endsWith(UPLOAD_SUFFIX)) {
      rest = rest.substring(0, rest.length() - UPLOAD_SUFFIX.length());
    }
    else if (rest.endsWith(DOWNLOAD_SUFFIX)) {
      rest = rest.substring(0, rest.length() - DOWNLOAD_SUFFIX.length());
    }
    String[] segments = rest.split("/");
    if (segments.length != 2 || segments[0].isEmpty() || segments[1].isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new CrateCoordinates(segments[0], segments[1]));
  }

  /**
   * Rebuilds an encoded query string from view parameters, or an empty string when there are none.
   */
  public static String queryString(@Nullable final Parameters parameters) {
    if (parameters == null || parameters.isEmpty()) {
      return "";
    }
    List<String> pairs = new ArrayList<>();
    for (java.util.Map.Entry<String, String> entry : parameters.entries()) {
      pairs.add(percentEncode(entry.getKey()) + "=" + percentEncode(entry.getValue()));
    }
    return String.join("&", pairs);
  }

  private static String percentEncode(final String value) {
    if (value == null) {
      return "";
    }
    try {
      return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }
    catch (UnsupportedEncodingException e) {
      return value;
    }
  }

  /**
   * Crate coordinates parsed out of an api path.
   */
  public static final class CrateCoordinates
  {
    private final String crate;

    private final String version;

    public CrateCoordinates(final String crate, final String version) {
      this.crate = crate;
      this.version = version;
    }

    public String getCrate() {
      return crate;
    }

    public String getVersion() {
      return version;
    }

    @Override
    public String toString() {
      return crate + "/" + version;
    }
  }
}
