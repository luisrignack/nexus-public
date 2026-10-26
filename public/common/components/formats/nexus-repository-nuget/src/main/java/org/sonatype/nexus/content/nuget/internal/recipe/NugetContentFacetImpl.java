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
package org.sonatype.nexus.content.nuget.internal.recipe;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import jakarta.inject.Inject;

import org.sonatype.nexus.common.entity.Continuation;
import org.sonatype.nexus.content.nuget.NugetContentFacet;
import org.sonatype.nexus.content.nuget.NugetPublishResult;
import org.sonatype.nexus.repository.content.facet.ContentFacetSupport;
import org.sonatype.nexus.repository.content.fluent.FluentAsset;
import org.sonatype.nexus.repository.content.fluent.FluentAssetBuilder;
import org.sonatype.nexus.repository.content.fluent.FluentComponent;
import org.sonatype.nexus.repository.content.fluent.FluentComponentBuilder;
import org.sonatype.nexus.repository.content.maintenance.ContentMaintenanceFacet;
import org.sonatype.nexus.repository.content.store.FormatStoreManager;
import org.sonatype.nexus.repository.nuget.NugetCoordinatesHelper;
import org.sonatype.nexus.repository.nuget.NugetNuspec;
import org.sonatype.nexus.repository.nuget.NugetPackage;
import org.sonatype.nexus.repository.nuget.internal.NugetFormat;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.Payload;
import org.sonatype.nexus.repository.view.payloads.TempBlob;

import com.google.common.io.ByteStreams;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * A {@link NugetContentFacet} that persists to a {@link ContentFacetSupport}.
 */
