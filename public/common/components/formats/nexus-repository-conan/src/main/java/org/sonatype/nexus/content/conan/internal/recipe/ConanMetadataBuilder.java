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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Builds the JSON documents served by the Conan v1/v2 HTTP API: revision listings, {@code files} listings,
 * {@code download_urls} listings and v1 search results.
 *
 * <p>Every method is static and side effect free so the documents can be produced and merged by unit tests without
 * any Nexus runtime.</p>
 */
public final class ConanMetadataBuilder
{
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

  private static final OffsetDateTime EPOCH = OffsetDateTime.parse("1970-01-01T00:00:00+00:00");

  private static final String CONANFILE = "conanfile";

  private static final String FILES = "files";

  private static final String URL = "url";

  private static final String LAST_MODIFIED = "lastModified";

  private static final String REVISIONS = "revisions";

  private static final String REFERENCE = "reference";

  private static final String REVISION = "revision";

  private static final String TIME = "time";

  private static final String INFO = "info";

  private static final String CONTENT_URL = "content_url";

  private static final String RESULTS = "results";

  private ConanMetadataBuilder() {
    // no instances
  }

  //
  // document builders
  //

  /**
   * Assembles a revision listing document.
   *
   * @param reference reference of the listing, e.g. {@code zlib/1.2.13@_/_}
   * @param revisions revision id to modification time, sorted newest first
   * @param contentUrlPrefix when not {@code null}, every entry carries a {@code content_url} built as
   * {@code contentUrlPrefix + "/" + revision}
   */
  public static String revisionsJson(
      final String reference,
      final Map<String, OffsetDateTime> revisions,
      final String contentUrlPrefix)
  {
    Map<String, String> contentUrls = null;
    if (contentUrlPrefix != null) {
      contentUrls = new LinkedHashMap<>();
      for (final String revision : revisions.keySet()) {
        contentUrls.put(revision, trimTrailingSlash(contentUrlPrefix) + "/" + revision);
      }
    }
    return renderRevisions(reference, revisions, contentUrls);
  }

  /**
   * Builds a {@code files} document; file names are exposed with the leading {@code /} required by the Conan
   * protocol.
   *
   * @param reference reference the files belong to
   * @param revision revision owning the files
   * @param files plain file name to (size, md5) attributes
   */
  public static String filesJson(
      final String reference,
      final String revision,
      final Map<String, Map<String, Object>> files)
  {
    Map<String, Object> inner = new LinkedHashMap<>();
    inner.put(REFERENCE, reference);
    inner.put(REVISION, revision);
    inner.put(FILES, prefixed(files));

    Map<String, Object> root = new LinkedHashMap<>();
    root.put(CONANFILE, inner);
    return json(root);
  }

  /**
   * Builds a {@code download_urls} document; file names are exposed with the leading {@code /} required by the
   * Conan protocol and the entries are sorted by descending modification time.
   *
   * @param files plain file name to (lastModified, url) attributes
   */
  public static String downloadUrlsJson(final Map<String, Map<String, Object>> files) {
    List<String> names = new ArrayList<>(files.keySet());
    names.sort((left, right) -> compareTime(files.get(right).get(LAST_MODIFIED),
        files.get(left).get(LAST_MODIFIED)));

    Map<String, Object> inner = new LinkedHashMap<>();
    Map<String, Object> entries = new LinkedHashMap<>();
    for (final String name : names) {
      Map<String, Object> value = new LinkedHashMap<>(files.get(name));
      value.remove(LAST_MODIFIED);
      entries.put("/" + stripLeadingSlash(name), value);
    }
    inner.put(FILES, entries);

    Map<String, Object> root = new LinkedHashMap<>();
    root.put(CONANFILE, inner);
    return json(root);
  }

  /**
   * Builds the v1 search document, references sorted alphabetically.
   */
  public static String searchResultsJson(final Iterable<String> references) {
    SortedSet<String> sorted = new TreeSet<>();
    for (final String reference : references) {
      sorted.add(reference);
    }

    Map<String, Object> root = new LinkedHashMap<>();
    root.put(RESULTS, new ArrayList<>(sorted));
    return json(root);
  }

  //
  // merging
  //

