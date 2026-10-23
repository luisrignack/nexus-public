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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.sonatype.nexus.content.yum.YumContentFacet;
import org.sonatype.nexus.content.yum.YumMetadataFacet;
import org.sonatype.nexus.repository.FacetSupport;
import org.sonatype.nexus.repository.content.AssetBlob;
import org.sonatype.nexus.repository.content.fluent.FluentAsset;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.yum.internal.YumFormat;
import org.sonatype.nexus.repository.yum.internal.metadata.YumMetadata;
import org.sonatype.nexus.repository.yum.internal.metadata.YumMetadataBuilder;
import org.sonatype.nexus.repository.yum.internal.metadata.YumMetadataFile;
import org.sonatype.nexus.repository.yum.internal.rpm.RpmHeaderParser;
import org.sonatype.nexus.repository.yum.internal.rpm.RpmPackage;

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import static org.sonatype.nexus.repository.yum.internal.metadata.YumMetadata.REPOMD_PATH;

/**
 * Builds Yum metadata from RPM asset headers and stores it as generated assets.
 */
@Component
@Qualifier(YumFormat.NAME)
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class YumMetadataFacetImpl
    extends FacetSupport
    implements YumMetadataFacet
{
  private static final Logger log = LoggerFactory.getLogger(YumMetadataFacetImpl.class);

  private static final String REVISION_ATTRIBUTE = "yum.revision";

  private final Map<String, RpmPackage> packageCache = new ConcurrentHashMap<>();

  private YumContentFacet content;

  @Inject
  public void setContentFacet(final YumContentFacet content) {
    this.content = content;
  }

  @Override
  protected void doStart() throws Exception {
    super.doStart();
    content = facet(YumContentFacet.class);
  }

  @Override
  public void rebuild() throws IOException {
    synchronized (this) {
      List<RpmPackage> packages = loadPackages();
      YumMetadata metadata = YumMetadataBuilder.build(packages);

      // Write the referenced files first and repomd.xml last so clients never see a repomd
      // pointing at metadata that has not been written yet.
      metadata.files().values().stream()
          .filter(file -> !REPOMD_PATH.equals(file.path()))
          .forEach(file -> put(file));
      put(metadata.file(REPOMD_PATH));

      log.debug("Rebuilt Yum metadata for repository {}: {} package(s), revision {}",
          getRepository().getName(), packages.size(), metadata.revision());
    }
  }

  @Override
  public void ensureMetadata() throws IOException {
    Optional<FluentAsset> repomd = content.find(REPOMD_PATH);
    if (repomd.isEmpty()) {
      rebuild();
      return;
    }

    String stored = repomd.get().attributes().get(REVISION_ATTRIBUTE, String.class);
    String current = currentRevision();
    if (stored == null || !stored.equals(current)) {
      log.debug("Yum metadata revision changed for repository {} (stored={}, current={})",
          getRepository().getName(), stored, current);
      rebuild();
    }
  }

  private void put(final YumMetadataFile file) {
    try {
      Map<String, Object> attributes = REPOMD_PATH.equals(file.path())
          ? Map.of(REVISION_ATTRIBUTE, revisionOfCurrentBuild(file))
          : Map.of();
      content.putGenerated(file.path(), file.content(), file.contentType(), attributes);
    }
    catch (IOException e) {
      throw new IllegalStateException("Unable to store Yum metadata file " + file.path(), e);
    }
  }

  private String revisionOfCurrentBuild(final YumMetadataFile repomd) {
    // The revision is also embedded in repomd.xml; recomputing it from the RPM assets keeps
    // the attribute self-consistent without having to parse the generated XML.
    return currentRevision();
  }

  private List<RpmPackage> loadPackages() {
    List<RpmPackage> packages = new ArrayList<>();
    Set<String> seen = ConcurrentHashMap.newKeySet();

    try (var assets = content.rpmAssets()) {
      assets.forEach(asset -> {
        String path = asset.path();
        String checksum = asset.blob().map(AssetBlob::checksums).map(map -> map.get("SHA256")).orElse(null);
        if (checksum == null || checksum.isEmpty()) {
          checksum = hash(asset);
        }
        String key = path + "|" + checksum + "|" + asset.lastUpdated();
        seen.add(key);

        RpmPackage rpm = packageCache.get(key);
        if (rpm == null) {
          rpm = parse(asset, checksum);
          if (rpm != null) {
            packageCache.put(key, rpm);
          }
        }
        if (rpm != null) {
          packages.add(rpm);
        }
      });
    }

    packageCache.keySet().retainAll(seen);
    return packages;
  }

  private RpmPackage parse(final FluentAsset asset, final String checksum) {
    long size = asset.blob().map(AssetBlob::blobSize).orElse(0L);
    try (Content download = asset.download()) {
      RpmPackage rpm = RpmHeaderParser.parse(download.openInputStream(), asset.path(), checksum, size);
      return rpm.withFileTime(asset.lastUpdated().toEpochSecond());
    }
    catch (IOException e) {
      log.warn("Unable to parse RPM header for {} in repository {}; it will be omitted from metadata",
          asset.path(), getRepository().getName(), e);
      return null;
    }
  }

  private String currentRevision() {
    List<String> identities = new ArrayList<>();
    try (var assets = content.rpmAssets()) {
      assets.forEach(asset -> {
        String checksum = asset.blob().map(AssetBlob::checksums).map(map -> map.get("SHA256")).orElse("");
        identities.add(checksum + ":" + asset.path());
      });
    }
    String identity = identities.stream().sorted().collect(Collectors.joining(","));
    return Hashing.sha256().hashString(identity, StandardCharsets.UTF_8).toString();
  }

  private String hash(final FluentAsset asset) {
    try (Content download = asset.download(); InputStream input = download.openInputStream()) {
      Hasher hasher = Hashing.sha256().newHasher();
      byte[] buffer = new byte[8192];
      int read;
      while ((read = input.read(buffer)) >= 0) {
        hasher.putBytes(buffer, 0, read);
      }
      return hasher.hash().toString();
    }
    catch (IOException e) {
      log.warn("Unable to hash RPM {} in repository {}", asset.path(), getRepository().getName(), e);
      return "";
    }
  }
}
