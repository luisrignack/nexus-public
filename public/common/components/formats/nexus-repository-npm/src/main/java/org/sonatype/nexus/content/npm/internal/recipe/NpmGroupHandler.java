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
package org.sonatype.nexus.content.npm.internal.recipe;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import jakarta.inject.Singleton;

import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.group.GroupFacet;
import org.sonatype.nexus.repository.group.GroupHandler;
import org.sonatype.nexus.repository.http.HttpResponses;
import org.sonatype.nexus.repository.npm.internal.NpmCoordinatesHelper;
import org.sonatype.nexus.repository.view.Context;
import org.sonatype.nexus.repository.view.Payload;
import org.sonatype.nexus.repository.view.Response;
import org.sonatype.nexus.repository.view.matchers.token.TokenMatcher;
import org.sonatype.nexus.repository.view.payloads.BytesPayload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.io.ByteStreams;
import org.springframework.stereotype.Component;

import static org.sonatype.nexus.repository.view.ContentTypes.APPLICATION_JSON;

/**
 * npm group handler.
 *
 * <p>Packuments and dist-tags are merged across members, preserving member order: a version or tag already
 * provided by an earlier member is not overridden. Every other path (manifests, tarballs, registry endpoints)
 * is served by the first member that answers successfully.</p>
 */
