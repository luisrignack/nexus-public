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
package org.sonatype.nexus.content.pypi.internal.recipe;

import java.io.IOException;
import java.util.Optional;

import jakarta.inject.Inject;

import org.sonatype.nexus.common.entity.Continuation;
import org.sonatype.nexus.content.pypi.PyPiContentFacet;
import org.sonatype.nexus.repository.content.facet.ContentFacetSupport;
import org.sonatype.nexus.repository.content.fluent.FluentAsset;
import org.sonatype.nexus.repository.content.fluent.FluentAssetBuilder;
import org.sonatype.nexus.repository.content.fluent.FluentComponent;
import org.sonatype.nexus.repository.content.fluent.FluentComponentBuilder;
import org.sonatype.nexus.repository.content.maintenance.ContentMaintenanceFacet;
import org.sonatype.nexus.repository.content.store.FormatStoreManager;
import org.sonatype.nexus.repository.pypi.PyPiCoordinatesHelper;
import org.sonatype.nexus.repository.pypi.internal.PyPiFormat;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.Payload;
import org.sonatype.nexus.repository.view.payloads.TempBlob;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * A {@link PyPiContentFacet} that persists to a {@link ContentFacetSupport}.
 */
@Component
@Qualifier(PyPiFormat.NAME)
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class PyPiContentFacetImpl
    extends ContentFacetSupport
    implements PyPiContentFacet
{
  @Inject
  public PyPiContentFacetImpl(@Qualifier(PyPiFormat.NAME) final FormatStoreManager formatStoreManager) {
    super(formatStoreManager);
  }

  @Override
  public Optional<Content> get(final String path) throws IOException {
    return assets().path(path).find().map(FluentAsset::download);
  }

  @Override
  public FluentAsset getOrCreateAsset(final String path, final String projectName, final String version) {
    FluentComponentBuilder builder = components()
        .name(PyPiCoordinatesHelper.normalizeProjectName(projectName))
        .namespace("");
    if (version != null && !version.isEmpty()) {
      builder.version(version);
    }
    return assets().path(path)
        .component(builder.getOrCreate())
        .save();
  }

  @Override
  public Content put(final String path, final Payload content) throws IOException {
    try (TempBlob blob = blobs().ingest(content, HASHING)) {
      return assets().path(path)
          .attributes(FILE_NAME_ATTRIBUTE, PyPiCoordinatesHelper.fileName(path))
          .blob(blob)
          .save()
          .markAsCached(content)
          .download();
    }
  }

  @Override
  public Content put(
      final String path,
      final String projectName,
      final String version,
      final Payload content,
      final String requiresPython) throws IOException
  {
    try (TempBlob blob = blobs().ingest(content, HASHING)) {
      FluentComponentBuilder builder = components()
          .name(PyPiCoordinatesHelper.normalizeProjectName(projectName))
          .namespace("");
      if (version != null && !version.isEmpty()) {
        builder.version(version);
      }

      FluentAssetBuilder asset = assets().path(path);
      if (requiresPython != null && !requiresPython.isEmpty()) {
        asset = asset.attributes(REQUIRES_PYTHON_ATTRIBUTE, requiresPython);
      }
      asset = asset.attributes(FILE_NAME_ATTRIBUTE, PyPiCoordinatesHelper.fileName(path));

      return asset
          .component(builder.getOrCreate())
          .blob(blob)
          .save()
          .markAsCached(content)
          .download();
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
  public boolean deleteProject(final String projectName) throws IOException {
    String normalized = PyPiCoordinatesHelper.normalizeProjectName(projectName);
    ContentMaintenanceFacet maintenance = repository().facet(ContentMaintenanceFacet.class);
    boolean deleted = false;

    String continuationToken = null;
    do {
      Continuation<FluentComponent> page = components().browseWithAssets(100, continuationToken);
      for (FluentComponent component : page) {
        if (!normalized.equals(component.name())) {
          continue;
        }
        for (FluentAsset asset : component.assets()) {
          if (maintenance.deleteAsset(asset).contains(asset.path())) {
            deleted = true;
          }
        }
        component.delete();
      }
      if (page.isEmpty()) {
        break;
      }
      continuationToken = page.nextContinuationToken();
    }
    while (continuationToken != null);

    return deleted;
  }
}
