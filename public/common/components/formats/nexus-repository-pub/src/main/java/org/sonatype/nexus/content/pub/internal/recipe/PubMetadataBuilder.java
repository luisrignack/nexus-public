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
package org.sonatype.nexus.content.pub.internal.recipe;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.sonatype.nexus.repository.pub.PubPackageMetadata;
import org.sonatype.nexus.repository.pub.internal.PubCoordinatesHelper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Builds, merges and rewrites the JSON documents of the Hosted Pub Repository Specification v2.
 *
 * <p>The documents are generated on demand rather than stored as assets: a stored copy would go
 * stale as soon as a package is published or deleted. Every {@code archive_url} is rewritten to the
 * repository that answers the request, so the Pub client keeps its bearer token and all downloads
 * stay inside Nexus instead of going straight to {@code pub.dev}.</p>
 */
public final class PubMetadataBuilder
{
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private PubMetadataBuilder() {
    // utility class
  }

  /**
   * Builds the {@code /api/packages/<name>} document of a hosted repository.
   */
  public static byte[] build(
      final String name,
      final String repositoryUrl,
      final Collection<PubPackageMetadata> packages) throws IOException
  {
    List<PubPackageMetadata> sorted = new ArrayList<>(packages);
    sorted.sort(Comparator.comparing(PubPackageMetadata::getVersion).reversed());

    ObjectNode root = MAPPER.createObjectNode();
    root.put("name", name);
    ArrayNode versions = root.putArray("versions");
    for (PubPackageMetadata metadata : sorted) {
      versions.add(versionNode(repositoryUrl, name, metadata.getVersion(), metadata.getSha256(),
          metadata.getPubspecJson()));
    }
    if (!sorted.isEmpty()) {
      PubPackageMetadata latest = sorted.get(0);
      root.set("latest", versionNode(repositoryUrl, name, latest.getVersion(), latest.getSha256(),
          latest.getPubspecJson()));
    }
    return MAPPER.writeValueAsBytes(root);
  }

  /**
   * Merges the package documents of the members of a group repository. For a given version the
   * first member wins, matching the usual Nexus group lookup order.
   */
  public static byte[] merge(
      final String name,
      final String repositoryUrl,
      final List<byte[]> documents) throws IOException
  {
    Map<String, ObjectNode> versions = new LinkedHashMap<>();
    ObjectNode first = null;
    for (byte[] document : documents) {
      JsonNode parsed = parse(document);
      if (!(parsed instanceof ObjectNode)) {
        continue;
      }
      ObjectNode node = (ObjectNode) parsed;
      if (first == null) {
        first = node;
      }
      JsonNode entries = node.get("versions");
      if (entries != null && entries.isArray()) {
        for (JsonNode entry : entries) {
          if (entry instanceof ObjectNode && entry.path("version").isTextual()) {
            versions.putIfAbsent(entry.get("version").asText(), (ObjectNode) entry);
          }
        }
      }
    }

    ObjectNode root = MAPPER.createObjectNode();
    root.put("name", name);
    ArrayNode mergedVersions = root.putArray("versions");
    for (ObjectNode node : versions.values()) {
      mergedVersions.add(rewriteVersion(repositoryUrl, name, node));
    }
    String latestVersion = first == null || !first.path("latest").path("version").isTextual()
        ? (versions.isEmpty() ? null : versions.keySet().iterator().next())
        : first.path("latest").path("version").asText();
    ObjectNode latest = latestVersion == null ? null : versions.get(latestVersion);
    if (latest != null) {
      root.set("latest", rewriteVersion(repositoryUrl, name, latest));
    }
    return MAPPER.writeValueAsBytes(root);
  }

  /**
   * Rewrites the archive URLs of a proxied document so that downloads go through this repository.
   * Unparseable bodies are returned unchanged; a broken upstream response must not be cached as an
   * empty document.
   */
  public static byte[] rewrite(final String repositoryUrl, final byte[] document) throws IOException {
    JsonNode parsed = parse(document);
    if (!(parsed instanceof ObjectNode)) {
      return document;
    }
    ObjectNode node = (ObjectNode) parsed;
    String name = node.path("name").asText(null);

    JsonNode versions = node.get("versions");
    if (versions != null && versions.isArray()) {
      for (JsonNode version : versions) {
        if (version instanceof ObjectNode && version.path("version").isTextual()) {
          rewriteVersion(repositoryUrl, name, (ObjectNode) version);
        }
      }
    }
    JsonNode latest = node.get("latest");
    if (latest instanceof ObjectNode && latest.path("version").isTextual()) {
      rewriteVersion(repositoryUrl, name, (ObjectNode) latest);
    }
    else {
      // Deprecated single-version endpoint: the identity is in the requested archive URL.
      rewriteLegacyArchiveUrl(repositoryUrl, node);
    }
    return MAPPER.writeValueAsBytes(node);
  }

  private static ObjectNode versionNode(
      final String repositoryUrl,
      final String name,
      final String version,
      final String sha256,
      final String pubspecJson) throws IOException
  {
    ObjectNode node = MAPPER.createObjectNode();
    node.put("version", version);
    node.put("archive_url", archiveUrl(repositoryUrl, name, version));
    if (sha256 != null) {
      node.put("archive_sha256", sha256);
    }
    if (pubspecJson != null) {
      node.set("pubspec", MAPPER.readTree(pubspecJson));
    }
    return node;
  }

  private static ObjectNode rewriteVersion(
      final String repositoryUrl,
      final String name,
      final ObjectNode version)
  {
    String actualVersion = version.path("version").asText();
    version.put("archive_url", archiveUrl(repositoryUrl, name, actualVersion));
    return version;
  }

  private static void rewriteLegacyArchiveUrl(final String repositoryUrl, final ObjectNode node) {
    JsonNode archiveUrl = node.get("archive_url");
    if (archiveUrl == null || !archiveUrl.isTextual()) {
      return;
    }
    try {
      String path = new URI(archiveUrl.asText()).getPath();
      java.util.Optional<String[]> coordinates = PubCoordinatesHelper.fromArchivePath(path);
      if (coordinates.isPresent()) {
        node.put("archive_url", archiveUrl(repositoryUrl, coordinates.get()[0], coordinates.get()[1]));
      }
    }
    catch (URISyntaxException e) {
      // leave an unparseable upstream URL untouched
    }
  }

  private static String archiveUrl(final String repositoryUrl, final String name, final String version) {
    String base = repositoryUrl.endsWith("/")
        ? repositoryUrl.substring(0, repositoryUrl.length() - 1)
        : repositoryUrl;
    return base + PubCoordinatesHelper.archivePath(name, version);
  }

  private static JsonNode parse(final byte[] document) {
    if (document == null || document.length == 0) {
      return MAPPER.createObjectNode();
    }
    try {
      return MAPPER.readTree(document);
    }
    catch (IOException e) {
      return MAPPER.createObjectNode();
    }
  }
}
