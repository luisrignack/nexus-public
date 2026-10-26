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
package org.sonatype.nexus.content.alpine.internal.recipe;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;

import org.sonatype.nexus.common.entity.Continuation;
import org.sonatype.nexus.content.alpine.AlpineContentFacet;
import org.sonatype.nexus.repository.alpine.AlpinePackageMetadata;
import org.sonatype.nexus.repository.alpine.internal.AlpineFormat;
import org.sonatype.nexus.repository.content.facet.ContentFacetSupport;
import org.sonatype.nexus.repository.content.fluent.FluentAsset;
import org.sonatype.nexus.repository.content.maintenance.ContentMaintenanceFacet;
import org.sonatype.nexus.repository.content.store.FormatStoreManager;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.Payload;
import org.sonatype.nexus.repository.view.payloads.TempBlob;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * {@link AlpineContentFacet} backed by the Nexus content store.
 */
@Component
@Qualifier(AlpineFormat.NAME)
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class AlpineContentFacetImpl
    extends ContentFacetSupport
    implements AlpineContentFacet
{
  @Inject
  public AlpineContentFacetImpl(@Qualifier(AlpineFormat.NAME) final FormatStoreManager formatStoreManager) {
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
  public Content putPackage(final String path, final Payload content) throws IOException {
    try (TempBlob blob = blobs().ingest(content, HASHING)) {
      long size = blob.getBlob().getMetrics().getContentSize();
      AlpinePackageMetadata metadata;
      try (InputStream input = blob.get()) {
        metadata = AlpinePackageMetadata.read(input, size);
      }
      catch (IOException | IllegalArgumentException e) {
        throw new IllegalArgumentException("Not a valid Alpine package: " + e.getMessage(), e);
      }

      FluentAsset asset = assets().path(path)
          .attributes(INDEX_ENTRY_ATTRIBUTE, metadata.toIndexEntry())
          .attributes(PACKAGE_NAME_ATTRIBUTE, metadata.getName())
          .attributes(PACKAGE_VERSION_ATTRIBUTE, metadata.getVersion())
          .attributes(PACKAGE_ARCH_ATTRIBUTE, metadata.getArch())
          .blob(blob)
          .save()
          .markAsCached(content);

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
  public List<String> indexEntries(final String directory) {
    String prefix = directory.endsWith("/") ? directory : directory + "/";
    List<String> entries = new ArrayList<>();

    String continuationToken = null;
    do {
      Continuation<FluentAsset> page = assets().browse(1000, continuationToken);
      for (FluentAsset asset : page) {
        String path = asset.path();
        if (!path.startsWith(prefix)) {
          continue;
        }
        String name = path.substring(prefix.length());
        if (name.isEmpty() || name.indexOf('/') >= 0 || !name.endsWith(".apk")) {
          continue;
        }
        Object entry = asset.attributes().get(INDEX_ENTRY_ATTRIBUTE);
        if (entry instanceof String) {
          entries.add((String) entry);
        }
      }
      if (page.isEmpty()) {
        break;
      }
      continuationToken = page.nextContinuationToken();
    }
    while (continuationToken != null);

    Collections.sort(entries);
    return entries;
  }
}