  /**
   * Unions two revision maps (id to modification time), keeping the newest time per revision id, sorted newest
   * first.
   */
  public static Map<String, OffsetDateTime> mergeRevisionLists(
      final Map<String, OffsetDateTime> left,
      final Map<String, OffsetDateTime> right)
  {
    Map<String, OffsetDateTime> merged = new LinkedHashMap<>(left);
    for (final Map.Entry<String, OffsetDateTime> entry : right.entrySet()) {
      OffsetDateTime current = merged.get(entry.getKey());
      if (current == null || isNewer(entry.getValue(), current)) {
        merged.put(entry.getKey(), entry.getValue());
      }
    }
    return sortNewestFirst(merged);
  }

  /**
   * Unions two v1 search result lists, preserving order and dropping duplicates.
   */
  public static List<String> mergeSearchResults(final List<String> left, final List<String> right) {
    LinkedHashSet<String> merged = new LinkedHashSet<>(left);
    merged.addAll(right);
    return new ArrayList<>(merged);
  }

  /**
   * Merges the revision documents returned by every member of a group.
   *
   * @param bodiesByMemberUrl member repository base URL to revision document
   * @param targetBaseUrl base URL of the group repository, used to rewrite {@code content_url} entries
   * @return a single revision document, or an empty document when no member returned any
   */
  public static String mergeRevisionBodies(final Map<String, String> bodiesByMemberUrl, final String targetBaseUrl) {
    String target = trimTrailingSlash(targetBaseUrl);
    String reference = null;
    Map<String, OffsetDateTime> revisions = new LinkedHashMap<>();
    Map<String, String> contentUrls = new LinkedHashMap<>();

    for (final Map.Entry<String, String> entry : bodiesByMemberUrl.entrySet()) {
      String source = trimTrailingSlash(entry.getKey());
      for (final RevisionInfo info : parseRevisionInfos(entry.getValue())) {
        if (info.reference != null) {
          reference = info.reference;
        }
        OffsetDateTime current = revisions.get(info.revision);
        if (current == null || isNewer(info.time, current)) {
          revisions.put(info.revision, info.time);
        }
        if (info.contentUrl != null) {
          contentUrls.put(info.revision, rewritePrefix(info.contentUrl, source, target));
        }
      }
    }

    return renderRevisions(reference, revisions, contentUrls.isEmpty() ? null : contentUrls);
  }

  /**
   * Merges the search documents returned by every member of a group.
   */
  public static String mergeSearchBodies(final List<String> bodies) {
    LinkedHashSet<String> results = new LinkedHashSet<>();
    for (final String body : bodies) {
      results.addAll(parseSearchResults(body));
    }
    Map<String, Object> root = new LinkedHashMap<>();
    root.put(RESULTS, new ArrayList<>(results));
    return json(root);
  }

  //
  // parsing helpers
  //

  /**
   * Extracts the {@code revision} of every entry of a revision document, with its modification time.
   */
  public static Map<String, OffsetDateTime> parseRevisions(final String body) {
    Map<String, OffsetDateTime> revisions = new LinkedHashMap<>();
    for (final RevisionInfo info : parseRevisionInfos(body)) {
      revisions.put(info.revision, info.time);
    }
    return revisions;
  }

  /**
   * Extracts the references of a v1 search document.
   */
  public static List<String> parseSearchResults(final String body) {
    List<String> results = new ArrayList<>();
    JsonNode root = parse(body);
    if (root == null) {
      return results;
    }
    JsonNode values = root.path(RESULTS);
    if (!values.isArray()) {
      return results;
    }
    values.forEach(value -> {
      if (value.isTextual()) {
        results.add(value.asText());
      }
    });
    return results;
  }

  private static List<RevisionInfo> parseRevisionInfos(final String body) {
    List<RevisionInfo> infos = new ArrayList<>();
    JsonNode root = parse(body);
    if (root == null) {
      return infos;
    }
    JsonNode values = root.path(REVISIONS);
    if (!values.isArray()) {
      return infos;
    }
    values.forEach(value -> {
      JsonNode info = value.path(INFO);
      RevisionInfo parsed = new RevisionInfo();
      parsed.reference = text(info, REFERENCE);
      parsed.revision = text(info, REVISION);
      parsed.contentUrl = value.path(CONTENT_URL).isTextual() ? value.path(CONTENT_URL).asText() : null;
      String time = text(info, TIME);
      if (parsed.revision == null || parsed.revision.isEmpty()) {
        return;
      }
      parsed.time = parseTime(time);
      infos.add(parsed);
    });
    return infos;
  }

