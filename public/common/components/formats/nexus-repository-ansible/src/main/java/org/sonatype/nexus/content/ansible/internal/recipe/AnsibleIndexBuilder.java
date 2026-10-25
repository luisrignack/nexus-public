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
package org.sonatype.nexus.content.ansible.internal.recipe;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.sonatype.nexus.common.entity.Continuation;
import org.sonatype.nexus.content.ansible.AnsibleContentFacet;
import org.sonatype.nexus.repository.ansible.AnsibleCoordinatesHelper;
import org.sonatype.nexus.repository.content.AssetBlob;
import org.sonatype.nexus.repository.content.facet.ContentFacet;
import org.sonatype.nexus.repository.content.fluent.FluentAsset;
import org.sonatype.nexus.repository.content.fluent.FluentComponent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Generates the Galaxy (v3) and Galaxy v2 documents served by an Ansible repository.
 *
 * <p>Every document is built from the components stored in the repository, so a hosted repository
 * and a group repository produce the same shape; absolute URLs are derived from the base URL of the
 * repository that answers the request.</p>
 */
public final class AnsibleIndexBuilder
{
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final int PAGE_SIZE = 100;

  private static final String ISO = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

  private AnsibleIndexBuilder() {
  }

  // ---------------------------------------------------------------- documents

  /**
   * API root document, useful as a smoke test of connectivity.
   */
  public static byte[] root(final String baseUrl) {
    ObjectNode node = MAPPER.createObjectNode();
    node.put("server_version", "3.89.0");
    node.put("galaxy_api_version", "v3");
    node.put("available_versions", "v3");
    ArrayNode versions = node.putArray("available_versions");
    versions.add("v3");
    node.put("collection_index", join(baseUrl, AnsibleCoordinatesHelper.V3_INDEX) + "/");
    return toBytes(node);
  }

  /**
   * Paged list of every collection in the repository.
   */
  public static byte[] collectionList(final ContentFacet content, final String baseUrl) throws IOException {
    Map<String, List<VersionEntry>> collections = load(content);
    ArrayNode data = MAPPER.createArrayNode();
    for (Map.Entry<String, List<VersionEntry>> entry : collections.entrySet()) {
      data.add(collectionNode(entry.getKey(), entry.getValue(), baseUrl, false));
    }
    return listDocument(data, collections.size());
  }

  /**
   * Paged list of the collections of one namespace.
   */
  public static byte[] namespaceList(
      final ContentFacet content,
      final String namespace,
      final String baseUrl) throws IOException
  {
    Map<String, List<VersionEntry>> collections = load(content);
    ArrayNode data = MAPPER.createArrayNode();
    int count = 0;
    for (Map.Entry<String, List<VersionEntry>> entry : collections.entrySet()) {
      if (!namespace.equals(namespaceOf(entry.getKey()))) {
        continue;
      }
      data.add(collectionNode(entry.getKey(), entry.getValue(), baseUrl, false));
      count++;
    }
    return listDocument(data, count);
  }

  /**
   * Detail of a single collection, including {@code highest_version}.
   */
  public static byte[] collection(
      final ContentFacet content,
      final String namespace,
      final String name,
      final String baseUrl) throws IOException
  {
    Map<String, List<VersionEntry>> collections = load(content);
    List<VersionEntry> versions = collections.get(key(namespace, name));
    if (versions == null || versions.isEmpty()) {
      return null;
    }
    return toBytes(collectionNode(key(namespace, name), versions, baseUrl, true));
  }

  /**
   * Versions of a single collection (newest first).
   */
  public static byte[] versions(
      final ContentFacet content,
      final String namespace,
      final String name,
      final String baseUrl) throws IOException
  {
    Map<String, List<VersionEntry>> collections = load(content);
    List<VersionEntry> versions = collections.get(key(namespace, name));
    if (versions == null || versions.isEmpty()) {
      return listDocument(MAPPER.createArrayNode(), 0);
    }
    ArrayNode data = MAPPER.createArrayNode();
    versions.forEach(version -> data.add(versionNode(version, baseUrl)));
    return listDocument(data, versions.size());
  }

