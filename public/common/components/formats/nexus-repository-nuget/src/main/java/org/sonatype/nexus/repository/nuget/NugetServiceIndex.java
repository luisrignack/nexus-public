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
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import javax.annotation.Nullable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Builds the NuGet v3 service index and rewrites URLs of upstream documents so they point back at the local
 * repository.
 */
public final class NugetServiceIndex
{
  private static final String SEARCH_SERVICE_TYPE = "SearchQueryService";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private NugetServiceIndex() {
    // no instances
  }

  /**
   * Service index of a v3 repository (hosted or group). Every URL points back at the given repository base.
   */
  public static byte[] build(final String baseUrl) {
    String base = NugetCoordinatesHelper.ensureTrailingSlash(baseUrl);
    ObjectNode root = MAPPER.createObjectNode();
    root.put("version", "3.0.0");
    ArrayNode resources = root.putArray("resources");
    addResource(resources, base + NugetCoordinatesHelper.FLAT_CONTAINER + "/", "PackageBaseAddress/3.0.0");
    addResource(resources, base + NugetCoordinatesHelper.REGISTRATION + "/", "RegistrationsBaseUrl/3.6.0");
    addResource(resources, base + NugetCoordinatesHelper.QUERY, "SearchQueryService/3.5.0");
    addResource(resources, base + NugetCoordinatesHelper.API_V2_PACKAGE, "PackagePublish/2.0.0");
    return toBytes(root);
  }

  /**
   * Service index served by a proxy configured as a v2 feed; only the endpoints that are proxied verbatim are
   * advertised (see README for the limitations of the v2 mode).
   */
  public static byte[] buildV2(final String baseUrl) {
    String base = NugetCoordinatesHelper.ensureTrailingSlash(baseUrl);
    ObjectNode root = MAPPER.createObjectNode();
    root.put("version", "3.0.0");
    ArrayNode resources = root.putArray("resources");
    addResource(resources, base + "Packages()", "SearchQueryService/2.0.0");
    addResource(resources, base + "FindPackagesById()", "RegistrationsBaseUrl/2.0.0");
    addResource(resources, base + "Download/", "PackageBaseAddress/2.0.0");
    addResource(resources, base + NugetCoordinatesHelper.API_V2_PACKAGE, "PackagePublish/2.0.0");
    return toBytes(root);
  }

  private static void addResource(final ArrayNode resources, final String id, final String type) {
    ObjectNode resource = resources.addObject();
    resource.put("@id", id);
    resource.put("@type", type);
  }

  /**
   * Rewrites every absolute URL found in {@code json} that starts with {@code remoteBase} so it points at
   * {@code localBase} instead. When the remote base lives on a different origin than the local repository, every
   * URL of that origin is rewritten as well: upstream feeds spread their resources over paths (and sometimes
   * hosts) that would not match the configured remote URL, and everything served by the upstream origin is
   * proxied back through this repository anyway.
   *
   * <p>Used by the proxy to make the service index and registration documents refer to this repository, and by
   * groups to make member documents refer to the group.</p>
   *
   * @return the rewritten JSON document, or the original text when it is not valid JSON
   */
  public static String rewriteUrls(final String json, final String remoteBase, final String localBase) {
    if (json == null || json.isEmpty()) {
      return json;
    }
    String remote = NugetCoordinatesHelper.stripTrailingSlash(remoteBase);
    String local = NugetCoordinatesHelper.ensureTrailingSlash(localBase);
    if (remote.isEmpty() || local.isEmpty() || remote.equals(NugetCoordinatesHelper.stripTrailingSlash(local))) {
      return json;
    }

    JsonNode root;
    try {
      root = MAPPER.readTree(json);
    }
    catch (IOException e) {
      return json; // not JSON (for instance an error page); pass through untouched
    }
    if (root == null) {
      return json;
    }

    String remoteOrigin = origin(remote);
    String localOrigin = origin(NugetCoordinatesHelper.stripTrailingSlash(local));
    boolean rewriteByOrigin = remoteOrigin != null && !remoteOrigin.equals(localOrigin);

    rewrite(root, remote, remoteOrigin, rewriteByOrigin, local);
    try {
      return MAPPER.writeValueAsString(root);
    }
    catch (IOException e) {
      return json;
    }
  }

