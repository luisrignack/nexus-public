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

import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import jakarta.inject.Inject;

import org.sonatype.nexus.common.entity.Continuation;
import org.sonatype.nexus.content.npm.NpmContentFacet;
import org.sonatype.nexus.content.npm.internal.NpmDistTagsManager;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.RepositoryTaskSupport;
import org.sonatype.nexus.repository.content.facet.ContentFacet;
import org.sonatype.nexus.repository.content.fluent.FluentAsset;
import org.sonatype.nexus.repository.content.fluent.FluentComponent;
import org.sonatype.nexus.repository.npm.internal.NpmCoordinatesHelper;
import org.sonatype.nexus.repository.view.payloads.BytesPayload;
import org.sonatype.nexus.scheduling.Cancelable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.ByteStreams;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import static org.sonatype.nexus.repository.view.ContentTypes.APPLICATION_JSON;

/**
 * Rebuilds the metadata of an npm repository.
 *
 * <p>For every component the task makes sure that a version manifest exists (recovering it from the legacy
 * packument or, as a last resort, from {@code package/package.json} inside the tarball), refreshes the
 * {@code npm.*} attributes used by search/browse, and finally repairs the dist-tags removing references to
 * versions that no longer exist and recalculating {@code latest} when needed.</p>
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class NpmRebuildMetadataTask
    extends RepositoryTaskSupport
    implements Cancelable
{
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private NpmDistTagsManager distTagsManager;

  @Autowired
  public NpmRebuildMetadataTask() {
  }

  @Inject
  public void setDistTagsManager(final NpmDistTagsManager distTagsManager) {
    this.distTagsManager = distTagsManager;
  }

  @Override
  public String getMessage() {
    return "Rebuilding npm metadata for " + getRepositoryField();
  }

  @Override
  protected void execute(final Repository repository) {
    ContentFacet contentFacet = repository.facet(ContentFacet.class);
    NpmContentFacet npmContentFacet = repository.facet(NpmContentFacet.class);

    Set<String> packages = new LinkedHashSet<>();
    int rebuilt = 0;
    int missing = 0;

    String token = null;
    do {
      Continuation<FluentComponent> page = contentFacet.components().browse(100, token);
      for (FluentComponent component : page) {
        if (isCanceled()) {
          return;
        }

        String version = component.version();
        if (version == null || version.isEmpty()) {
          continue;
        }
        if (isLegacyManifestComponent(component)) {
          log.debug("Skipping legacy version manifest component {}@{}", component.name(), version);
          continue;
        }

        String packagePath = packagePath(component);
        packages.add(packagePath);

        String manifestPath = "/" + packagePath + "/" + version;
        Optional<FluentAsset> manifestAsset = contentFacet.assets().path(manifestPath).find();
        JsonNode manifest = manifestAsset.map(NpmRebuildMetadataTask::readJson).orElse(null);

        if (manifest == null) {
          manifest = storedManifest(repository, packagePath, version);
        }
        if (manifest == null) {
          manifest = tarballManifest(component);
        }

        if (manifest == null) {
          missing++;
          log.debug("No metadata found for {}@{}", packagePath, version);
          continue;
        }

        if (!manifestAsset.isPresent()) {
          try {
            npmContentFacet.put(manifestPath,
                new BytesPayload(MAPPER.writeValueAsBytes(manifest), APPLICATION_JSON), version);
          }
          catch (Exception e) {
            log.warn("Could not recreate manifest for {}@{}", packagePath, version, e);
            continue;
          }
        }

        NpmComponentAttributes.apply(repository, packagePath, version, manifest);
        rebuilt++;
      }
      token = nextToken(page);
    }
    while (token != null);

    for (String packagePath : packages) {
      distTagsManager.repair(repository, packagePath);
    }

    log.info("Rebuilt npm metadata for repository '{}': {} versions updated, {} versions without metadata",
        repository.getName(), rebuilt, missing);
  }

  @Override
  protected boolean appliesTo(final Repository repository) {
    return repository != null && repository.optionalFacet(NpmContentFacet.class).isPresent();
  }

  private JsonNode storedManifest(final Repository repository, final String packagePath, final String version) {
    try {
      Optional<org.sonatype.nexus.repository.view.Content> stored =
          repository.facet(NpmContentFacet.class).get("/" + packagePath);
      if (stored.isPresent()) {
        try (InputStream input = stored.get().openInputStream()) {
          JsonNode packument = MAPPER.readTree(ByteStreams.toByteArray(input));
          JsonNode manifest = packument.path("versions").get(version);
          if (manifest != null && manifest.isObject()) {
            return ((com.fasterxml.jackson.databind.node.ObjectNode) manifest).deepCopy();
          }
        }
      }
    }
    catch (Exception e) {
      log.debug("Could not read stored packument for {}", packagePath, e);
    }
    return null;
  }

  private JsonNode tarballManifest(final FluentComponent component) {
    for (FluentAsset asset : component.assets()) {
      String path = asset.path();
      if (!path.contains("/-/") || !(path.endsWith(".tgz") || path.endsWith(".tar.gz"))) {
        continue;
      }
      try (InputStream input = asset.download().openInputStream();
           GZIPInputStream gzip = new GZIPInputStream(input);
           TarArchiveInputStream tar = new TarArchiveInputStream(gzip)) {
        TarArchiveEntry entry;
        while ((entry = tar.getNextEntry()) != null) {
          String name = entry.getName();
          if (entry.isFile() && name.endsWith("package.json") && name.indexOf('/') == name.lastIndexOf('/')) {
            JsonNode manifest = MAPPER.readTree(ByteStreams.toByteArray(tar));
            if (manifest != null && manifest.isObject()) {
              return manifest;
            }
          }
        }
      }
      catch (Exception e) {
        log.debug("Could not read package.json from {}", path, e);
      }
    }
    return null;
  }

  private static JsonNode readJson(final FluentAsset asset) {
    try (InputStream input = asset.download().openInputStream()) {
      JsonNode document = MAPPER.readTree(ByteStreams.toByteArray(input));
      return document != null && document.isObject() ? document : null;
    }
    catch (Exception e) {
      return null;
    }
  }

  private static String packagePath(final FluentComponent component) {
    String name = component.name();
    String namespace = component.namespace();
    if (NpmCoordinatesHelper.isVersion(name) && namespace != null && !namespace.isEmpty()
        && !(namespace.startsWith("@") && namespace.indexOf('/') < 0)) {
      // Legacy data: a version manifest used to be stored as its own component (namespace=package, name=version).
      return namespace;
    }
    return namespace == null || namespace.isEmpty() ? name : namespace + "/" + name;
  }

  /**
   * Old versions of the format stored a version manifest as its own component, using the version as component name
   * and no namespace. Such components are skipped by the rebuild and ignored by search.
   */
  private static boolean isLegacyManifestComponent(final FluentComponent component) {
    String name = component.name();
    String namespace = component.namespace();
    return NpmCoordinatesHelper.isVersion(name) && (namespace == null || namespace.isEmpty());
  }

  private static String nextToken(final Continuation<FluentComponent> page) {
    try {
      return page.nextContinuationToken();
    }
    catch (IllegalStateException e) {
      return null;
    }
  }
}