  /**
   * Detail of a single collection version, or {@code null} when it does not exist.
   */
  public static byte[] version(
      final ContentFacet content,
      final String namespace,
      final String name,
      final String versionName,
      final String baseUrl) throws IOException
  {
    Map<String, List<VersionEntry>> collections = load(content);
    List<VersionEntry> versions = collections.get(key(namespace, name));
    if (versions == null) {
      return null;
    }
    return versions.stream()
        .filter(version -> version.version.equals(versionName))
        .findFirst()
        .map(version -> toBytes(versionNode(version, baseUrl)))
        .orElse(null);
  }

  // ----------------------------------------------------------- v2 documents

  /**
   * Galaxy v2 collection detail ({@code latest_version} as an href/version pair).
   */
  public static byte[] v2Collection(
      final ContentFacet content,
      final String namespace,
      final String name,
      final String baseUrl) throws IOException
  {
    Map<String, List<VersionEntry>> collections = load(content);
    List<VersionEntry> versions = collections.get(key(namespace, name));
    if (versions == null || versions.isEmpty()) {
      return null;
    }
    VersionEntry latest = versions.get(0);
    ObjectNode node = MAPPER.createObjectNode();
    node.put("name", name);
    node.put("namespace", namespace);
    node.put("deprecated", false);
    node.put("created", iso(latest.created));
    node.put("updated", iso(latest.created));
    node.put("versions_url", join(baseUrl, v2VersionsUrl(namespace, name)));
    ObjectNode latestVersion = node.putObject("latest_version");
    latestVersion.put("version", latest.version);
    latestVersion.put("href", join(baseUrl, v2VersionUrl(namespace, name, latest.version)));
    return toBytes(node);
  }

  /**
   * Galaxy v2 version list ({@code count}/{@code results} shape).
   */
  public static byte[] v2Versions(
      final ContentFacet content,
      final String namespace,
      final String name,
      final String baseUrl) throws IOException
  {
    Map<String, List<VersionEntry>> collections = load(content);
    List<VersionEntry> versions = collections.get(key(namespace, name));
    ArrayNode results = MAPPER.createArrayNode();
    if (versions != null) {
      versions.forEach(version -> results.add(v2VersionNode(version, baseUrl)));
    }
    ObjectNode node = MAPPER.createObjectNode();
    node.put("count", results.size());
    node.putNull("next");
    node.putNull("previous");
    node.set("results", results);
    return toBytes(node);
  }

  /**
   * Galaxy v2 version detail.
   */
  public static byte[] v2Version(
      final ContentFacet content,
      final String namespace,
      final String name,
      final String versionName,
      final String baseUrl) throws IOException
  {
    Map<String, List<VersionEntry>> collections = load(content);
    List<VersionEntry> versions = collections.get(key(namespace, name));
    if (versions == null) {
      return null;
    }
    return versions.stream()
        .filter(version -> version.version.equals(versionName))
        .findFirst()
        .map(version -> toBytes(v2VersionNode(version, baseUrl)))
        .orElse(null);
  }

  // ------------------------------------------------------------------ merges

  /**
   * Merges collection list documents coming from the members of a group.
   */
  public static byte[] mergeCollectionLists(final List<String> documents) {
    Map<String, ObjectNode> merged = new TreeMap<>();
    for (String document : documents) {
      JsonNode data = parseListData(document);
      if (data == null) {
        continue;
      }
      for (JsonNode item : data) {
        String namespace = text(item, "namespace");
        String name = text(item, "name");
        if (namespace == null || name == null) {
          continue;
        }
        merged.putIfAbsent(key(namespace, name), (ObjectNode) deep(item));
      }
    }
    ArrayNode data = MAPPER.createArrayNode();
    merged.values().forEach(data::add);
    return listDocument(data, data.size());
  }

  /**
   * Merges version list documents coming from the members of a group.
   */
  public static byte[] mergeVersionLists(final List<String> documents) {
    Map<String, ObjectNode> merged = new TreeMap<>(VERSION_STRING_ORDER);
    for (String document : documents) {
      JsonNode data = parseListData(document);
      if (data == null) {
        continue;
      }
      for (JsonNode item : data) {
        String version = versionOf(item);
        if (version == null) {
          continue;
        }
        merged.putIfAbsent(version, (ObjectNode) deep(item));
      }
    }
    ArrayNode data = MAPPER.createArrayNode();
    merged.values().forEach(data::add);
    return listDocument(data, data.size());
  }

