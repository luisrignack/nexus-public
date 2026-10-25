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
package org.sonatype.nexus.content.cargo.internal.recipe;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.annotation.Nullable;

import org.sonatype.nexus.repository.cargo.CargoCrateSummary;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

/**
 * Pure helpers that build the payloads served by the cargo format: the sparse index (newline delimited JSON), the
 * {@code index/config.json} document, the crates.io compatible search and crate detail responses.
 *
 * <p>All methods are stateless and free of I/O so they can be unit tested hermetically.</p>
 *
 * @since 3.89
 */
public final class CargoIndexBuilder
{
  public static final int DEFAULT_PER_PAGE = 10;

  public static final int MAX_PER_PAGE = 100;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private CargoIndexBuilder() {
    // no instances
  }

  //
  // responses
  //

  /**
   * Body returned by successful publishes: {@code {"ok":true}}.
   */
  public static String okResponse() {
    return "{\"ok\":true}";
  }

  //
  // semver ordering
  //

  /**
   * Compares two semantic versions (crates.io uses plain semver ordering, ascending).
   *
   * <p>Rules: numeric core comparison, build metadata is ignored, a pre-release sorts before its release and
   * numeric pre-release identifiers sort before alphanumeric ones.</p>
   */
  public static int compareVersions(@Nullable final String left, @Nullable final String right) {
    String[] a = splitPreRelease(normalizeVersion(left));
    String[] b = splitPreRelease(normalizeVersion(right));

    int core = compareCore(a[0], b[0]);
    if (core != 0) {
      return core;
    }
    if (a[1].isEmpty() && b[1].isEmpty()) {
      return 0;
    }
    if (a[1].isEmpty()) {
      return 1; // release sorts after its pre-releases
    }
    if (b[1].isEmpty()) {
      return -1;
    }
    return comparePreRelease(a[1], b[1]);
  }

  private static String normalizeVersion(@Nullable final String version) {
    String value = version == null ? "" : version.trim();
    while (value.startsWith("=") || value.startsWith("v") || value.startsWith("V")) {
      value = value.substring(1).trim();
    }
    int build = value.indexOf('+');
    if (build >= 0) {
      value = value.substring(0, build);
    }
    return value;
  }

  private static String[] splitPreRelease(final String version) {
    int index = version.indexOf('-');
    if (index < 0) {
      return new String[]{version, ""};
    }
    return new String[]{version.substring(0, index), version.substring(index + 1)};
  }

  private static int compareCore(final String left, final String right) {
    String[] a = left.split("\\.");
    String[] b = right.split("\\.");
    int length = Math.max(a.length, b.length);
    for (int i = 0; i < length; i++) {
      BigInteger x = i < a.length ? numeric(a[i]) : BigInteger.ZERO;
      BigInteger y = i < b.length ? numeric(b[i]) : BigInteger.ZERO;
      int cmp = x.compareTo(y);
      if (cmp != 0) {
        return cmp;
      }
    }
    return 0;
  }

  private static int comparePreRelease(final String left, final String right) {
    String[] a = left.split("\\.");
    String[] b = right.split("\\.");
    int length = Math.min(a.length, b.length);
    for (int i = 0; i < length; i++) {
      int cmp = compareIdentifier(a[i], b[i]);
      if (cmp != 0) {
        return cmp;
      }
    }
    return Integer.compare(a.length, b.length);
  }

