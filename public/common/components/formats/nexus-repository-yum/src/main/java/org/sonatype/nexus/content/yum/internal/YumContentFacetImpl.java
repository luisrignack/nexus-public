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
package org.sonatype.nexus.content.yum.internal;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import jakarta.inject.Inject;

import org.sonatype.nexus.common.entity.Continuations;
import org.sonatype.nexus.common.hash.HashAlgorithm;
import org.sonatype.nexus.content.yum.YumContentFacet;
import org.sonatype.nexus.repository.content.facet.ContentFacetSupport;
import org.sonatype.nexus.repository.content.fluent.FluentAsset;
import org.sonatype.nexus.repository.content.fluent.FluentComponentBuilder;
import org.sonatype.nexus.repository.content.maintenance.ContentMaintenanceFacet;
import org.sonatype.nexus.repository.content.store.FormatStoreManager;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.Payload;
import org.sonatype.nexus.repository.view.payloads.BytesPayload;
import org.sonatype.nexus.repository.view.payloads.TempBlob;
import org.sonatype.nexus.repository.yum.internal.YumFormat;
import org.sonatype.nexus.repository.yum.internal.rpm.RpmHeaderParser;
import org.sonatype.nexus.repository.yum.internal.rpm.RpmPackage;

import com.google.common.hash.HashCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Yum content facet backed by the standard Nexus content store.
 */
@Component
@Qualifier(YumFormat.NAME)
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class YumContentFacetImpl
    extends ContentFacetSupport
    implements YumContentFacet
{
  @Inject
  public YumContentFacetImpl(@Qualifier(YumFormat.NAME) final FormatStoreManager formatStoreManager) {
    super(formatStoreManager);
  }

  @Override
  public Optional<Content> get(final String path) throws IOException {
    return assets().path(normalize(path)).find().map(FluentAsset::download);
  }

  @Override
  public Content put(final String path, final Payload content) throws IOException {
    String assetPath = normalize(path);
    try (TempBlob blob = blobs().ingest(content, HASHING)) {
      return assets()
          .path(assetPath)
          .component(genericComponent(assetPath))
          .blob(blob)
          .save()
          .markAsCached(content)
          .download();
    }
  }

  @Override
  public Content putRpm(final String path, final Payload content) throws IOException {
    String assetPath = normalize(path);
    try (TempBlob blob = blobs().ingest(content, HASHING)) {
      String sha256 = checksum(blob, HashAlgorithm.SHA256);
      RpmPackage rpm = RpmHeaderParser.parse(
          blob.get(), assetPath, sha256, blob.getBlob().getMetrics().getContentSize());
      rpm.withFileTime(Instant.now().getEpochSecond());

      FluentComponentBuilder component = components()
          .name(rpm.name())
          .namespace(rpm.arch())
          .version(rpm.componentVersion())
          .kind("rpm");

      return assets()
          .path(assetPath)
          .kind("rpm")
          .component(component.getOrCreate())
          .blob(blob)
          .save()
          .markAsCached(content)
          .download();
    }
  }

  @Override
  public Content putGenerated(final String path, final byte[] content, final String contentType) throws IOException {
    return putGenerated(path, content, contentType, Map.of());
  }

  @Override
  public Content putGenerated(
      final String path,
      final byte[] content,
      final String contentType,
      final Map<String, Object> attributes) throws IOException
  {
    String assetPath = normalize(path);
    Payload payload = new BytesPayload(content, contentType);
    try (TempBlob blob = blobs().ingest(payload, HASHING)) {
      var builder = assets()
          .path(assetPath)
          .kind("yum-metadata")
          .component(components()
              .name("_yum_metadata")
              .namespace("repodata")
              .version("1")
              .kind("yum-metadata")
              .getOrCreate());
      for (Map.Entry<String, Object> attribute : attributes.entrySet()) {
        builder = builder.attributes(attribute.getKey(), attribute.getValue());
      }
      return builder
          .blob(blob)
          .save()
          .download();
    }
  }

  @Override
  public boolean delete(final String path) throws IOException {
    String assetPath = normalize(path);
    return assets().path(assetPath)
        .find()
        .map(asset -> repository().facet(ContentMaintenanceFacet.class).deleteAsset(asset).contains(assetPath))
        .orElse(false);
  }

  @Override
  public Optional<FluentAsset> find(final String path) {
    return assets().path(normalize(path)).find();
  }

  @Override
  public Stream<FluentAsset> rpmAssets() {
    return Continuations
        .streamOf((limit, token) -> assets().browse(limit, token))
        .filter(asset -> asset.path().toLowerCase(java.util.Locale.ROOT).endsWith(".rpm"));
  }

  private org.sonatype.nexus.repository.content.Component genericComponent(final String path) {
    return components()
        .name(path)
        .namespace(group(path))
        .getOrCreate();
  }

  private static String group(final String path) {
    int slash = path.lastIndexOf('/');
    if (slash <= 0) {
      return "/";
    }
    return path.substring(0, slash);
  }

  private static String checksum(final TempBlob blob, final HashAlgorithm algorithm) {
    HashCode hash = blob.getHashes().get(algorithm);
    return hash == null ? "" : hash.toString();
  }

  private static String normalize(final String path) {
    return path.startsWith("/") ? path : "/" + path;
  }
}
