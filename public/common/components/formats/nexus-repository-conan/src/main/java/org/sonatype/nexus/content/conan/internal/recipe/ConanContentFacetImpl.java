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
package org.sonatype.nexus.content.conan.internal.recipe;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.inject.Inject;

import org.sonatype.nexus.content.conan.ConanContentFacet;
import org.sonatype.nexus.repository.content.AttributeOperation;
import org.sonatype.nexus.repository.content.facet.ContentFacetSupport;
import org.sonatype.nexus.repository.content.fluent.FluentAsset;
import org.sonatype.nexus.repository.content.fluent.FluentAssetBuilder;
import org.sonatype.nexus.repository.content.fluent.FluentComponent;
import org.sonatype.nexus.repository.content.fluent.FluentQuery;
import org.sonatype.nexus.repository.content.store.AssetDAO;
import org.sonatype.nexus.repository.content.store.FormatStoreManager;
import org.sonatype.nexus.repository.conan.ConanCoordinates;
import org.sonatype.nexus.repository.conan.internal.ConanFormat;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.Payload;
import org.sonatype.nexus.repository.view.payloads.TempBlob;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.Hashing;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import static org.sonatype.nexus.common.entity.Continuations.iterableOf;

/**
 * A {@link ConanContentFacet} that persists to a {@link ContentFacetSupport}.
 *
 * @since 3.89
 */
