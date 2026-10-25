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
package org.sonatype.nexus.content.conan.internal.recipe;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Rewrites the {@code download_urls} documents returned by a Conan remote (or group) so the files are fetched
 * through Nexus instead of straight from the upstream host.
 *
 * <p>All methods are static and side effect free so they can be exercised by unit tests.</p>
 */
public final class ConanDownloadUrlsRewriter
{
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String CONANFILE = "conanfile";

  private static final String FILES = "files";

  private static final String URL = "url";

  private static final String LAST_MODIFIED = "lastModified";

  private ConanDownloadUrlsRewriter() {
    // no instances
  }

  /**
   * Rewrites a {@code download_urls} document replacing every remote root prefix with {@code localRoot}.
   * Falls back to swapping the origin (scheme, host and port) when the remote root cannot be stripped.
   *
   * @param body the original JSON document
   * @param remoteRoot absolute base URL of the remote repository, e.g. {@code https://center.conan.io}
   * @param localRoot absolute base URL of this repository, e.g. {@code http://localhost:8081/repository/conan-proxy}
   */
  public static String rewriteFromRemote(final String body, final String remoteRoot, final String localRoot) {
    if (body == null || remoteRoot == null || localRoot == null) {
      return body;
    }
    String remote = trimTrailingSlash(remoteRoot);
    String local = trimTrailingSlash(localRoot);

    Map<String, String> urls = extractFileUrls(body);
    if (urls.isEmpty()) {
      return body;
    }

    String result = body;
    for (final Map.Entry<String, String> entry : urls.entrySet()) {
      String value = entry.getValue();
      if (value == null) {
        continue;
      }
      if (value.startsWith(remote)) {
        result = replaceFirst(result, value, local + value.substring(remote.length()));
      }
      else {
        String swapped = swapOrigin(value, remote, local);
        if (swapped != null && !swapped.equals(value)) {
          result = replaceFirst(result, value, swapped);
        }
      }
    }
    return result;
  }

  /**
   * Rewrites a {@code download_urls} document by replacing any occurrence of {@code prefixFrom} with
   * {@code prefixTo}. Used to move member repository URLs to the group URL.
   */
  public static String rewritePrefix(final String body, final String prefixFrom, final String prefixTo) {
    if (body == null || prefixFrom == null || prefixTo == null || prefixFrom.isEmpty()) {
      return body;
    }
    String from = trimTrailingSlash(prefixFrom);
    String to = trimTrailingSlash(prefixTo);
    if (from.equals(to)) {
      return body;
    }

    Map<String, String> urls = extractFileUrls(body);
    String result = body;
    for (final Map.Entry<String, String> entry : urls.entrySet()) {
      String value = entry.getValue();
      if (value != null && value.startsWith(from)) {
        result = replaceFirst(result, value, to + value.substring(from.length()));
      }
    }
    return result;
  }

  /**
   * Extracts the {@code url} member of every file of a {@code download_urls} document, keyed by the (leading
   * slash prefixed) file name, so the URLs can be replaced verbatim in the original body.
   */
  public static Map<String, String> extractFileUrls(final String body) {
    Map<String, String> urls = new LinkedHashMap<>();
    JsonNode root = parse(body);
    if (root == null) {
      return urls;
    }
    JsonNode files = root.path(CONANFILE).path(FILES);
    if (!files.isObject()) {
      return urls;
    }
    files.fields().forEachRemaining(entry -> {
      JsonNode url = entry.getValue().path(URL);
      if (url.isTextual()) {
        urls.put(entry.getKey(), url.asText());
      }
    });
    return urls;
  }

  /**
   * Parses a {@code download_urls} document into a flat file name to (lastModified, url) map.
   */
  public static Map<String, Map<String, Object>> extractFiles(final String body) {
    Map<String, Map<String, Object>> files = new LinkedHashMap<>();
    JsonNode root = parse(body);
    if (root == null) {
      return files;
    }
    JsonNode container = root.path(CONANFILE).path(FILES);
    if (!container.isObject()) {
      return files;
    }
    container.fields().forEachRemaining(entry -> {
      Map<String, Object> value = new LinkedHashMap<>();
      JsonNode url = entry.getValue().path(URL);
      JsonNode lastModified = entry.getValue().path(LAST_MODIFIED);
      if (url.isTextual()) {
        value.put(URL, url.asText());
      }
      if (lastModified.isNumber()) {
        value.put(LAST_MODIFIED, lastModified.asLong());
      }
      else if (lastModified.isTextual()) {
        value.put(LAST_MODIFIED, lastModified.asText());
      }
      files.put(entry.getKey(), value);
    });
    return files;
  }

  // ----------------------------------------------------------------- helpers

  private static JsonNode parse(final String body) {
    if (body == null || body.isEmpty()) {
      return null;
    }
    try {
      return MAPPER.readTree(body);
    }
    catch (final Exception e) {
      return null;
    }
  }

  private static String swapOrigin(final String url, final String remoteRoot, final String localRoot) {
    try {
      URI remote = URI.create(trimTrailingSlash(remoteRoot));
      URI value = URI.create(url);
      if (remote.getHost() == null || value.getHost() == null) {
        return null;
      }
      URI local = URI.create(trimTrailingSlash(localRoot));
      String path = trimTrailingSlash(local.getPath() == null ? "" : local.getPath()) + value.getPath();
      URI rewritten = new URI(local.getScheme(), local.getUserInfo(), local.getHost(), local.getPort(),
          path, value.getQuery(), value.getFragment());
      return rewritten.toString();
    }
    catch (final Exception e) {
      return null;
    }
  }

  private static String replaceFirst(final String body, final String target, final String replacement) {
    int index = body.indexOf(quote(target));
    if (index < 0) {
      return body;
    }
    int start = index + 1;
    return body.substring(0, start) + escape(replacement) + body.substring(start + target.length());
  }

  private static String quote(final String value) {
    return "\"" + value + "\"";
  }

  private static String escape(final String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private static String trimTrailingSlash(final String value) {
    String result = value;
    while (result.endsWith("/")) {
      result = result.substring(0, result.length() - 1);
    }
    return result;
  }
}
