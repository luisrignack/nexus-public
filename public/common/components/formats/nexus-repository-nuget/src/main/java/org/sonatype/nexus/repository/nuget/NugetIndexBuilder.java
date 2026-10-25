/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype Nexus" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.repository.nuget;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.annotation.Nullable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Builds the JSON documents generated from repository content: the flat container version index, the
 * registration index and the search results.
 */
public final class NugetIndexBuilder
{
  public static final int DEFAULT_TAKE = 20;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private NugetIndexBuilder() {
    // no instances
  }

  /**
   * {@code v3-flatcontainer/<id>/index.json}: {@code {"versions":["1.0.0", ...]}} sorted with SemVer semantics.
   */
  public static byte[] versionsIndex(final List<String> versions) {
    ObjectNode root = MAPPER.createObjectNode();
    ArrayNode array = root.putArray("versions");
    NugetCoordinatesHelper.sortVersions(versions).forEach(array::add);
    return toBytes(root);
  }

  /**
   * {@code v3/registration/<id>/index.json} built from the stored packages.
   */
  public static byte[] registrationIndex(final String baseUrl, final String id, final List<NugetPackage> packages) {
    String base = NugetCoordinatesHelper.ensureTrailingSlash(baseUrl);
    String normalizedId = NugetCoordinatesHelper.normalizeId(id);
    String indexUrl = base + NugetCoordinatesHelper.REGISTRATION + "/" + normalizedId + "/index.json";

    List<NugetPackage> sorted = new ArrayList<>(packages);
    sorted.sort((a, b) -> NugetCoordinatesHelper.compareVersions(a.getVersion(), b.getVersion()));

    ObjectNode root = MAPPER.createObjectNode();
    root.put("@id", indexUrl);
    root.put("count", sorted.size());

    ArrayNode pages = root.putArray("items");
    if (!sorted.isEmpty()) {
      ObjectNode page = pages.addObject();
      page.put("@id", indexUrl + "#page");
      page.put("lower", sorted.get(0).getVersion());
      page.put("upper", sorted.get(sorted.size() - 1).getVersion());
      page.put("count", sorted.size());
      ArrayNode leaves = page.putArray("items");
      for (NugetPackage nugetPackage : sorted) {
        leaves.add(leaf(base, indexUrl, nugetPackage));
      }
    }

    ArrayNode versions = root.putArray("versions");
    for (NugetPackage nugetPackage : sorted) {
      versions.add(leaf(base, indexUrl, nugetPackage));
    }

    return toBytes(root);
  }

  /**
   * {@code v3/query?q=...&take=...&skip=...}: {@code {"totalHits":N,"data":[...]}}.
   *
   * <p>The filter matches (case insensitively) against id, description and tags; {@code totalHits} is the number
   * of matches before paging is applied.</p>
   */
  public static byte[] searchResults(
      final String baseUrl,
      final List<NugetPackage> packages,
      @Nullable final String query,
      final int skip,
      final int take)
  {
    List<NugetPackage> matches = new ArrayList<>();
    for (NugetPackage nugetPackage : packages) {
      if (matches(nugetPackage, query)) {
        matches.add(nugetPackage);
      }
    }

    int from = Math.max(0, skip);
    int limit = take < 0 ? DEFAULT_TAKE : take;
    int to = limit == 0 ? from : Math.min(matches.size(), from + limit);

    String base = NugetCoordinatesHelper.ensureTrailingSlash(baseUrl);
    ObjectNode root = MAPPER.createObjectNode();
    root.put("totalHits", matches.size());
    ArrayNode data = root.putArray("data");
    for (int i = from; i < to; i++) {
      NugetPackage nugetPackage = matches.get(i);
      data.add(catalogEntry(base, base + NugetCoordinatesHelper.REGISTRATION + "/"
          + NugetCoordinatesHelper.normalizeId(nugetPackage.getId()) + "/index.json", nugetPackage));
    }
    return toBytes(root);
  }

  /**
   * Whether a package matches the free text query over id, description and tags.
   */
  public static boolean matches(@Nullable final NugetPackage nugetPackage, @Nullable final String query) {
    if (nugetPackage == null) {
      return false;
    }
    String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    if (needle.isEmpty()) {
      return true;
    }
    return contains(nugetPackage.getId(), needle)
        || contains(nugetPackage.getDescription(), needle)
        || contains(nugetPackage.getTags(), needle);
  }

