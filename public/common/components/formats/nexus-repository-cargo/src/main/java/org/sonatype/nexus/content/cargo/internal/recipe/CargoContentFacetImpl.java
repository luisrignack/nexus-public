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
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.inject.Inject;

import org.sonatype.nexus.common.entity.Continuation;
import org.sonatype.nexus.content.cargo.CargoContentFacet;
import org.sonatype.nexus.repository.cargo.CargoCoordinatesHelper;
import org.sonatype.nexus.repository.cargo.CargoCrateSummary;
import org.sonatype.nexus.repository.cargo.internal.CargoFormat;
import org.sonatype.nexus.repository.content.facet.ContentFacetSupport;
import org.sonatype.nexus.repository.content.fluent.FluentAsset;
import org.sonatype.nexus.repository.content.fluent.FluentAssetBuilder;
import org.sonatype.nexus.repository.content.fluent.FluentComponentBuilder;
import org.sonatype.nexus.repository.content.maintenance.ContentMaintenanceFacet;
import org.sonatype.nexus.repository.content.store.FormatStoreManager;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.Payload;
import org.sonatype.nexus.repository.view.payloads.BytesPayload;
import org.sonatype.nexus.repository.view.payloads.TempBlob;

import com.google.common.io.ByteStreams;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import static org.sonatype.nexus.repository.view.ContentTypes.APPLICATION_JSON;

/**
 * A {@link CargoContentFacet} that persists to a {@link ContentFacetSupport}.
 *
 * @since 3.89
 */
@Component
@Qualifier(CargoFormat.NAME)
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class CargoContentFacetImpl
    extends ContentFacetSupport
    implements CargoContentFacet
{
  private static final int PAGE_SIZE = 200;

  @Inject
  public CargoContentFacetImpl(@Qualifier(CargoFormat.NAME) final FormatStoreManager formatStoreManager) {
    super(formatStoreManager);
  }

  @Override
  public Optional<Content> get(final String path) throws IOException {
    return assets().path(CargoCoordinatesHelper.storagePath(path)).find().map(FluentAsset::download);
  }

  @Override
  public Content put(final String path, final Payload content) throws IOException {
    return put(path, content, null);
  }

  @Override
  public Content put(final String path, final Payload content, final Map<String, Object> attributes)
      throws IOException
  {
    try (TempBlob blob = blobs().ingest(content, HASHING)) {
      FluentAssetBuilder builder = assets().path(CargoCoordinatesHelper.storagePath(path))
          .attributes(FILE_NAME_ATTRIBUTE, fileName(path));
      if (attributes != null) {
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
          builder = builder.attributes(entry.getKey(), entry.getValue());
        }
      }
      return builder.blob(blob).save().markAsCached(content).download();
    }
  }

  @Override
  public Content put(
      final String path,
      final String crate,
      final String version,
      final Payload content) throws IOException
  {
    try (TempBlob blob = blobs().ingest(content, HASHING)) {
      FluentComponentBuilder component = components()
          .name(CargoCoordinatesHelper.lower(crate))
          .namespace("")
          .version(version);

      return assets().path(CargoCoordinatesHelper.storagePath(path))
          .attributes(FILE_NAME_ATTRIBUTE, fileName(path))
          .component(component.getOrCreate())
          .blob(blob)
          .save()
          .markAsCached(content)
          .download();
    }
  }

  @Override
  public boolean delete(final String path) throws IOException {
    String storagePath = CargoCoordinatesHelper.storagePath(path);
    return assets().path(storagePath)
        .find()
        .map(asset -> repository().facet(ContentMaintenanceFacet.class).deleteAsset(asset).contains(storagePath))
        .orElse(false);
  }

  @Override
  public String readIndex(final String crate) throws IOException {
    Optional<Content> content = get(CargoCoordinatesHelper.indexPath(crate));
    if (content.isEmpty()) {
      return "";
    }
    try (InputStream input = content.get().openInputStream()) {
      return new String(ByteStreams.toByteArray(input), StandardCharsets.UTF_8);
    }
  }

  @Override
  public Content writeIndex(final String crate, final String ndjson, final CargoCrateSummary summary)
      throws IOException
  {
    Map<String, Object> attributes = new HashMap<>();
    attributes.put(FILE_NAME_ATTRIBUTE, CargoCoordinatesHelper.indexPath(crate));
    attributes.put(NAME_ATTRIBUTE, summary.getName());
    attributes.put(MAX_VERSION_ATTRIBUTE, summary.getMaxVersion());
    attributes.put(DESCRIPTION_ATTRIBUTE, summary.getDescription());
    attributes.put(DOWNLOADS_ATTRIBUTE, summary.getDownloads());

    return put(CargoCoordinatesHelper.indexPath(crate),
        new BytesPayload(ndjson.getBytes(StandardCharsets.UTF_8), APPLICATION_JSON),
        attributes);
  }

  @Override
  public List<CargoCrateSummary> listCrates() throws IOException {
    List<CargoCrateSummary> crates = new ArrayList<>();
    Continuation<FluentAsset> page;
    String continuationToken = null;
    do {
      page = assets().browse(PAGE_SIZE, continuationToken);
      for (FluentAsset asset : page) {
        String path = CargoCoordinatesHelper.normalize(asset.path());
        if (!path.startsWith(CargoCoordinatesHelper.INDEX_DIR + "/")
            || CargoCoordinatesHelper.isConfigPath(path)) {
          continue;
        }
        crates.add(summaryOf(asset, path));
      }
      continuationToken = page.nextContinuationToken();
    }
    while (continuationToken != null);
    return crates;
  }

  @Override
  public Optional<String> remoteApiBase() {
    return remoteAttribute(REMOTE_API_ATTRIBUTE);
  }

  @Override
  public Optional<String> remoteDlBase() {
    return remoteAttribute(REMOTE_DL_ATTRIBUTE);
  }

  private Optional<String> remoteAttribute(final String key) {
    return assets().path(CargoCoordinatesHelper.storagePath(CargoCoordinatesHelper.CONFIG_PATH))
        .find()
        .map(FluentAsset::attributes)
        .map(attributes -> attributes.get(key))
        .map(String::valueOf)
        .filter(value -> !value.isEmpty() && !"null".equals(value));
  }

  private CargoCrateSummary summaryOf(final FluentAsset asset, final String path) {
    Map<String, Object> attributes = asset.attributes().backing();

    Object name = attributes.get(NAME_ATTRIBUTE);
    String crateName = name != null ? String.valueOf(name) : fileName(path);

    Object maxVersion = attributes.get(MAX_VERSION_ATTRIBUTE);
    Object description = attributes.get(DESCRIPTION_ATTRIBUTE);
    Object downloads = attributes.get(DOWNLOADS_ATTRIBUTE);

    OffsetDateTime lastUpdated = asset.lastUpdated();
    String updatedAt = lastUpdated == null ? "" : DateTimeFormatter.ISO_INSTANT.format(lastUpdated.toInstant());

    return new CargoCrateSummary(
        crateName,
        description == null ? "" : String.valueOf(description),
        maxVersion == null ? "" : String.valueOf(maxVersion),
        asLong(downloads),
        updatedAt);
  }

  private static long asLong(final Object value) {
    if (value instanceof Number) {
      return ((Number) value).longValue();
    }
    if (value == null) {
      return 0L;
    }
    try {
      return Long.parseLong(String.valueOf(value).trim());
    }
    catch (NumberFormatException e) {
      return 0L;
    }
  }

  private static String fileName(final String path) {
    int index = path.lastIndexOf('/');
    return index >= 0 ? path.substring(index + 1) : path;
  }
}
