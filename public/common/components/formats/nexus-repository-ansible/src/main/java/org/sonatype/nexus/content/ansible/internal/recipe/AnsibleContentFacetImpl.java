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
package org.sonatype.nexus.content.ansible.internal.recipe;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import jakarta.inject.Inject;

import org.sonatype.nexus.common.entity.Continuation;
import org.sonatype.nexus.content.ansible.AnsibleContentFacet;
import org.sonatype.nexus.repository.content.facet.ContentFacetSupport;
import org.sonatype.nexus.repository.content.fluent.FluentAsset;
import org.sonatype.nexus.repository.content.fluent.FluentComponent;
import org.sonatype.nexus.repository.content.maintenance.ContentMaintenanceFacet;
import org.sonatype.nexus.repository.content.store.FormatStoreManager;
import org.sonatype.nexus.repository.ansible.AnsibleCollectionMetadata;
import org.sonatype.nexus.repository.ansible.AnsibleCoordinatesHelper;
import org.sonatype.nexus.repository.ansible.internal.AnsibleFormat;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.Payload;
import org.sonatype.nexus.repository.view.payloads.TempBlob;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * A {@link AnsibleContentFacet} that persists to a {@link ContentFacetSupport}.
 *
 * <p>Collections live at {@code /collections/{namespace}/{name}/{version}/{namespace}.{name}-{version}.tar.gz};
 * the coordinates and description read from {@code MANIFEST.json} are stored on the component so the
 * Galaxy API documents can be generated without opening the archives again.</p>
 */
@Component
@Qualifier(AnsibleFormat.NAME)
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class AnsibleContentFacetImpl
    extends ContentFacetSupport
    implements AnsibleContentFacet
{
  @Inject
  public AnsibleContentFacetImpl(@Qualifier(AnsibleFormat.NAME) final FormatStoreManager formatStoreManager) {
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
          .attributes(FILE_NAME_ATTRIBUTE, AnsibleCoordinatesHelper.fileName(path))
          .blob(blob)
          .save()
          .markAsCached(content)
          .download();
    }
  }

  @Override
  public Content putCollection(
      final String path,
      final String namespace,
      final String name,
      final String version,
      final Payload content) throws IOException
  {
    try (TempBlob blob = blobs().ingest(content, HASHING)) {
      AnsibleCollectionMetadata metadata;
      try (InputStream input = blob.get()) {
        metadata = AnsibleCollectionMetadata.read(input);
      }
      catch (IOException e) {
        throw new IllegalArgumentException("Not a valid Ansible collection archive: " + e.getMessage(), e);
      }

      if (!metadata.getNamespace().equals(namespace)
          || !metadata.getName().equals(name)
          || !metadata.getVersion().equals(version))
      {
        throw new IllegalArgumentException(String.format(
            "Archive declares %s.%s-%s but the URL requested %s.%s-%s",
            metadata.getNamespace(), metadata.getName(), metadata.getVersion(),
            namespace, name, version));
      }

      FluentComponent component = components()
          .name(name)
          .namespace(namespace)
          .version(version)
          .getOrCreate();

      FluentAsset asset = assets().path(path)
          .attributes(FILE_NAME_ATTRIBUTE, AnsibleCoordinatesHelper.fileName(path))
          .component(component)
          .blob(blob)
          .save()
          .markAsCached(content);

      if (metadata.getDescription() != null && !metadata.getDescription().isEmpty()) {
        component.withAttribute(DESCRIPTION_ATTRIBUTE, metadata.getDescription());
      }
      if (!metadata.getAuthors().isEmpty()) {
        component.withAttribute(AUTHORS_ATTRIBUTE, metadata.getAuthors());
      }
      if (!metadata.getDependencies().isEmpty()) {
        component.withAttribute(DEPENDENCIES_ATTRIBUTE, metadata.getDependencies());
      }

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
  public boolean deleteCollection(final String namespace, final String name) throws IOException {
    ContentMaintenanceFacet maintenance = repository().facet(ContentMaintenanceFacet.class);
    boolean deleted = false;

    String continuationToken = null;
    do {
      Continuation<FluentComponent> page = components().browseWithAssets(100, continuationToken);
      for (FluentComponent component : page) {
        if (!name.equals(component.name()) || !namespace.equals(component.namespace())) {
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