  private static int compareIdentifier(final String left, final String right) {
    boolean numericLeft = isNumeric(left);
    boolean numericRight = isNumeric(right);
    if (numericLeft && numericRight) {
      return numeric(left).compareTo(numeric(right));
    }
    if (numericLeft) {
      return -1; // numeric identifiers have lower precedence
    }
    if (numericRight) {
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

  private static BigInteger numeric(final String value) {
    String trimmed = value.trim();
    if (!isNumeric(trimmed)) {
      return BigInteger.ZERO;
    }
    try {
      return new BigInteger(trimmed);
    }
    catch (NumberFormatException e) {
      return BigInteger.ZERO;
    }
  }

  //
  // checksums
  //

  public static String sha256Hex(final byte[] data) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return toHex(digest.digest(data));
    }
    catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  public static String sha256Hex(final InputStream input) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] buffer = new byte[8192];
      int read;
      while ((read = input.read(buffer)) != -1) {
        digest.update(buffer, 0, read);
      }
      return toHex(digest.digest());
    }
    catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  private static String toHex(final byte[] bytes) {
    StringBuilder builder = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      builder.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
    }
    return builder.toString();
  }

  //
  // index records
  //

  /**
   * Parses a record, throwing {@link IllegalArgumentException} with a readable message when it is not a JSON object.
   */
  public static ObjectNode parseRecord(final String json) {
    JsonNode node;
    try {
      node = MAPPER.readTree(json);
    }
    catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Invalid index record: " + e.getOriginalMessage());
    }
    if (node == null || !node.isObject()) {
      throw new IllegalArgumentException("Invalid index record: expected a JSON object");
    }
    return (ObjectNode) node;
  }

  public static String text(final JsonNode node, final String field) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? "" : value.asText();
  }

  /**
   * Validates a published index record against the crate/version from the request path.
   *
   * @return {@code null} when the record is valid, otherwise a human readable error for a 400 response
   */
  @Nullable
  public static String validateRecord(final String json, final String crate, final String version) {
    final ObjectNode record;
    try {
      record = parseRecord(json);
    }
    catch (IllegalArgumentException e) {
      return e.getMessage();
    }
    String name = text(record, "name");
    String vers = text(record, "vers");
    if (name.isEmpty()) {
      return "Index record is missing the 'name' field";
    }
    if (vers.isEmpty()) {
      return "Index record is missing the 'vers' field";
    }
    if (!name.equalsIgnoreCase(crate)) {
      return "Crate name mismatch: path '" + crate + "' does not match record '" + name + "'";
    }
    if (!vers.equals(version)) {
      return "Version mismatch: path '" + version + "' does not match record '" + vers + "'";
    }
    return null;
  }

  /**
   * Builds a minimal index record for clients that upload only the {@code .crate} archive.
   */
  public static String recordFor(final String crate, final String version, final String cksum, final String pubtime) {
    ObjectNode record = MAPPER.createObjectNode();
    record.put("name", crate);
    record.put("vers", version);
    record.putArray("deps");
    record.put("cksum", cksum);
    record.putObject("features");
    record.put("yanked", false);
    record.put("pubtime", pubtime);
    return record.toString();
  }

  /**
   * Returns the record with its {@code cksum} field replaced.
   */
  public static String withCksum(final String recordJson, final String cksum) {
    ObjectNode record = parseRecord(recordJson);
    record.put("cksum", cksum);
    return record.toString();
  }

  /**
   * Merges {@code recordJson} into the newline delimited index of a crate: an existing record of the same version is
   * replaced, records are sorted by version ascending and every record occupies exactly one line.
   */
  public static String mergeRecord(final String ndjson, final String recordJson) {
    ObjectNode record = parseRecord(recordJson);
    String version = text(record, "vers");
    if (version.isEmpty()) {
      throw new IllegalArgumentException("Index record is missing the 'vers' field");
    }

    List<ObjectNode> records = parseRecords(ndjson);
    records.removeIf(existing -> version.equals(text(existing, "vers")));
    records.add(record);
    records.sort(Comparator.comparing(existing -> text(existing, "vers"), CargoIndexBuilder::compareVersions));

    return toNdjson(records);
  }

  /**
   * Drops the record of a given version, returning the remaining index.
   */
  public static String removeRecord(final String ndjson, final String version) {
    List<ObjectNode> records = parseRecords(ndjson);
    records.removeIf(existing -> version.equals(text(existing, "vers")));
    return toNdjson(records);
  }

  public static List<ObjectNode> parseRecords(@Nullable final String ndjson) {
    List<ObjectNode> records = new ArrayList<>();
    if (ndjson == null || ndjson.isEmpty()) {
      return records;
    }
    for (String line : ndjson.split("\n")) {
      String trimmed = line.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      try {
        records.add(parseRecord(trimmed));
      }
      catch (IllegalArgumentException e) {
        // skip damaged lines rather than failing the whole index
      }
    }
    return records;
  }

  /**
   * Highest version contained in the index, empty when there is no record.
   */
  public static String maxVersion(@Nullable final String ndjson) {
    return parseRecords(ndjson).stream()
        .map(record -> text(record, "vers"))
        .filter(version -> !version.isEmpty())
        .max(CargoIndexBuilder::compareVersions)
        .orElse("");
  }

  @Nullable
  public static ObjectNode findRecord(@Nullable final String ndjson, final String version) {
    return parseRecords(ndjson).stream()
        .filter(record -> version.equals(text(record, "vers")))
        .findFirst()
        .orElse(null);
  }

  private static String toNdjson(final List<ObjectNode> records) {
    if (records.isEmpty()) {
      return "";
    }
    return records.stream().map(ObjectNode::toString).collect(joining("\n")) + "\n";
  }

  //
  // index/config.json
  //

  /**
   * Builds the sparse index configuration pointing at the given repository, e.g.
   * {@code {"dl":"http://host/repository/cargo/api/v1/crates","api":"http://host/repository/cargo/api"}}.
   */
  public static String configJson(final String repositoryBaseUrl) {
    String base = ensureTrailingSlash(repositoryBaseUrl);
    ObjectNode config = MAPPER.createObjectNode();
    config.put("dl", base + "api/v1/crates");
    config.put("api", base + "api");
    return config.toString();
  }

  /**
   * Rewrites the {@code dl} and {@code api} entries of a remote configuration so downloads and API calls go through
   * this repository. Unknown fields of the upstream document are preserved; a document that cannot be parsed is
   * replaced by a freshly generated configuration.
   */
  public static String rewriteConfig(final String configJson, final String repositoryBaseUrl) {
    String base = ensureTrailingSlash(repositoryBaseUrl);
    final ObjectNode config;
    try {
      JsonNode node = MAPPER.readTree(configJson);
      if (node == null || !node.isObject()) {
        return configJson(repositoryBaseUrl);
      }
      config = (ObjectNode) node;
    }
    catch (JsonProcessingException e) {
      return configJson(repositoryBaseUrl);
    }
    config.put("dl", base + "api/v1/crates");
    config.put("api", base + "api");
    return config.toString();
  }

  private static String ensureTrailingSlash(final String url) {
    String base = url == null ? "" : url.trim();
    if (base.isEmpty() || base.endsWith("/")) {
      return base;
    }
    return base + "/";
  }

  /**
   * Reads a top level string field of a JSON document, empty when the document or the field is missing.
   */
  public static String jsonField(final String json, final String field) {
    try {
      JsonNode node = MAPPER.readTree(json);
      return node == null ? "" : text(node, field);
    }
    catch (JsonProcessingException | RuntimeException e) {
      return "";
    }
  }

  //
  // search & crate detail
  //

  /**
   * Crates.io style filtering: every whitespace separated term of the query has to appear in the crate name or its
   * description (case insensitive). A blank query matches everything.
   */
  public static List<CargoCrateSummary> filter(@Nullable final List<CargoCrateSummary> crates, @Nullable final String query) {
    List<CargoCrateSummary> all = crates == null ? new ArrayList<>() : crates;
    if (query == null || query.trim().isEmpty()) {
      return all;
    }
    String[] terms = query.trim().toLowerCase(Locale.ROOT).split("\\s+");
    return all.stream().filter(crate -> matches(crate, terms)).collect(toList());
  }

  private static boolean matches(final CargoCrateSummary crate, final String[] terms) {
    String haystack = (crate.getName() + " " + crate.getDescription()).toLowerCase(Locale.ROOT);
    for (String term : terms) {
      if (!haystack.contains(term)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Builds the crates.io search response {@code {"crates":[...],"meta":{"total":N}}} honouring {@code page} and
   * {@code per_page}.
   */
  public static String searchResponse(
      @Nullable final List<CargoCrateSummary> crates,
      @Nullable final String query,
      final int page,
      final int perPage)
  {
    List<CargoCrateSummary> matched = filter(crates, query);
    int total = matched.size();

    int currentPage = Math.max(1, page);
    int pageSize = Math.max(1, Math.min(perPage, MAX_PER_PAGE));
    int from = Math.min((currentPage - 1) * pageSize, total);
    int to = Math.min(from + pageSize, total);

    ArrayNode entries = MAPPER.createArrayNode();
    for (int i = from; i < to; i++) {
      entries.add(summaryNode(matched.get(i)));
    }

    ObjectNode response = MAPPER.createObjectNode();
    response.set("crates", entries);
    response.putObject("meta").put("total", total);
    return response.toString();
  }

  private static ObjectNode summaryNode(final CargoCrateSummary crate) {
    ObjectNode node = MAPPER.createObjectNode();
    node.put("id", crate.getName());
    node.put("name", crate.getName());
    node.put("max_version", crate.getMaxVersion());
    node.put("newest_version", crate.getMaxVersion());
    node.put("description", crate.getDescription());
    node.put("downloads", crate.getDownloads());
    node.put("updated_at", crate.getUpdatedAt());
    return node;
  }

  /**
   * Builds the {@code GET api/v1/crates/{crate}} response used by {@code cargo info}.
   */
  public static String detailResponse(final CargoCrateSummary crate, @Nullable final String ndjson) {
    ObjectNode crateNode = summaryNode(crate);
    crateNode.put("recent_downloads", 0);

    ArrayNode versions = MAPPER.createArrayNode();
    for (ObjectNode record : parseRecords(ndjson)) {
      ObjectNode version = MAPPER.createObjectNode();
      version.put("crate", crate.getName());
      version.put("num", text(record, "vers"));
      version.put("cksum", text(record, "cksum"));
      version.put("yanked", record.path("yanked").asBoolean(false));
      version.put("downloads", crate.getDownloads());
      version.put("created_at", text(record, "pubtime"));
      versions.add(version);
    }

    ObjectNode response = MAPPER.createObjectNode();
    response.set("crate", crateNode);
    response.set("versions", versions);
    return response.toString();
  }

  /**
   * Merges the search responses of group members: entries are concatenated in member order and de-duplicated by
   * {@code id}, so the first member that knows a crate wins.
   */
  public static String mergeSearch(final List<String> memberResponses) {
    ArrayNode merged = MAPPER.createArrayNode();
    Set<String> seen = new HashSet<>();
    if (memberResponses != null) {
      for (String body : memberResponses) {
        final JsonNode root;
        try {
          root = MAPPER.readTree(body);
        }
        catch (JsonProcessingException | RuntimeException e) {
          continue;
        }
        JsonNode crates = root == null ? null : root.get("crates");
        if (crates == null || !crates.isArray()) {
          continue;
        }
        for (JsonNode entry : crates) {
          String id = entry.path("id").asText(entry.path("name").asText(""));
          if (!id.isEmpty() && seen.add(id)) {
            merged.add(entry);
          }
        }
      }
    }

    ObjectNode response = MAPPER.createObjectNode();
    response.set("crates", merged);
    response.putObject("meta").put("total", merged.size());
    return response.toString();
  }

  /**
   * Convenience for tests and callers that want to count the records of an index.
   */
  public static String toJson(final ObjectNode node) {
    return node.toString();
  }

  static byte[] utf8(final String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }
}
