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

import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.content.npm.NpmContentFacet;
import org.sonatype.nexus.content.npm.internal.NpmDistTagsManager;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.http.HttpResponses;
import org.sonatype.nexus.repository.npm.internal.NpmCoordinatesHelper;
import org.sonatype.nexus.repository.view.Context;
import org.sonatype.nexus.repository.view.Handler;
import org.sonatype.nexus.repository.view.Payload;
import org.sonatype.nexus.repository.view.Response;
import org.sonatype.nexus.repository.view.matchers.token.TokenMatcher;
import org.sonatype.nexus.repository.view.payloads.BytesPayload;
import org.sonatype.nexus.security.SecurityHelper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.io.ByteStreams;

import org.springframework.stereotype.Component;

import static com.google.common.base.Preconditions.checkState;
import static org.sonatype.nexus.repository.http.HttpMethods.DELETE;
import static org.sonatype.nexus.repository.http.HttpMethods.GET;
import static org.sonatype.nexus.repository.http.HttpMethods.HEAD;
import static org.sonatype.nexus.repository.http.HttpMethods.PUT;
import static org.sonatype.nexus.repository.view.ContentTypes.APPLICATION_JSON;
import static org.sonatype.nexus.repository.view.ContentTypes.APPLICATION_OCTET_STREAM;

/**
 * npm content handler. Understands the npm publish payload (packument plus embedded tarball
 * attachments) and serves packuments, version manifests, tarballs and dist-tags back to npm clients.
 */