  /**
   * Rewrites every absolute URL that points at {@code from} so it points at {@code to}.
   * Used so downloads served through a group stay on the group rather than a member.
   */
  public static String rewrite(final String json, final String from, final String to) {
    if (json == null || from == null || from.isEmpty() || to == null || to.isEmpty() || from.equals(to)) {
      return json;
    }
    return json.replace(from, to);
  }

  // ------------------------------------------------------------------ helpers

  private static ObjectNode collectionNode(
      final String qualifiedName,
      final List<VersionEntry> versions,
      final String baseUrl,
      final boolean detail)
  {
    VersionEntry latest = versions.get(0);
    ObjectNode node = MAPPER.createObjectNode();
    node.put("name", nameOf(qualifiedName));
    node.put("namespace", namespaceOf(qualifiedName));
    node.put("deprecated", false);
    node.set("highest_version", versionNode(latest, baseUrl));
    node.put("latest_version", latest.version);
    node.put("versions_url", join(baseUrl, versionsUrl(namespaceOf(qualifiedName), nameOf(qualifiedName))));
    node.put("created", iso(latest.created));
    node.put("updated", iso(latest.created));
    if (detail) {
      ObjectNode dependencies = node.putObject("dependencies");
      versions.get(0).dependencies.forEach(dependencies::put);
      node.put("version_count", versions.size());
      node.put("download_url", join(baseUrl, downloadUrl(latest)));
      node.put("href", join(baseUrl, collectionUrl(namespaceOf(qualifiedName), nameOf(qualifiedName))));
    }
    return node;
  }

  private static ObjectNode versionNode(final VersionEntry version, final String baseUrl) {
    ObjectNode node = MAPPER.createObjectNode();
    node.put("name", version.name);
    node.put("namespace", version.namespace);
    node.put("version", version.version);
    node.put("created", iso(version.created));
    node.put("updated", iso(version.created));
    node.put("download_url", join(baseUrl, downloadUrl(version)));
    ObjectNode artifact = node.putObject("artifact");
    artifact.put("filename", version.fileName());
    artifact.put("size", version.size);
    if (version.sha256 != null) {
      artifact.put("sha256", version.sha256);
    }
    artifact.put("deleted", false);
    ObjectNode manifest = node.putObject("manifest");
    manifest.put("namespace", version.namespace);
    manifest.put("name", version.name);
    manifest.put("version", version.version);
    if (version.description != null) {
      manifest.put("description", version.description);
    }
    manifest.set("dependencies", MAPPER.valueToTree(version.dependencies));
    node.set("dependencies", MAPPER.valueToTree(version.dependencies));
    return node;
  }

  private static ObjectNode v2VersionNode(final VersionEntry version, final String baseUrl) {
    ObjectNode node = MAPPER.createObjectNode();
    node.put("version", version.version);
    node.put("href", join(baseUrl, v2VersionUrl(version.namespace, version.name, version.version)));
    ObjectNode artifact = node.putObject("artifact");
    artifact.put("filename", version.fileName());
    artifact.put("size", version.size);
    if (version.sha256 != null) {
      artifact.put("sha256", version.sha256);
    }
    artifact.put("url", join(baseUrl, downloadUrl(version)));
    return node;
  }

  private static byte[] listDocument(final ArrayNode data, final int count) {
    ObjectNode node = MAPPER.createObjectNode();
    ObjectNode links = node.putObject("links");
    links.putNull("next");
    links.putNull("previous");
    ObjectNode meta = node.putObject("meta");
    meta.put("count", count);
    // Galaxy clients read either `data` (v3) or `results` (v2); both are served from one document.
    node.set("data", data);
    node.set("results", data.deepCopy());
    node.put("count", count);
    return toBytes(node);
  }

  private static Map<String, List<VersionEntry>> load(final ContentFacet content) throws IOException {
    Map<String, List<VersionEntry>> collections = new LinkedHashMap<>();
    String continuationToken = null;
    do {
      Continuation<FluentComponent> page = content.components().browseWithAssets(PAGE_SIZE, continuationToken);
      for (FluentComponent component : page) {
        VersionEntry entry = entry(component);
        if (entry == null) {
          continue;
        }
        collections.computeIfAbsent(key(entry.namespace, entry.name), ignored -> new ArrayList<>()).add(entry);
      }
      if (page.isEmpty()) {
        break;
      }
      continuationToken = page.nextContinuationToken();
    }
    while (continuationToken != null);

    collections.values().forEach(versions -> versions.sort(VERSION_ORDER));
    return collections;
  }

