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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.inject.Inject;

import org.sonatype.nexus.common.entity.Continuation;
import org.sonatype.nexus.content.pub.PubContentFacet;
import org.sonatype.nexus.repository.content.facet.ContentFacetSupport;
import org.sonatype.nexus.repository.content.fluent.FluentAsset;
import org.sonatype.nexus.repository.content.maintenance.ContentMaintenanceFacet;
import org.sonatype.nexus.repository.content.store.FormatStoreManager;
import org.sonatype.nexus.repository.pub.PubPackageMetadata;
import org.sonatype.nexus.repository.pub.internal.PubCoordinatesHelper;
import org.sonatype.nexus.repository.pub.internal.PubFormat;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.Payload;
import org.sonatype.nexus.repository.view.payloads.BytesPayload;
import org.sonatype.nexus.repository.view.payloads.TempBlob;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import static org.sonatype.nexus.repository.view.ContentTypes.APPLICATION_OCTET_STREAM;

/**
 * {@link PubContentFacet} backed by the Nexus content store.
 */
@Component
@Qualifier(PubFormat.NAME)
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class PubContentFacetImpl
    extends ContentFacetSupport
    implements PubContentFacet
{
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Inject
  public PubContentFacetImpl(@Qualifier(PubFormat.NAME) final FormatStoreManager formatStoreManager) {
    super(formatStoreManager);
  }

  @Override
  public Optional<Content> get(final String path) throws IOException {
    return assets().path(path).find().map(FluentAsset::download);
  }

  @Override
  public Content put(final String path, final Payload content) throws IOException {
    try (TempBlob blob = blobs().ingest(content, HASHING)) {
      return assets().path(path)
          .blob(blob)
          .save()
          .markAsCached(content)
          .download();
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public Content putPackage(final byte[] archive) throws IOException {
    PubPackageMetadata metadata = PubPackageMetadata.read(archive);
    BytesPayload payload = new BytesPayload(archive, APPLICATION_OCTET_STREAM);
    try (TempBlob blob = blobs().ingest(payload, HASHING)) {
      String path = PubCoordinatesHelper.archivePath(metadata.getName(), metadata.getVersion());
      FluentAsset asset = assets().path(path)
          .attributes(PACKAGE_NAME_ATTRIBUTE, metadata.getName())
          .attributes(PACKAGE_VERSION_ATTRIBUTE, metadata.getVersion())
          .attributes(PUBSPEC_ATTRIBUTE, metadata.getPubspecJson())
          .attributes(SHA256_ATTRIBUTE, metadata.getSha256())
          .blob(blob)
          .save()
          .markAsCached(payload);
      return asset.download();
    }
  }

  @Override
  public boolean delete(final String path) throws IOException {
    return assets().path(path)
        .find()
        .map(asset -> repository().facet(ContentMaintenanceFacet.class).deleteAsset(asset).contains(path))
        .orElse(false);
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<PubPackageMetadata> packages(final String name) {
    List<PubPackageMetadata> result = new ArrayList<>();
    String continuationToken = null;
    do {
      Continuation<FluentAsset> page = assets().browse(1000, continuationToken);
      for (FluentAsset asset : page) {
        Object assetName = asset.attributes().get(PACKAGE_NAME_ATTRIBUTE);
        if (!name.equals(assetName) || !PubCoordinatesHelper.isArchive(asset.path())) {
          continue;
        }
        String version = String.valueOf(asset.attributes().get(PACKAGE_VERSION_ATTRIBUTE));
        String pubspec = String.valueOf(asset.attributes().get(PUBSPEC_ATTRIBUTE));
        String sha256 = String.valueOf(asset.attributes().get(SHA256_ATTRIBUTE));
        Map<String, Object> parsed;
        try {
          parsed = MAPPER.readValue(pubspec, Map.class);
        }
        catch (IOException e) {
          continue;
        }
        result.add(new PubPackageMetadata(name, version, parsed, sha256, null));
      }
      if (page.isEmpty()) {
        break;
      }
      continuationToken = page.nextContinuationToken();
    }
    while (continuationToken != null);

    result.sort(Comparator.comparing(PubPackageMetadata::getVersion));
    return result;
  }
}