@Component
@Qualifier(ConanFormat.NAME)
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ConanContentFacetImpl
    extends ContentFacetSupport
    implements ConanContentFacet
{
  /**
   * Attribute holding the MD5 of a stored file, below the {@code conan} attribute block.
   */
  private static final String MD5_ATTRIBUTE = "md5";

  private static final String FILENAME_ATTRIBUTE = "filename";

  private static final String REVISION_ATTRIBUTE = "revision";

  private static final String PACKAGE_ID_ATTRIBUTE = "packageId";

  private static final String PACKAGE_REVISION_ATTRIBUTE = "packageRevision";

  private static final String BASE_VERSION_ATTRIBUTE = "baseVersion";

  private static final String USER_ATTRIBUTE = "user";

  private static final String CHANNEL_ATTRIBUTE = "channel";

  @Inject
  public ConanContentFacetImpl(@Qualifier(ConanFormat.NAME) final FormatStoreManager formatStoreManager) {
    super(formatStoreManager);
  }

  @Override
  public Optional<Content> get(final String path) throws IOException {
    return assets().path(path).find().map(FluentAsset::download);
  }

  @Override
  public Content put(final String path, final Payload content) throws IOException {
    try (TempBlob blob = blobs().ingest(content, HASHING)) {
      FluentAssetBuilder builder = assets()
          .path(path)
          .attributes(ConanFormat.NAME, storedAttributes(path))
          .blob(blob);

      ConanCoordinates coordinates = ConanCoordinates.fromStoragePath(path);
      if (coordinates != null) {
        builder = builder.component(components()
            .name(coordinates.name())
            .namespace(coordinates.namespace())
            .version(coordinates.version())
            .getOrCreate());
      }

      return builder.save()
          .markAsCached(content)
          .download();
    }
  }

  /**
   * Best effort attributes for a stored path, so cached proxy content stays searchable through the UI.
   */
  private Map<String, Object> storedAttributes(final String path) {
    Map<String, Object> attributes = new HashMap<>();
    List<String> segments = ConanCoordinates.segments(path);
    if (segments.isEmpty()) {
      return attributes;
    }
    attributes.put(FILENAME_ATTRIBUTE, segments.get(segments.size() - 1));
    String revision = ConanCoordinates.revisionFromPath(path);
    if (revision != null) {
      attributes.put(REVISION_ATTRIBUTE, revision);
    }
    String packageId = ConanCoordinates.packageIdFromPath(path);
    if (packageId != null) {
      attributes.put(PACKAGE_ID_ATTRIBUTE, packageId);
      String packageRevision = ConanCoordinates.packageRevisionFromPath(path);
      if (packageRevision != null) {
        attributes.put(PACKAGE_REVISION_ATTRIBUTE, packageRevision);
      }
    }
    return attributes;
  }

  @Override
  public Iterable<FluentAsset> assetsWithPrefix(final String prefix) {
    Map<String, Object> params = ImmutableMap.of(
        "repositoryParam", contentRepositoryId(),
        "pathParam", prefix + "%");

    FluentQuery<FluentAsset> query = assets().byFilter(
        "path LIKE #{" + AssetDAO.FILTER_PARAMS + ".pathParam}",
        params);

    // the LIKE pattern treats "_" (the Conan user/channel placeholder) as a single character wildcard, so the
    // results are checked again against the exact prefix
    return ImmutableList.copyOf(
        com.google.common.collect.Iterables.filter(iterableOf(query::browse), asset -> asset.path().startsWith(prefix)));
  }

  @Override
  public FluentAsset putRecipeFile(
      final ConanCoordinates coordinates,
      final String revision,
      final String filename,
      final Payload content) throws IOException
  {
    return save(
        coordinates.recipeFilePath(revision, filename),
        coordinates,
        revision,
        null,
        null,
        filename,
        content);
  }

  @Override
  public FluentAsset putPackageFile(
      final ConanCoordinates coordinates,
      final String revision,
      final String packageId,
      final String packageRevision,
      final String filename,
      final Payload content) throws IOException
  {
    return save(
        coordinates.packageFilePath(revision, packageId, packageRevision, filename),
        coordinates,
        revision,
        packageId,
        packageRevision,
        filename,
        content);
  }

  private FluentAsset save(
      final String path,
      final ConanCoordinates coordinates,
      final String revision,
      final String packageId,
      final String packageRevision,
      final String filename,
      final Payload content) throws IOException
  {
    try (TempBlob blob = blobs().ingest(content, HASHING)) {
      FluentComponent component = components()
          .name(coordinates.name())
          .namespace(coordinates.namespace())
          .version(coordinates.version())
          .getOrCreate();

      // attributes are searched through the UI as attributes.conan.*
      component.attributes(AttributeOperation.OVERLAY, ConanFormat.NAME, componentAttributes(coordinates, revision));

      Map<String, Object> assetAttributes = new HashMap<>();
      assetAttributes.put(FILENAME_ATTRIBUTE, filename);
      assetAttributes.put(REVISION_ATTRIBUTE, revision);
      assetAttributes.put(MD5_ATTRIBUTE, md5(blob));
      if (packageId != null) {
        assetAttributes.put(PACKAGE_ID_ATTRIBUTE, packageId);
        assetAttributes.put(PACKAGE_REVISION_ATTRIBUTE, packageRevision);
      }

      return assets().path(path)
          .component(component)
          .attributes(ConanFormat.NAME, assetAttributes)
          .blob(blob)
          .save()
          .markAsCached(content);
    }
  }

  private Map<String, Object> componentAttributes(final ConanCoordinates coordinates, final String revision) {
    Map<String, Object> attributes = new HashMap<>();
    attributes.put(BASE_VERSION_ATTRIBUTE, coordinates.version());
    attributes.put(USER_ATTRIBUTE, coordinates.user());
    attributes.put(CHANNEL_ATTRIBUTE, coordinates.channel());
    attributes.put(REVISION_ATTRIBUTE, revision);
    return attributes;
  }

  private static String md5(final TempBlob blob) {
    return Optional.ofNullable(blob.getHashes().get(org.sonatype.nexus.common.hash.HashAlgorithm.MD5))
        .map(Object::toString)
        .orElse(null);
  }

  @Override
  public String md5(final FluentAsset asset) throws IOException {
    Object stored = asset.attributes(ConanFormat.NAME).get(MD5_ATTRIBUTE);
    if (stored != null) {
      return stored.toString();
    }

    Optional<String> checksum = asset.blob()
        .map(blob -> blob.checksums().get(org.sonatype.nexus.common.hash.HashAlgorithm.MD5.name()));
    if (checksum.isPresent()) {
      return checksum.get();
    }

    try (Content content = asset.download();
         InputStream in = content.openInputStream()) {
      return Hashing.md5().hashBytes(com.google.common.io.ByteStreams.toByteArray(in)).toString();
    }
  }
}