  private static OffsetDateTime parseTime(final String time) {
    if (time == null || time.isEmpty()) {
      return null;
    }
    try {
      return OffsetDateTime.parse(time);
    }
    catch (final Exception e) {
      try {
        return OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(Long.parseLong(time)),
            java.time.ZoneOffset.UTC);
      }
      catch (final Exception ignored) {
        return null;
      }
    }
  }

  private static String text(final JsonNode node, final String field) {
    JsonNode value = node.path(field);
    return value.isTextual() ? value.asText() : null;
  }

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

  //
  // internals
  //

  private static String renderRevisions(
      final String reference,
      final Map<String, OffsetDateTime> revisions,
      final Map<String, String> contentUrls)
  {
    List<Map.Entry<String, OffsetDateTime>> ordered = new ArrayList<>(sortNewestFirstMap(revisions).entrySet());

    List<Map<String, Object>> results = new ArrayList<>(ordered.size());
    for (final Map.Entry<String, OffsetDateTime> entry : ordered) {
      Map<String, Object> info = new LinkedHashMap<>();
      if (reference != null) {
        info.put(REFERENCE, reference);
      }
      info.put(REVISION, entry.getKey());
      info.put(TIME, formatTime(entry.getValue()));

      Map<String, Object> item = new LinkedHashMap<>();
      item.put(INFO, info);
      if (contentUrls != null && contentUrls.containsKey(entry.getKey())) {
        item.put(CONTENT_URL, contentUrls.get(entry.getKey()));
      }
      results.add(item);
    }

    Map<String, Object> root = new LinkedHashMap<>();
    root.put(REVISIONS, results);
    return json(root);
  }

  private static Map<String, OffsetDateTime> sortNewestFirst(final Map<String, OffsetDateTime> revisions) {
    return sortNewestFirstMap(revisions);
  }

  private static Map<String, OffsetDateTime> sortNewestFirstMap(final Map<String, OffsetDateTime> revisions) {
    List<Map.Entry<String, OffsetDateTime>> ordered = new ArrayList<>(revisions.entrySet());
    ordered.sort((left, right) -> compareTime(right.getValue(), left.getValue()));
    Map<String, OffsetDateTime> sorted = new LinkedHashMap<>();
    for (final Map.Entry<String, OffsetDateTime> entry : ordered) {
      sorted.put(entry.getKey(), entry.getValue());
    }
    return sorted;
  }

  private static Map<String, Map<String, Object>> prefixed(final Map<String, Map<String, Object>> files) {
    Map<String, Map<String, Object>> prefixed = new LinkedHashMap<>();
    for (final Map.Entry<String, Map<String, Object>> entry : files.entrySet()) {
      prefixed.put("/" + stripLeadingSlash(entry.getKey()), entry.getValue());
    }
    return prefixed;
  }

  private static int compareTime(final Object left, final Object right) {
    if (left == right) {
      return 0;
    }
    // nulls sort last, so a missing modification time is treated as the oldest entry
    if (left == null) {
      return 1;
    }
    if (right == null) {
      return -1;
    }
    return Long.compare(timeOf(left), timeOf(right));
  }

  private static long timeOf(final Object value) {
    if (value instanceof OffsetDateTime) {
      return ((OffsetDateTime) value).toInstant().toEpochMilli();
    }
    if (value instanceof Number) {
      return ((Number) value).longValue();
    }
    OffsetDateTime parsed = parseTime(String.valueOf(value));
    return parsed == null ? 0L : parsed.toInstant().toEpochMilli();
  }

  private static boolean isNewer(final OffsetDateTime candidate, final OffsetDateTime current) {
    if (candidate == null) {
      return false;
    }
    if (current == null) {
      return true;
    }
    return candidate.isAfter(current);
  }

  private static String formatTime(final OffsetDateTime time) {
    return ISO.format(time == null ? EPOCH : time);
  }

  private static String rewritePrefix(final String value, final String from, final String to) {
    if (from == null || from.isEmpty() || !value.startsWith(from)) {
      return value;
    }
    return to + value.substring(from.length());
  }

  private static String trimTrailingSlash(final String value) {
    if (value == null) {
      return "";
    }
    String result = value;
    while (result.endsWith("/")) {
      result = result.substring(0, result.length() - 1);
    }
    return result;
  }

  private static String stripLeadingSlash(final String value) {
    String result = value;
    while (result.startsWith("/")) {
      result = result.substring(1);
    }
    return result;
  }

  private static String json(final Object value) {
    try {
      return MAPPER.writeValueAsString(value);
    }
    catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * One entry of a revision document.
   */
  private static class RevisionInfo
  {
    String reference;

    String revision;

    OffsetDateTime time;

    String contentUrl;
  }
}