  /**
   * Newest first.
   */
  private static final Comparator<VersionEntry> VERSION_ORDER =
      (left, right) -> compareVersions(right.version, left.version);

  /**
   * Newest first, for merging version documents by version string.
   */
  private static final Comparator<String> VERSION_STRING_ORDER =
      (left, right) -> compareVersions(right, left);

  private static VersionEntry entry(final FluentComponent component) {
    String namespace = component.namespace();
    String name = component.name();
    String version = component.version();
    if (name == null || name.isEmpty() || version == null || version.isEmpty()) {
      return null;
    }
    if (namespace == null || namespace.isEmpty()) {
      // components created outside the Galaxy API carry `namespace.name` in the name field
      int dot = name.indexOf('.');
      if (dot <= 0) {
        return null;
      }
      namespace = name.substring(0, dot);
      name = name.substring(dot + 1);
    }

    VersionEntry entry = new VersionEntry();
    entry.namespace = namespace;
    entry.name = name;
    entry.version = version;
    entry.created = component.created();
    entry.description = attribute(component, AnsibleContentFacet.DESCRIPTION_ATTRIBUTE);
    entry.dependencies = new LinkedHashMap<>();
    Object dependencies = rawAttribute(component, AnsibleContentFacet.DEPENDENCIES_ATTRIBUTE);
    if (dependencies instanceof Map<?, ?> map) {
      map.forEach((key, value) -> entry.dependencies.put(String.valueOf(key), String.valueOf(value)));
    }

    for (FluentAsset asset : component.assets()) {
      if (!asset.path().toLowerCase(Locale.ROOT).endsWith(".tar.gz")) {
        continue;
      }
      entry.path = asset.path();
      entry.size = asset.assetBlobSize();
      asset.blob().map(AssetBlob::checksums).ifPresent(checksums -> entry.sha256 = checksums.get("sha256"));
      break;
    }

    return entry.path == null ? null : entry;
  }

  private static String attribute(final FluentComponent component, final String key) {
    Object value = rawAttribute(component, key);
    return value == null ? null : String.valueOf(value);
  }

  private static Object rawAttribute(final FluentComponent component, final String key) {
    try {
      return component.attributes().get(key);
    }
    catch (RuntimeException e) {
      return null;
    }
  }

  private static JsonNode parseListData(final String document) {
    if (document == null || document.isEmpty()) {
      return null;
    }
    try {
      JsonNode node = MAPPER.readTree(document);
      if (node == null || !node.isObject()) {
        return null;
      }
      JsonNode data = node.get("data");
      if (data == null || !data.isArray()) {
        data = node.get("results");
      }
      return data != null && data.isArray() ? data : null;
    }
    catch (IOException e) {
      return null;
    }
  }

  /**
   * Merges the detail documents of a single collection coming from the members of a group: the
   * first document wins, with its URLs rewritten to the group.
   */
  public static String mergeCollectionDetail(final List<String> documents, final String groupUrl) {
    for (String document : documents) {
      JsonNode node = parseObject(document);
      if (node != null && node.has("name") && node.has("namespace")) {
        return rewrite(node.toString(), originOf(document), groupUrl);
      }
    }
    return null;
  }

  /**
   * Merges the detail documents of a single collection version coming from the members of a group.
   */
  public static String mergeVersionDetail(final List<String> documents, final String groupUrl) {
    for (String document : documents) {
      JsonNode node = parseObject(document);
      if (node != null && node.has("version") && node.has("artifact")) {
        return rewrite(node.toString(), originOf(document), groupUrl);
      }
    }
    return null;
  }

  /**
   * Best-effort origin (scheme://host[:port]) of the absolute URLs contained in a document, so the
   * group can rewrite them to its own base.
   */
  private static String originOf(final String document) {
    java.util.regex.Matcher matcher =
        ORIGIN_PATTERN.matcher(document);
    return matcher.find() ? matcher.group() : null;
  }