@Component
@Singleton
public class NpmContentHandler
    extends ComponentSupport
    implements Handler
{
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final long MAX_DOCUMENT_SIZE = 64L * 1024L * 1024L;

  private static final String PACKAGE_PREFIX = "-/package/";

  private static final String DIST_TAGS_SUFFIX = "/dist-tags";

  private final NpmPackumentBuilder packumentBuilder;

  private final NpmDistTagsManager distTagsManager;

  private final SecurityHelper securityHelper;

  @Inject
  public NpmContentHandler(
      final NpmPackumentBuilder packumentBuilder,
      final NpmDistTagsManager distTagsManager,
      final SecurityHelper securityHelper)
  {
    this.packumentBuilder = packumentBuilder;
    this.distTagsManager = distTagsManager;
    this.securityHelper = securityHelper;
  }

  @Nonnull
  @Override
  public Response handle(@Nonnull final Context context) throws Exception {
    String path = NpmCoordinatesHelper.stripLeadingSlash(NpmCoordinatesHelper.decode(contentPath(context)));
    String method = context.getRequest().getAction();

    Repository repository = context.getRepository();
    log.debug("{} repository '{}' content-path: {}", method, repository.getName(), path);

    NpmContentFacet storage = repository.facet(NpmContentFacet.class);

    if (path.startsWith("-/")) {
      return handleRegistry(repository, storage, context, method, path);
    }

    switch (method) {
      case HEAD:
      case GET:
        return get(repository, storage, path);

      case PUT:
        return put(repository, storage, path, context.getRequest().getPayload());

      case DELETE:
        return delete(storage, path);

      default:
        return HttpResponses.methodNotAllowed(method, GET, HEAD, PUT, DELETE);
    }
  }

  private Response get(
      final Repository repository,
      final NpmContentFacet storage,
      final String path) throws Exception
  {
    if (isTarball(path)) {
      return storage.get(assetPath(path))
          .map(HttpResponses::ok)
          .orElseGet(() -> HttpResponses.notFound(path));
    }

    String manifestPackage = NpmCoordinatesHelper.getPackagePathForManifest(path);
    if (manifestPackage != null) {
      Optional<org.sonatype.nexus.repository.view.Content> manifest = storage.get(assetPath(path));
      if (manifest.isPresent()) {
        return HttpResponses.ok(manifest.get());
      }

      String version = path.substring(path.lastIndexOf('/') + 1);
      JsonNode packument = readPackument(repository, manifestPackage);
      JsonNode versionNode = packument != null ? packument.path("versions").get(version) : null;
      if (versionNode != null) {
        return jsonResponse(MAPPER.writeValueAsBytes(versionNode));
      }
      return HttpResponses.notFound(path);
    }

    byte[] packument = packumentBuilder.buildPackument(repository, path);
    if (packument != null) {
      return jsonResponse(packument);
    }

    // Legacy repositories stored the raw packument at /<package>; keep serving it.
    return storage.get(assetPath(path))
        .map(HttpResponses::ok)
        .orElseGet(() -> HttpResponses.notFound(path));
  }

  private Response put(
      final Repository repository,
      final NpmContentFacet storage,
      final String path,
      final Payload payload) throws Exception
  {
    if (isTarball(path)) {
      storage.put(assetPath(path), payload);
      return HttpResponses.created();
    }

    byte[] body = ByteStreams.toByteArray(payload.openInputStream());
    checkState(body.length <= MAX_DOCUMENT_SIZE, "npm document too large: %s bytes", body.length);

    JsonNode root = parse(body);
    if (root instanceof ObjectNode && root.has("_attachments")) {
      return publish(repository, storage, path, (ObjectNode) root);
    }

    String manifestPackage = NpmCoordinatesHelper.getPackagePathForManifest(path);
    if (manifestPackage != null) {
      String version = path.substring(path.lastIndexOf('/') + 1);
      storage.put(assetPath(path), new BytesPayload(body, APPLICATION_JSON), version);
    }
    else {
      String contentType = payload.getContentType() != null ? payload.getContentType() : APPLICATION_JSON;
      storage.put(assetPath(path), new BytesPayload(body, contentType));
    }
    return HttpResponses.created();
  }

  private Response publish(
      final Repository repository,
      final NpmContentFacet storage,
      final String packagePath,
      final ObjectNode packument) throws Exception
  {
    JsonNode attachments = packument.remove("_attachments");
    JsonNode versions = packument.path("versions");
    Set<String> publishedVersions = new LinkedHashSet<>();

    if (versions.isObject()) {
      Iterator<Map.Entry<String, JsonNode>> fields = versions.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> entry = fields.next();
        String version = entry.getKey();
        if (!entry.getValue().isObject()) {
          continue;
        }
        ObjectNode manifest = ((ObjectNode) entry.getValue()).deepCopy();
        manifest.put("_id", packagePath + "@" + version);
        manifest.put("name", packagePath);
        manifest.put("version", version);
        storage.put(assetPath(packagePath + "/" + version),
            new BytesPayload(MAPPER.writeValueAsBytes(manifest), APPLICATION_JSON), version);
        publishedVersions.add(version);
      }
    }

    if (attachments != null && attachments.isObject()) {
      Iterator<Map.Entry<String, JsonNode>> fields = attachments.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> attachment = fields.next();
        JsonNode data = attachment.getValue().get("data");
        if (data == null || !data.isTextual()) {
          continue;
        }

        String tarballName = attachment.getKey();
        int slash = tarballName.lastIndexOf('/');
        if (slash >= 0) {
          tarballName = tarballName.substring(slash + 1);
        }
        String version = NpmCoordinatesHelper.getVersionFromTarballName(tarballName);
        if (version == null) {
          continue;
        }

        byte[] tarball = Base64.getMimeDecoder().decode(data.asText());
        storage.put(assetPath(packagePath + "/-/" + tarballName), new BytesPayload(tarball, APPLICATION_OCTET_STREAM),
            version);
        publishedVersions.add(version);
      }
    }

    if (publishedVersions.isEmpty()) {
      return HttpResponses.badRequest("No versions or tarballs found in npm publish payload");
    }

    Map<String, String> tags = new LinkedHashMap<>();
    JsonNode distTags = packument.path("dist-tags");
    if (distTags.isObject()) {
      distTags.fields().forEachRemaining(entry -> tags.put(entry.getKey(), entry.getValue().asText()));
    }
    for (Map.Entry<String, String> entry : tags.entrySet()) {
      distTagsManager.set(repository, packagePath, entry.getKey(), entry.getValue());
    }
    // When the client does not send "latest" (for example "npm publish --tag beta") the tag getter
    // derives it from the highest stable version, so a prerelease never silently replaces latest.

    return HttpResponses.created();
  }

  private Response delete(final NpmContentFacet storage, final String path) throws Exception {
    int revision = path.lastIndexOf("/-rev/");
    if (revision >= 0) {
      String packagePath = path.substring(0, revision);
      storage.deletePackage(packagePath);
      return HttpResponses.noContent();
    }

    boolean deleted = storage.delete(assetPath(path));
    if (deleted) {
      return HttpResponses.noContent();
    }
    return HttpResponses.notFound(path);
  }

  private Response handleRegistry(
      final Repository repository,
      final NpmContentFacet storage,
      final Context context,
      final String method,
      final String path) throws Exception
  {
    if (path.equals("-/whoami") && (GET.equals(method) || HEAD.equals(method))) {
      ObjectNode result = MAPPER.createObjectNode();
      result.put("username", String.valueOf(securityHelper.subject().getPrincipal()));
      return jsonResponse(MAPPER.writeValueAsBytes(result));
    }

    if (path.equals("-/ping") && GET.equals(method)) {
      return jsonResponse("{}".getBytes());
    }

    if (!path.startsWith(PACKAGE_PREFIX)) {
      return HttpResponses.notFound(path);
    }

    String rest = path.substring(PACKAGE_PREFIX.length());
    int tagsIndex = rest.lastIndexOf(DIST_TAGS_SUFFIX);
    if (tagsIndex < 0) {
      return HttpResponses.notFound(path);
    }

    String packagePath = rest.substring(0, tagsIndex);
    String tag = rest.substring(tagsIndex + DIST_TAGS_SUFFIX.length());
    if (tag.startsWith("/")) {
      tag = tag.substring(1);
    }

    if (tag.isEmpty()) {
      if (!GET.equals(method) && !HEAD.equals(method)) {
        return HttpResponses.methodNotAllowed(method, GET, HEAD);
      }
      Map<String, String> tags = new LinkedHashMap<>(distTagsManager.get(repository, packagePath));
      if (!tags.containsKey("latest")) {
        JsonNode packument = readPackument(repository, packagePath);
        if (packument != null && packument.path("dist-tags").isObject()) {
          packument.path("dist-tags").fields()
              .forEachRemaining(entry -> tags.putIfAbsent(entry.getKey(), entry.getValue().asText()));
        }
      }
      return jsonResponse(MAPPER.writeValueAsBytes(tags));
    }

    if (PUT.equals(method)) {
      byte[] body = ByteStreams.toByteArray(context.getRequest().getPayload().openInputStream());
      String version = parseTagVersion(body);
      if (version == null || !distTagsManager.set(repository, packagePath, tag, version)) {
        return HttpResponses.notFound("Unknown version: " + version);
      }
      return HttpResponses.created();
    }

    if (DELETE.equals(method)) {
      distTagsManager.delete(repository, packagePath, tag);
      return HttpResponses.noContent();
    }

    return HttpResponses.methodNotAllowed(method, GET, HEAD, PUT, DELETE);
  }

  private static String parseTagVersion(final byte[] body) {
    try {
      JsonNode node = MAPPER.readTree(body);
      if (node != null && node.isTextual()) {
        return node.asText();
      }
      if (node != null && node.isObject() && node.has("version")) {
        return node.path("version").asText();
      }
    }
    catch (Exception e) {
      // fall through to plain text parsing
    }
    String value = new String(body, java.nio.charset.StandardCharsets.UTF_8).trim();
    if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 1) {
      value = value.substring(1, value.length() - 1);
    }
    return value.isEmpty() ? null : value;
  }

  private JsonNode readPackument(final Repository repository, final String packagePath) {
    try {
      byte[] bytes = packumentBuilder.buildPackument(repository, packagePath);
      return bytes != null ? MAPPER.readTree(bytes) : null;
    }
    catch (Exception e) {
      log.debug("Could not build packument for {}", packagePath, e);
      return null;
    }
  }

  private static Response jsonResponse(final byte[] bytes) {
    return HttpResponses.ok(new BytesPayload(bytes, APPLICATION_JSON));
  }

  private static String assetPath(final String path) {
    return path.startsWith("/") ? path : "/" + path;
  }

  private static boolean isTarball(final String path) {
    return path.contains("/-/") && (path.endsWith(".tgz") || path.endsWith(".tar.gz"));
  }

  private static JsonNode parse(final byte[] body) {
    try {
      return MAPPER.readTree(body);
    }
    catch (Exception e) {
      return null;
    }
  }

  @Nonnull
  private String contentPath(final Context context) {
    TokenMatcher.State state = context.getAttributes().require(TokenMatcher.State.class);
    String path = state.getTokens().get(NpmRecipeSupport.PATH_NAME);
    checkState(path != null, "Missing token: path");

    return path;
  }
}