  private static boolean contains(@Nullable final String value, final String needle) {
    return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
  }

  private static ObjectNode leaf(final String base, final String indexUrl, final NugetPackage nugetPackage) {
    ObjectNode leaf = MAPPER.createObjectNode();
    leaf.put("@id", indexUrl);
    leaf.set("catalogEntry", catalogEntry(base, indexUrl, nugetPackage));
    leaf.put("packageContent", packageContent(base, nugetPackage));
    return leaf;
  }

  /**
   * The {@code catalogEntry} document built from the nuspec metadata of a package.
   */
  public static ObjectNode catalogEntry(final String baseUrl, final String indexUrl, final NugetPackage nugetPackage) {
    String base = NugetCoordinatesHelper.ensureTrailingSlash(baseUrl);
    ObjectNode entry = MAPPER.createObjectNode();
    entry.put("@id", indexUrl);
    entry.put("id", nugetPackage.getId());
    entry.put("version", nugetPackage.getVersion());
    putIfPresent(entry, "title", nugetPackage.getTitle());
    putIfPresent(entry, "description", nugetPackage.getDescription());
    putIfPresent(entry, "authors", nugetPackage.getAuthors());
    putIfPresent(entry, "owners", nugetPackage.getOwners());
    putIfPresent(entry, "projectUrl", nugetPackage.getProjectUrl());
    putIfPresent(entry, "licenseUrl", nugetPackage.getLicenseUrl());
    putIfPresent(entry, "tags", nugetPackage.getTags());
    if (!nugetPackage.getDependencies().isEmpty()) {
      ArrayNode dependencies = entry.putArray("dependencies");
      nugetPackage.getDependencies().forEach(dependencies::add);
    }
    putIfPresent(entry, "published", nugetPackage.getPublished());
    entry.put("listed", nugetPackage.isListed());
    entry.put("packageContent", packageContent(base, nugetPackage));
    return entry;
  }

  private static String packageContent(final String base, final NugetPackage nugetPackage) {
    return NugetCoordinatesHelper.packageContentUrl(base, nugetPackage.getId(), nugetPackage.getVersion());
  }

  private static void putIfPresent(final ObjectNode node, final String field, @Nullable final String value) {
    if (value != null && !value.trim().isEmpty()) {
      node.put(field, value.trim());
    }
  }

  /**
   * Merges the {@code data} arrays of several {@code v3/query} responses, keeping the first entry per package id.
   *
   * @return the merged response document
   */
  public static byte[] mergeSearchResults(final List<byte[]> responses) {
    Set<String> seen = new LinkedHashSet<>();
    ObjectNode root = MAPPER.createObjectNode();
    ArrayNode data = root.putArray("data");
    int totalHits = 0;

    for (byte[] response : responses) {
      if (response == null || response.length == 0) {
        continue;
      }
      try {
        com.fasterxml.jackson.databind.JsonNode parsed = MAPPER.readTree(response);
        if (parsed == null || !parsed.isObject()) {
          continue;
        }
        com.fasterxml.jackson.databind.JsonNode hits = parsed.get("totalHits");
        if (hits != null && hits.isNumber()) {
          totalHits += hits.asInt();
        }
        com.fasterxml.jackson.databind.JsonNode items = parsed.get("data");
        if (items == null || !items.isArray()) {
          continue;
        }
        for (com.fasterxml.jackson.databind.JsonNode item : items) {
          if (!item.isObject()) {
            continue;
          }
          com.fasterxml.jackson.databind.JsonNode id = item.get("id");
          if (id == null || !id.isTextual()) {
            continue;
          }
          if (seen.add(id.asText().toLowerCase(Locale.ROOT))) {
            data.add(item);
          }
        }
      }
      catch (IOException e) {
        // ignore malformed member responses
      }
    }

    root.put("totalHits", Math.max(totalHits, data.size()));
    return toBytes(root);
  }

  private static byte[] toBytes(final com.fasterxml.jackson.databind.JsonNode node) {
    try {
      return MAPPER.writeValueAsBytes(node);
    }
    catch (IOException e) {
      return "{}".getBytes(StandardCharsets.UTF_8);
    }
  }
}
