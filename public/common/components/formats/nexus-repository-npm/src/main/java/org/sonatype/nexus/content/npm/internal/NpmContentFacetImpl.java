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
package org.sonatype.nexus.content.npm.internal;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import javax.inject.Inject;

import org.sonatype.nexus.content.npm.NpmContentFacet;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.content.facet.ContentFacetSupport;
import org.sonatype.nexus.repository.content.fluent.FluentAsset;
import org.sonatype.nexus.repository.content.fluent.FluentComponent;
import org.sonatype.nexus.repository.content.fluent.FluentComponentBuilder;
import org.sonatype.nexus.repository.content.maintenance.ContentMaintenanceFacet;
import org.sonatype.nexus.repository.content.store.FormatStoreManager;
import org.sonatype.nexus.repository.npm.internal.NpmCoordinatesHelper;
import org.sonatype.nexus.repository.npm.internal.NpmFormat;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.Payload;
import org.sonatype.nexus.repository.view.payloads.TempBlob;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * A {@link NpmContentFacet} that persists to a {@link ContentFacet}.
 */
@Component
@Qualifier(NpmFormat.NAME)
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class NpmContentFacetImpl
    extends ContentFacetSupport
    implements NpmContentFacet
{
  @Inject
  public NpmContentFacetImpl(@Qualifier(NpmFormat.NAME) final FormatStoreManager formatStoreManager) {
    super(formatStoreManager);
  }

  @Override
  public Optional<Content> get(final String path) throws IOException {
    return assets().path(path).find().map(FluentAsset::download);
  }

  @Override
  public FluentAsset getOrCreateAsset(
      final Repository repository,
      final String componentName,
      final String componentGroup,
      final String assetName)
  {
    return assets().path(assetName)
        .component(components()
            .name(componentName)
            .namespace(componentGroup)
            .getOrCreate())
        .save();
  }

  @Override
  public Content put(final String path, final Payload content) throws IOException {
    return put(path, content, NpmCoordinatesHelper.getComponentVersion(path));
  }

  @Override
  public Content put(final String path, final Payload content, final String componentVersion) throws IOException {
    try (TempBlob blob = blobs().ingest(content, HASHING)) {
      FluentComponentBuilder builder = components()
          .name(NpmCoordinatesHelper.getComponentName(path))
          .namespace(NpmCoordinatesHelper.getComponentGroup(path));

      if (componentVersion != null && !componentVersion.isEmpty()) {
        builder.version(componentVersion);
      }

      return assets()
          .path(path)
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
  public boolean deletePackage(final String packagePath) {
    String name = NpmCoordinatesHelper.getComponentName(packagePath);
    String group = NpmCoordinatesHelper.getComponentGroup(packagePath);
    List<FluentComponent> components = new java.util.ArrayList<>();
    for (String version : components().versions(group, name)) {
      components().name(name).namespace(group).version(version).find().ifPresent(components::add);
    }

    ContentMaintenanceFacet maintenance = repository().facet(ContentMaintenanceFacet.class);
    boolean deleted = false;
    for (FluentComponent component : components) {
      for (FluentAsset asset : component.assets()) {
        if (maintenance.deleteAsset(asset).contains(asset.path())) {
          deleted = true;
        }
      }
      component.delete();
    }
    return deleted;
  }
}