  /**
   * Points every {@code SearchQueryService} resource of a service index at the local {@code v3/query} endpoint, so
   * that searches issued by clients are answered by this repository (and merged by groups) instead of being sent
   * to a search host that lives on another origin.
   *
   * @return the document with localized search resources, or the original text when it is not valid JSON
   */
  public static String localizeSearchResources(final String json, final String localBase) {
    if (json == null || json.isEmpty()) {
      return json;
    }
    String local = NugetCoordinatesHelper.stripTrailingSlash(localBase);
    JsonNode root;
    try {
      root = MAPPER.readTree(json);
    }
    catch (IOException e) {
      return json;
    }
    if (root == null || !root.isObject()) {
      return json;
    }

    JsonNode resources = root.get("resources");
    if (resources == null || !resources.isArray()) {
      return json;
    }

    String localOrigin = origin(local);
    boolean changed = false;
    for (JsonNode resource : resources) {
      if (!resource.isObject()) {
        continue;
      }
      JsonNode type = resource.get("@type");
      JsonNode id = resource.get("@id");
      if (type == null || id == null || !type.isTextual() || !id.isTextual()) {
        continue;
      }
      if (!type.asText().startsWith(SEARCH_SERVICE_TYPE)) {
        continue;
      }
      String value = id.asText();
      String valueOrigin = origin(NugetCoordinatesHelper.stripTrailingSlash(value));
      if (valueOrigin != null && valueOrigin.equals(localOrigin)) {
        continue; // already local
      }
      ((ObjectNode) resource).put("@id", local + "/" + NugetCoordinatesHelper.QUERY);
      changed = true;
    }

    if (!changed) {
      return json;
    }
    try {
      return MAPPER.writeValueAsString(root);
    }
    catch (IOException e) {
      return json;
    }
  }

  /**
   * The upstream {@code SearchQueryService} URL of a service index, or {@code null} when it has none.
   */
  @Nullable
  public static String searchServiceUrl(final String json) {
    if (json == null || json.isEmpty()) {
      return null;
    }
    JsonNode root;
    try {
      root = MAPPER.readTree(json);
    }
    catch (IOException e) {
      return null;
    }
    if (root == null || !root.isObject()) {
      return null;
    }
    JsonNode resources = root.get("resources");
    if (resources == null || !resources.isArray()) {
      return null;
    }
    for (JsonNode resource : resources) {
      JsonNode type = resource.get("@type");
      JsonNode id = resource.get("@id");
      if (type != null && id != null && type.isTextual() && id.isTextual()
          && type.asText().startsWith(SEARCH_SERVICE_TYPE))
      {
        return id.asText();
      }
    }
    return null;
  }

  /**
   * {@code scheme://authority} of an absolute URL, or {@code null} when it cannot be determined.
   */
  @Nullable
  private static String origin(final String url) {
    if (url == null || url.isEmpty()) {
      return null;
    }
    try {
      URI uri = new URI(url);
      if (uri.getScheme() == null || uri.getAuthority() == null) {
        return null;
      }
      return uri.getScheme().toLowerCase(Locale.ROOT) + "://" + uri.getAuthority().toLowerCase(Locale.ROOT);
    }
    catch (URISyntaxException e) {
      return null;
    }
  }

  private static void rewrite(
      final JsonNode node,
      final String remote,
      final String remoteOrigin,
      final boolean rewriteByOrigin,
      final String local)
  {
    if (node.isObject()) {
      ObjectNode object = (ObjectNode) node;
      object.fields().forEachRemaining(field -> {
        JsonNode value = field.getValue();
        if (value.isTextual()) {
          String rewritten = rewriteValue(value.asText(), remote, remoteOrigin, rewriteByOrigin, local);
          if (!rewritten.equals(value.asText())) {
            object.put(field.getKey(), rewritten);
          }
        }
        else {
          rewrite(value, remote, remoteOrigin, rewriteByOrigin, local);
        }
      });
    }
    else if (node.isArray()) {
      ArrayNode array = (ArrayNode) node;
      for (int i = 0; i < array.size(); i++) {
        JsonNode value = array.get(i);
        if (value.isTextual()) {
          String rewritten = rewriteValue(value.asText(), remote, remoteOrigin, rewriteByOrigin, local);
          if (!rewritten.equals(value.asText())) {
            array.set(i, MAPPER.getNodeFactory().textNode(rewritten));
          }
        }
        else {
          rewrite(value, remote, remoteOrigin, rewriteByOrigin, local);
        }
      }
    }
  }

  private static String rewriteValue(
      final String value,
      final String remote,
      final String remoteOrigin,
      final boolean rewriteByOrigin,
      final String local)
  {
    if (value.equals(remote)) {
      return NugetCoordinatesHelper.stripTrailingSlash(local);
    }
    if (value.startsWith(remote + "/") || value.startsWith(remote + "?") || value.startsWith(remote + "#")) {
      return join(local, value.substring(remote.length()));
    }
    if (rewriteByOrigin && remoteOrigin != null
        && (value.equals(remoteOrigin) || value.startsWith(remoteOrigin + "/")))
    {
      return join(local, value.substring(remoteOrigin.length()));
    }
    return value;
  }

  /**
   * Appends a path, query or fragment suffix to the local base URL (the suffix keeps its leading separator).
   */
  private static String join(final String local, final String suffix) {
    String base = NugetCoordinatesHelper.stripTrailingSlash(local);
    if (suffix.isEmpty() || suffix.startsWith("/") || suffix.startsWith("?") || suffix.startsWith("#")) {
      return base + suffix;
    }
    return base + "/" + suffix;
  }

  private static byte[] toBytes(final JsonNode node) {
    try {
      return MAPPER.writeValueAsBytes(node);
    }
    catch (IOException e) {
      return "{}".getBytes(StandardCharsets.UTF_8);
    }
  }
}