  private static final java.util.regex.Pattern ORIGIN_PATTERN =
      java.util.regex.Pattern.compile("https?://[^\"\\\\/]+");

  private static JsonNode parseObject(final String document) {
    if (document == null || document.isEmpty()) {
      return null;
    }
    try {
      JsonNode node = MAPPER.readTree(document);
      return node != null && node.isObject() ? node : null;
    }
    catch (IOException e) {
      return null;
    }
  }

  private static JsonNode deep(final JsonNode node) {
    try {
      return MAPPER.readTree(node.toString());
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String versionOf(final JsonNode node) {
    JsonNode version = node.get("version");
    if (version == null) {
      return null;
    }
    if (version.isTextual()) {
      return version.asText();
    }
    return version.isObject() && version.has("version") ? version.get("version").asText() : null;
  }

  private static String key(final String namespace, final String name) {
    return namespace + "." + name;
  }

  private static String namespaceOf(final String qualifiedName) {
    int dot = qualifiedName.indexOf('.');
    return dot < 0 ? qualifiedName : qualifiedName.substring(0, dot);
  }

  private static String nameOf(final String qualifiedName) {
    int dot = qualifiedName.indexOf('.');
    return dot < 0 ? qualifiedName : qualifiedName.substring(dot + 1);
  }

  private static String text(final JsonNode node, final String field) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? null : value.asText();
  }

  private static String iso(final OffsetDateTime time) {
    OffsetDateTime value = time == null ? OffsetDateTime.now() : time;
    return DateTimeFormatter.ofPattern(ISO).withZone(java.time.ZoneOffset.UTC).format(value);
  }

  private static String join(final String baseUrl, final String path) {
    String base = AnsibleCoordinatesHelper.stripTrailingSlash(baseUrl);
    return base + (path.startsWith("/") ? path : "/" + path);
  }

  private static String collectionUrl(final String namespace, final String name) {
    return AnsibleCoordinatesHelper.V3_INDEX + "/" + namespace + "/" + name;
  }

  private static String versionsUrl(final String namespace, final String name) {
    return collectionUrl(namespace, name) + "/versions";
  }

  private static String downloadUrl(final VersionEntry version) {
    return AnsibleCoordinatesHelper.V3_DOWNLOAD + "/" + version.namespace + "/" + version.name + "/"
        + version.version + "/download";
  }

  private static String v2VersionsUrl(final String namespace, final String name) {
    return AnsibleCoordinatesHelper.V2_COLLECTIONS + "/" + namespace + "/" + name + "/versions";
  }

  private static String v2VersionUrl(final String namespace, final String name, final String version) {
    return v2VersionsUrl(namespace, name) + "/" + version;
  }

  private static byte[] toBytes(final JsonNode node) {
    try {
      return MAPPER.writeValueAsBytes(node);
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Compares two semantic-ish versions numerically segment by segment, falling back to text.
   */
  public static int compareVersions(final String left, final String right) {
    String[] leftParts = left.split("[.+\\-]");
    String[] rightParts = right.split("[.+\\-]");
    int length = Math.max(leftParts.length, rightParts.length);
    for (int i = 0; i < length; i++) {
      String leftPart = i < leftParts.length ? leftParts[i] : "";
      String rightPart = i < rightParts.length ? rightParts[i] : "";
      if (leftPart.isEmpty() && rightPart.isEmpty()) {
        continue;
      }
      if (leftPart.isEmpty()) {
        return -1;
      }
      if (rightPart.isEmpty()) {
        return 1;
      }
      int comparison;
      try {
        comparison = Long.compare(Long.parseLong(leftPart), Long.parseLong(rightPart));
      }
      catch (NumberFormatException e) {
        comparison = leftPart.compareToIgnoreCase(rightPart);
      }
      if (comparison != 0) {
        return comparison;
      }
    }
    return 0;
  }

  private static final class VersionEntry
  {
    String namespace;

    String name;

    String version;

    String path;

    String sha256;

    String description;

    long size;

    OffsetDateTime created;

    Map<String, String> dependencies = new LinkedHashMap<>();

    String fileName() {
      return AnsibleCoordinatesHelper.fileName(namespace, name, version);
    }
  }
}
