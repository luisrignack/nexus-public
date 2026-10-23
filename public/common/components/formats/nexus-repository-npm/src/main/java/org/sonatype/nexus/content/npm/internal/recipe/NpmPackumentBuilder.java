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
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.repository.content.AssetBlob;
import org.sonatype.nexus.content.npm.NpmContentFacet;
import org.sonatype.nexus.content.npm.internal.NpmDistTagsManager;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.content.facet.ContentFacet;
import org.sonatype.nexus.repository.content.fluent.FluentAsset;
import org.sonatype.nexus.repository.content.fluent.FluentComponent;
import org.sonatype.nexus.repository.npm.internal.NpmCoordinatesHelper;
import org.sonatype.nexus.repository.view.Content;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteStreams;

import static org.sonatype.nexus.repository.npm.internal.NpmCoordinatesHelper.decode;

/**
 * Builds an npm packument from the components and manifests stored in a repository.
 *
 * <p>Manifests are stored once per version at {@code <package>/<version>}, tarballs at
 * {@code <package>/-/<name>-<version>.tgz} and dist-tags as component attributes. Old repositories that
 * still have a raw packument stored at {@code <package>} are merged in as a fallback.</p>
 */
@Named
@Singleton
public class NpmPackumentBuilder
    extends ComponentSupport
{
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final NpmDistTagsManager distTagsManager;

  @Inject
  public NpmPackumentBuilder(final NpmDistTagsManager distTagsManager) {
    this.distTagsManager = distTagsManager;
  }

  /**
   * Returns the packument for the given package, or {@code null} when the package has no content.
   */
  public byte[] buildPackument(final Repository repository, final String packagePath) throws IOException {
    ContentFacet contentFacet = repository.facet(ContentFacet.class);
    List<FluentComponent> components = components(contentFacet, packagePath);

    JsonNode storedPackument = readStoredPackument(repository, packagePath);
    ObjectNode storedVersions = storedPackument != null && storedPackument.path("versions").isObject()
        ? (ObjectNode) storedPackument.path("versions")
        : MAPPER.createObjectNode();

    ObjectNode root = MAPPER.createObjectNode();
    root.put("_id", packagePath);
    root.put("name", packagePath);

    if (storedPackument != null) {
      copyIfPresent(storedPackument, root, "description");
      copyIfPresent(storedPackument, root, "homepage");
      copyIfPresent(storedPackument, root, "keywords");
      copyIfPresent(storedPackument, root, "license");
      copyIfPresent(storedPackument, root, "repository");
      copyIfPresent(storedPackument, root, "readme");
    }

    ObjectNode versions = root.putObject("versions");
    ObjectNode time = root.putObject("time");

    for (FluentComponent component : components) {
      String version = component.version();
      if (version == null || version.isEmpty()) {
        continue;
      }

      ObjectNode versionNode = readManifest(contentFacet, packagePath, version);
      if (versionNode == null && storedVersions.has(version) && storedVersions.get(version).isObject()) {
        versionNode = ((ObjectNode) storedVersions.get(version)).deepCopy();
      }
      if (versionNode == null) {
        versionNode = MAPPER.createObjectNode();
        versionNode.put("name", packagePath);
        versionNode.put("version", version);
      }

      versionNode.put("_id", packagePath + "@" + version);
      versionNode.put("name", packagePath);
      versionNode.put("version", version);
      applyDist(repository, packagePath, version, component, versionNode);

      versions.set(version, versionNode);
      time.put(version, component.created() != null ? component.created().toString() : "");
    }

    // Keep versions that predate the component/manifest layout so old data remains installable.
    storedVersions.fields().forEachRemaining(entry -> {
      if (!versions.has(entry.getKey()) && entry.getValue().isObject()) {
        ObjectNode versionNode = ((ObjectNode) entry.getValue()).deepCopy();
        versionNode.put("_id", packagePath + "@" + entry.getKey());
        versions.set(entry.getKey(), versionNode);
      }
    });

    if (versions.isEmpty()) {
      return null;
    }

    // npm unpublish reads the packument revision and sends it back in DELETE /<pkg>/-rev/<rev>.
    root.put("_rev", "1-" + Integer.toHexString(versions.size()));

    Map<String, String> tags = new LinkedHashMap<>();
    if (storedPackument != null && storedPackument.path("dist-tags").isObject()) {
      storedPackument.path("dist-tags").fields()
          .forEachRemaining(entry -> tags.put(entry.getKey(), entry.getValue().asText()));
    }
    tags.putAll(distTagsManager.get(repository, packagePath));
    if (!tags.containsKey("latest")) {
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

    ObjectNode distTags = root.putObject("dist-tags");
    tags.forEach(distTags::put);

    if (storedPackument != null && storedPackument.path("time").isObject()) {
      storedPackument.path("time").fields().forEachRemaining(entry -> {
        if (!time.has(entry.getKey())) {
          time.put(entry.getKey(), entry.getValue().asText());
        }
      });
    }
    if (!time.has("modified") || time.path("modified").asText().isEmpty()) {
      time.put("modified", java.time.OffsetDateTime.now().toString());
    }

    ObjectNode maintainers = root.putArray("maintainers").addObject();
    maintainers.put("name", "nexus");
    maintainers.put("email", "nexus@localhost");

    return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(root);
  }

  private JsonNode readStoredPackument(final Repository repository, final String packagePath) {
    try {
      NpmContentFacet facet = repository.facet(NpmContentFacet.class);
      Optional<Content> content = facet.get(assetPath(packagePath));
      if (content.isPresent()) {
        byte[] bytes = ByteStreams.toByteArray(content.get().openInputStream());
        return MAPPER.readTree(bytes);
      }
    }
    catch (Exception e) {
      log.debug("Could not read stored packument for {}", packagePath, e);
    }
    return null;
  }

  private ObjectNode readManifest(
      final ContentFacet contentFacet,
      final String packagePath,
      final String version)
  {
    Optional<FluentAsset> asset = contentFacet.assets()
        .path(assetPath(packagePath + "/" + version))
        .find();

    if (!asset.isPresent()) {
      asset = contentFacet.assets().path(assetPath(packagePath + "/" + version + "/package.json")).find();
    }

    if (asset.isPresent()) {
      try {
        byte[] bytes = ByteStreams.toByteArray(asset.get().download().openInputStream());
        JsonNode parsed = MAPPER.readTree(bytes);
        if (parsed instanceof ObjectNode) {
          return (ObjectNode) parsed;
        }
      }
      catch (Exception e) {
        log.debug("Could not read manifest for {}@{}", packagePath, version, e);
      }
    }
    return null;
  }

  private void applyDist(
      final Repository repository,
      final String packagePath,
      final String version,
      final FluentComponent component,
      final ObjectNode versionNode)
  {
    FluentAsset tarballAsset = null;
    for (FluentAsset asset : component.assets()) {
      String assetPath = decode(asset.path());
      if (assetPath.contains("/-/") && (assetPath.endsWith(".tgz") || assetPath.endsWith(".tar.gz"))) {
        tarballAsset = asset;
        break;
      }
    }

    JsonNode existingDist = versionNode.get("dist");
    ObjectNode dist = existingDist != null && existingDist.isObject()
        ? (ObjectNode) existingDist
        : versionNode.putObject("dist");
    if (tarballAsset != null) {
      String tarballName = tarballAsset.path().substring(tarballAsset.path().lastIndexOf('/') + 1);
      String baseUrl = repository.getUrl();
      dist.put("tarball", baseUrl + "/" + packagePath + "/-/" + tarballName);
      final String tarballPath = tarballAsset.path();
      tarballAsset.blob().map(AssetBlob::checksums).ifPresent(checksums -> {
        String sha1 = checksums.get("sha1");
        if (sha1 != null && !dist.has("shasum")) {
          dist.put("shasum", sha1);
        }
        String sha512 = checksums.get("sha512");
        if (sha512 != null && !dist.has("integrity")) {
          try {
            dist.put("integrity", "sha512-" + Base64.getEncoder().encodeToString(BaseEncoding.base16().decode(
                sha512.toUpperCase(java.util.Locale.ROOT))));
          }
          catch (Exception e) {
            log.debug("Could not encode sha512 for {}", tarballPath, e);
          }
        }
      });
    }
    else if (!dist.has("tarball")) {
      dist.put("tarball", repository.getUrl() + "/" + packagePath + "/-/" + tarballFileName(packagePath, version));
    }
  }

  private static String assetPath(final String path) {
    return path.startsWith("/") ? path : "/" + path;
  }

  private static List<FluentComponent> components(final ContentFacet contentFacet, final String packagePath) {
    String namespace = NpmCoordinatesHelper.getComponentGroup(packagePath);
    String name = NpmCoordinatesHelper.getComponentName(packagePath);
    List<FluentComponent> components = new ArrayList<>();
    for (String version : contentFacet.components().versions(namespace, name)) {
      contentFacet.components().name(name).namespace(namespace).version(version).find().ifPresent(components::add);
    }
    return components;
  }

  private static String tarballFileName(final String packagePath, final String version) {
    String name = NpmCoordinatesHelper.getComponentName(packagePath);
    return name + "-" + version + ".tgz";
  }

  private static void copyIfPresent(final JsonNode source, final ObjectNode target, final String field) {
    JsonNode value = source.get(field);
    if (value != null && !value.isNull()) {
      target.set(field, value);
    }
  }

  /**
   * Returns the versions known to the packument builder, used by tests.
   */
  static Collection<String> versions(final JsonNode packument) {
    List<String> versions = new ArrayList<>();
    if (packument != null) {
      packument.path("versions").fieldNames().forEachRemaining(versions::add);
    }
    return versions;
  }
}