@Component
@Singleton
public class NpmGroupHandler
    extends GroupHandler
{
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String PACKAGE_PREFIX = "-/package/";

  private static final String DIST_TAGS_SUFFIX = "/dist-tags";

  @Nonnull
  @Override
  protected Response doGet(
      @Nonnull final Context context,
      @Nonnull final DispatchedRepositories dispatched) throws Exception
  {
    String path = NpmCoordinatesHelper.stripLeadingSlash(NpmCoordinatesHelper.decode(contentPath(context)));

    if (isPackument(path)) {
      return mergedPackument(context, dispatched);
    }
    if (isDistTags(path)) {
      return mergedDistTags(context, dispatched);
    }

    GroupFacet groupFacet = context.getRepository().facet(GroupFacet.class);
    return getFirst(context, groupFacet.members(), dispatched);
  }

  private Response mergedPackument(
      final Context context,
      final DispatchedRepositories dispatched) throws Exception
  {
    GroupFacet groupFacet = context.getRepository().facet(GroupFacet.class);

    ObjectNode merged = null;
    ObjectNode versions = MAPPER.createObjectNode();
    ObjectNode tags = MAPPER.createObjectNode();

    for (Map.Entry<Repository, Response> entry : getAll(context, groupFacet.members(), dispatched).entrySet()) {
      JsonNode document = readDocument(entry.getValue());
      if (!(document instanceof ObjectNode)) {
        continue;
      }

      ObjectNode packument = (ObjectNode) document;
      if (merged == null) {
        merged = packument.deepCopy();
      }

      JsonNode memberVersions = packument.path("versions");
      if (memberVersions.isObject()) {
        Iterator<Map.Entry<String, JsonNode>> fields = memberVersions.fields();
        while (fields.hasNext()) {
          Map.Entry<String, JsonNode> field = fields.next();
          if (!versions.has(field.getKey())) {
            versions.set(field.getKey(), field.getValue());
          }
        }
      }

      JsonNode memberTags = packument.path("dist-tags");
      if (memberTags.isObject()) {
        Iterator<Map.Entry<String, JsonNode>> fields = memberTags.fields();
        while (fields.hasNext()) {
          Map.Entry<String, JsonNode> field = fields.next();
          if (!tags.has(field.getKey())) {
            tags.set(field.getKey(), field.getValue());
          }
        }
      }
    }

    if (merged == null) {
      return notFoundResponse(context);
    }

    if (!tags.has("latest") && versions.size() > 0) {
      List<String> published = new ArrayList<>(versions.size());
      versions.fieldNames().forEachRemaining(published::add);
      String latest = NpmCoordinatesHelper.highestStableVersion(published);
      if (latest == null) {
        latest = NpmCoordinatesHelper.highestVersion(published);
      }
      if (latest != null) {
        tags.put("latest", latest);
      }
    }

    merged.set("versions", versions);
    merged.set("dist-tags", tags);
    rewriteTarballs(merged, context.getRepository().getUrl());

    return jsonResponse(MAPPER.writeValueAsBytes(merged));
  }

  private Response mergedDistTags(
      final Context context,
      final DispatchedRepositories dispatched) throws Exception
  {
    GroupFacet groupFacet = context.getRepository().facet(GroupFacet.class);

    ObjectNode tags = MAPPER.createObjectNode();
    boolean found = false;

    for (Map.Entry<Repository, Response> entry : getAll(context, groupFacet.members(), dispatched).entrySet()) {
      JsonNode document = readDocument(entry.getValue());
      if (!(document instanceof ObjectNode)) {
        continue;
      }
      found = true;
      Iterator<Map.Entry<String, JsonNode>> fields = document.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        if (!tags.has(field.getKey())) {
          tags.set(field.getKey(), field.getValue());
        }
      }
    }

    if (!found) {
      return notFoundResponse(context);
    }
    return jsonResponse(MAPPER.writeValueAsBytes(tags));
  }

  /**
   * Points tarball downloads at the group repository rather than at the member that supplied the version.
   */
  private static void rewriteTarballs(final ObjectNode packument, final String baseUrl) {
    JsonNode versions = packument.path("versions");
    if (!versions.isObject()) {
      return;
    }

    Iterator<Map.Entry<String, JsonNode>> entries = versions.fields();
    while (entries.hasNext()) {
      JsonNode version = entries.next().getValue();
      JsonNode dist = version.path("dist");
      if (!dist.isObject()) {
        continue;
      }
      JsonNode tarball = dist.get("tarball");
      if (tarball == null || !tarball.isTextual()) {
        continue;
      }
      try {
        String tarballPath = java.net.URI.create(tarball.asText()).getPath();
        if (tarballPath != null && !tarballPath.isEmpty()) {
          // Member responses advertise tarballs on the member repository, for example
          // /repository/npm-proxy/is-number/-/is-number-7.0.0.tgz. Keep only the format path.
          int repository = tarballPath.indexOf("/repository/");
          if (repository >= 0) {
            String rest = tarballPath.substring(repository + "/repository/".length());
            int slash = rest.indexOf('/');
            if (slash >= 0) {
              tarballPath = rest.substring(slash);
            }
          }
          ((ObjectNode) dist).put("tarball",
              baseUrl + (tarballPath.startsWith("/") ? "" : "/") + tarballPath);
        }
      }
      catch (IllegalArgumentException e) {
        // Leave malformed URLs untouched.
      }
    }
  }

  private static JsonNode readDocument(final Response response) {
    if (!response.getStatus().isSuccessful()) {
      return null;
    }
    Payload payload = response.getPayload();
    if (payload == null) {
      return null;
    }
    try (InputStream input = payload.openInputStream()) {
      return MAPPER.readTree(ByteStreams.toByteArray(input));
    }
    catch (IOException e) {
      return null;
    }
  }

  private static Response jsonResponse(final byte[] bytes) {
    return HttpResponses.ok(new BytesPayload(bytes, APPLICATION_JSON));
  }

  private static boolean isPackument(final String path) {
    if (path.isEmpty() || path.startsWith("-/")) {
      return false;
    }
    if (path.startsWith("@")) {
      String[] segments = path.split("/");
      return segments.length == 2 && !segments[1].isEmpty();
    }
    return path.indexOf('/') < 0;
  }

  private static boolean isDistTags(final String path) {
    return path.startsWith(PACKAGE_PREFIX) && path.endsWith(DIST_TAGS_SUFFIX);
  }

  private static String contentPath(final Context context) {
    TokenMatcher.State state = context.getAttributes().require(TokenMatcher.State.class);
    return state.getTokens().get(NpmRecipeSupport.PATH_NAME);
  }
}