@Component
@Qualifier(NugetFormat.NAME)
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class NugetContentFacetImpl
    extends ContentFacetSupport
    implements NugetContentFacet
{
  private static final int PAGE_SIZE = 100;

  private static final String NUPKG_CONTENT_TYPE = "application/octet-stream";

  private static final String NUSPEC_CONTENT_TYPE = "application/xml";

  private static final String DEFAULT_CONTENT_TYPE = "application/json";

  @Inject
  public NugetContentFacetImpl(@Qualifier(NugetFormat.NAME) final FormatStoreManager formatStoreManager) {
    super(formatStoreManager);
  }

  @Override
  public Optional<Content> get(final String path) throws IOException {
    return assets().path(path).find().map(FluentAsset::download);
  }

  @Override
  public Content put(final String path, final Payload content) throws IOException {
    return put(path, content, null);
  }

  @Override
  public Content put(
      final String path,
      final Payload content,
      final Map<String, Object> attributes) throws IOException
  {
    String contentType = content.getContentType() != null ? content.getContentType() : DEFAULT_CONTENT_TYPE;
    try (InputStream input = content.openInputStream();
         TempBlob blob = blobs().ingest(input, contentType, HASHING)) {
      FluentAssetBuilder builder = assets().path(path)
          .attributes(FILE_NAME_ATTRIBUTE, fileName(path));
      if (attributes != null) {
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
          builder = builder.attributes(entry.getKey(), entry.getValue());
        }
      }
      return builder.blob(blob)
          .save()
          .markAsCached(content)
          .download();
    }
  }

  @Override
  public Optional<Map<String, Object>> assetAttributes(final String path) throws IOException {
    return assets().path(path).find()
        .map(asset -> asset.attributes().backing());
  }

  @Override
  public NugetPublishResult publish(final Payload nupkg) throws IOException {
    try (InputStream input = nupkg.openInputStream();
         TempBlob packageBlob = blobs().ingest(input, NUPKG_CONTENT_TYPE, HASHING))
    {
      byte[] nuspecXml;
      try (InputStream archive = packageBlob.get()) {
        nuspecXml = NugetNuspec.extractXml(archive);
      }
      NugetNuspec nuspec = NugetNuspec.parse(nuspecXml);

      String id = NugetCoordinatesHelper.normalizeId(nuspec.getId());
      String version = NugetCoordinatesHelper.normalizeVersion(nuspec.getVersion());
      Map<String, Object> metadata = nuspec.toAttributeMap();

      FluentComponent component = component(id, version, metadata);

      String nupkgPath = NugetCoordinatesHelper.nupkgPath(id, version);
      assets().path(nupkgPath)
          .attributes(FILE_NAME_ATTRIBUTE, id + "." + version + NugetCoordinatesHelper.NUPKG_EXTENSION)
          .attributes(METADATA_ATTRIBUTE, metadata)
          .component(component)
          .blob(packageBlob)
          .save();

      String nuspecPath = NugetCoordinatesHelper.nuspecPath(id, version);
      try (TempBlob manifestBlob = blobs().ingest(new ByteArrayInputStream(nuspecXml), NUSPEC_CONTENT_TYPE, HASHING)) {
        assets().path(nuspecPath)
            .attributes(FILE_NAME_ATTRIBUTE, id + "." + version + NugetCoordinatesHelper.NUSPEC_EXTENSION)
            .component(component)
            .blob(manifestBlob)
            .save();
      }

      return new NugetPublishResult(id, version, nupkgPath, nuspecPath, nuspecXml);
    }
  }

  private FluentComponent component(final String id, final String version, final Map<String, Object> metadata) {
    FluentComponentBuilder builder = components()
        .name(id)
        .namespace("")
        .version(version);
    FluentComponent component = builder.getOrCreate();
    // refresh the stored metadata on re-publish
    return component.withAttribute(METADATA_ATTRIBUTE, metadata);
  }

  @Override
  public boolean delete(final String path) throws IOException {
    ContentMaintenanceFacet maintenance = repository().facet(ContentMaintenanceFacet.class);

    Optional<FluentAsset> asset = assets().path(path).find();
    boolean deleted = asset.isPresent() && maintenance.deleteAsset(asset.get()).contains(path);
    if (!deleted) {
      return false;
    }

    // a package is stored as a pair of assets (nupkg + nuspec): remove the sibling as well
    String sibling = siblingPath(path);
    if (sibling != null) {
      assets().path(sibling).find().ifPresent(maintenance::deleteAsset);
    }

    deleteComponentIfOrphan(path);
    return true;
  }

  @Nullable
  private String siblingPath(final String path) {
    String normalized = NugetCoordinatesHelper.normalizePath(path);
    if (normalized.endsWith(NugetCoordinatesHelper.NUPKG_EXTENSION)) {
      return normalized.substring(0, normalized.length() - NugetCoordinatesHelper.NUPKG_EXTENSION.length())
          + NugetCoordinatesHelper.NUSPEC_EXTENSION;
    }
    if (normalized.endsWith(NugetCoordinatesHelper.NUSPEC_EXTENSION)) {
      return normalized.substring(0, normalized.length() - NugetCoordinatesHelper.NUSPEC_EXTENSION.length())
          + NugetCoordinatesHelper.NUPKG_EXTENSION;
    }
    return null;
  }

  private void deleteComponentIfOrphan(final String path) {
    String[] parts = NugetCoordinatesHelper.normalizePath(path).split("/");
    if (parts.length != 5 || !NugetCoordinatesHelper.FLAT_CONTAINER.equals(parts[1])) {
      return;
    }
    components()
        .name(parts[2])
        .namespace("")
        .version(parts[3])
        .find()
        .filter(component -> component.assets().isEmpty())
        .ifPresent(FluentComponent::delete);
  }

  @Override
  public List<NugetPackage> packages() throws IOException {
    List<NugetPackage> packages = new ArrayList<>();

    String continuationToken = null;
    do {
      Continuation<FluentComponent> page = components().browseWithAssets(PAGE_SIZE, continuationToken);
      for (FluentComponent component : page) {
        NugetPackage nugetPackage = toPackage(component);
        if (nugetPackage != null) {
          packages.add(nugetPackage);
        }
      }
      if (page.isEmpty()) {
        break;
      }
      continuationToken = page.nextContinuationToken();
    }
    while (continuationToken != null);

    return packages;
  }

  @Override
  public List<String> versions(final String id) throws IOException {
    Collection<String> versions = components().versions("", NugetCoordinatesHelper.normalizeId(id));
    return NugetCoordinatesHelper.sortVersions(versions);
  }

  @Nullable
  private NugetPackage toPackage(final FluentComponent component) {
    String id = component.name();
    String version = component.version();
    if (id == null || id.isEmpty() || version == null || version.isEmpty()) {
      return null;
    }

    String published = component.created() == null
        ? null
        : DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(component.created());

    Object raw = component.attributes().get(METADATA_ATTRIBUTE);
    if (raw instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> metadata = (Map<String, Object>) raw;
      return new NugetPackage(
          string(metadata, "id", id),
          string(metadata, "version", version),
          string(metadata, "title", null),
          string(metadata, "description", null),
          string(metadata, "authors", null),
          string(metadata, "owners", null),
          string(metadata, "projectUrl", null),
          string(metadata, "licenseUrl", null),
          string(metadata, "tags", null),
          stringList(metadata, "dependencies"),
          published,
          true);
    }

    // no cached metadata: fall back to parsing the stored manifest
    for (FluentAsset asset : component.assets()) {
      if (!asset.path().toLowerCase(Locale.ROOT).endsWith(NugetCoordinatesHelper.NUSPEC_EXTENSION)
          || !asset.hasBlob()) {
        continue;
      }
      try (InputStream input = asset.download().openInputStream()) {
        NugetNuspec nuspec = NugetNuspec.parse(ByteStreams.toByteArray(input));
        return NugetPackage.of(nuspec, published);
      }
      catch (Exception e) {
        // unreadable manifest; try the next asset
      }
    }
    return null;
  }

  private static String string(final Map<String, Object> metadata, final String key, @Nullable final String defaultValue) {
    Object value = metadata.get(key);
    return value instanceof String ? (String) value : defaultValue;
  }

  @SuppressWarnings("unchecked")
  private static List<String> stringList(final Map<String, Object> metadata, final String key) {
    Object value = metadata.get(key);
    if (value instanceof List) {
      List<String> values = new ArrayList<>();
      for (Object item : (List<Object>) value) {
        if (item instanceof String) {
          values.add((String) item);
        }
      }
      return values;
    }
    return List.of();
  }

  private static String fileName(final String path) {
    String normalized = NugetCoordinatesHelper.normalizePath(path);
    int slash = normalized.lastIndexOf('/');
    return slash >= 0 ? normalized.substring(slash + 1) : normalized;
  }
}
