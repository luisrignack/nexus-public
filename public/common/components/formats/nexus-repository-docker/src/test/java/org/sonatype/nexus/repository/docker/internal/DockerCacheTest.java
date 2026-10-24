/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark. The
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.repository.docker.internal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

import org.sonatype.nexus.repository.docker.internal.DockerCache.Entry;
import org.sonatype.nexus.repository.docker.internal.DockerRegistryConfig.Upstream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class DockerCacheTest
{
  @TempDir
  Path cacheDir;

  private DockerCache cache;

  private Upstream upstream;

  @BeforeEach
  void setUp() {
    DockerRegistryConfig config = new DockerRegistryConfig(
        true, "dockerhub=https://docker.m.daocloud.io", "dockerhub", cacheDir.toString(), 300, 300, 60, true);
    cache = new DockerCache(config);
    upstream = config.getUpstreams().get("dockerhub");
  }

  @Test
  void storesAndReadsBlobsByDigest() throws Exception {
    String digest = "sha256:" + repeat('a', 64);
    Path downloaded = tempFile("layer");

    Entry stored = cache.storeBlob(upstream, digest, "application/x-gzip", "\"etag\"", downloaded);
    Entry read = cache.blob(upstream, digest);

    assertThat(stored.digest()).isEqualTo(digest);
    assertThat(read).isNotNull();
    assertThat(read.contentType()).isEqualTo("application/x-gzip");
    assertThat(read.etag()).isEqualTo("\"etag\"");
    assertThat(Files.readString(read.file())).isEqualTo("layer");
  }

  @Test
  void computesDigestWhenUpstreamDoesNotSendOne() throws Exception {
    Path downloaded = tempFile("manifest");

    Entry stored = cache.storeTags(upstream, "library/nginx", "application/json", downloaded);

    assertThat(stored.digest()).isEqualTo("sha256:" + sha256("manifest"));
    assertThat(stored.etag()).isEqualTo("\"" + stored.digest() + "\"");
  }

  @Test
  void separatesTagAndDigestManifestCaches() throws Exception {
    String digest = "sha256:" + repeat('b', 64);

    cache.storeManifest(upstream, "library/nginx", "latest", false, "application/json", null, null,
        tempFile("by-tag"));
    cache.storeManifest(upstream, "library/nginx", digest, true, "application/json", digest, null,
        tempFile("by-digest"));

    assertThat(cache.manifestByTag(upstream, "library/nginx", "latest")).isNotNull();
    assertThat(cache.manifestByDigest(upstream, digest)).isNotNull();
    assertThat(Files.readString(cache.manifestByTag(upstream, "library/nginx", "latest").file()))
        .isEqualTo("by-tag");
    assertThat(Files.readString(cache.manifestByDigest(upstream, digest).file())).isEqualTo("by-digest");
  }

  @Test
  void reportsFreshnessBasedOnTtl() throws Exception {
    Entry entry = cache.storeTags(upstream, "library/nginx", "application/json", tempFile("tags"));

    assertThat(cache.isFresh(entry, 300)).isTrue();
    assertThat(cache.isFresh(entry, 0)).isFalse();
    assertThat(cache.isFresh(null, 300)).isFalse();
  }

  @Test
  void sanitizesRepositoryNamesInPaths() throws Exception {
    Entry entry = cache.storeTags(upstream, "../../etc/passwd", "application/json", tempFile("tags"));

    assertThat(entry.file().normalize().startsWith(cacheDir.normalize())).isTrue();
    assertThat(entry.file().getFileName().toString()).isEqualTo("list");
    assertThat(cache.tags(upstream, "../../etc/passwd")).isNotNull();
  }

  @Test
  void returnsNullForMissingEntries() {
    assertThat(cache.blob(upstream, "sha256:" + repeat('c', 64))).isNull();
    assertThat(cache.tags(upstream, "missing")).isNull();
  }

  @Test
  void gcProtectsBlobsReferencedByRecentManifestDuringAgeAndLruEviction() throws Exception {
    String referencedBlobDigest = "sha256:" + repeat('1', 64);
    String unreferencedBlobDigest = "sha256:" + repeat('2', 64);

    Entry refBlob = cache.storeBlob(upstream, referencedBlobDigest, "application/octet-stream", "\"e1\"",
        tempFile("referenced-layer-payload"));
    Entry unrefBlob = cache.storeBlob(upstream, unreferencedBlobDigest, "application/octet-stream", "\"e2\"",
        tempFile("unreferenced-layer-payload"));

    // Backdate both blobs by 10 hours so they appear expired
    backdateMeta(refBlob.file(), System.currentTimeMillis() - 10 * 3600_000L);
    backdateMeta(unrefBlob.file(), System.currentTimeMillis() - 10 * 3600_000L);

    // Store a recent manifest by tag that references refBlob
    String manifestJson = "{\"schemaVersion\":2,\"config\":{\"digest\":\"" + referencedBlobDigest
        + "\"},\"layers\":[{\"digest\":\"" + referencedBlobDigest + "\"}]}";
    cache.storeManifest(upstream, "library/alpine", "latest", false,
        "application/vnd.docker.distribution.manifest.v2+json", null, null, tempFile(manifestJson));

    // Dry run first: nothing deleted on disk
    DockerCache.GcResult dryResult = cache.gc(0L, 3600L, true, null);
    assertThat(dryResult.dryRun()).isTrue();
    assertThat(dryResult.deletedFiles()).isEqualTo(1);
    assertThat(dryResult.protectedFiles()).isEqualTo(1);
    assertThat(cache.blob(upstream, unreferencedBlobDigest)).isNotNull();

    // Real GC run with maxAge = 1 hour
    DockerCache.GcResult result = cache.gc(0L, 3600L, false, null);
    assertThat(result.dryRun()).isFalse();
    assertThat(result.deletedFiles()).isEqualTo(1);
    assertThat(result.protectedFiles()).isEqualTo(1);
    assertThat(cache.blob(upstream, unreferencedBlobDigest)).isNull();
    assertThat(cache.blob(upstream, referencedBlobDigest)).isNotNull();
  }

  @Test
  void gcEvictsOldestUnreferencedEntriesWhenExceedingMaxSize() throws Exception {
    String oldDigest = "sha256:" + repeat('a', 64);
    String newDigest = "sha256:" + repeat('b', 64);

    Entry oldBlob = cache.storeBlob(upstream, oldDigest, "application/octet-stream", "\"eold\"",
        tempFile(repeat('x', 200)));
    backdateMeta(oldBlob.file(), System.currentTimeMillis() - 60_000L);

    Entry newBlob = cache.storeBlob(upstream, newDigest, "application/octet-stream", "\"enew\"",
        tempFile(repeat('y', 200)));

    // Limit to 250 bytes: oldBlob (200B) should be evicted so only newBlob (200B) remains
    DockerCache.GcResult result = cache.gc(250L, 0L, false, "dockerhub");
    assertThat(result.deletedFiles()).isEqualTo(1);
    assertThat(result.retainedFiles()).isEqualTo(1);
    assertThat(cache.blob(upstream, oldDigest)).isNull();
    assertThat(cache.blob(upstream, newDigest)).isNotNull();
  }

  private static void backdateMeta(final Path dataFile, final long cachedAtMillis) throws IOException {
    Path meta = dataFile.resolveSibling(dataFile.getFileName() + ".meta");
    java.util.Properties props = new java.util.Properties();
    try (java.io.InputStream in = Files.newInputStream(meta)) {
      props.load(in);
    }
    props.setProperty("cachedAt", Long.toString(cachedAtMillis));
    try (java.io.OutputStream out = Files.newOutputStream(meta)) {
      props.store(out, null);
    }
  }

  private Path tempFile(final String content) throws IOException {
    Path file = Files.createTempFile(cacheDir, "download-", ".tmp");
    Files.writeString(file, content);
    return file;
  }

  private static String repeat(final char value, final int count) {
    return String.valueOf(value).repeat(count);
  }

  private static String sha256(final String value) throws Exception {
    byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    StringBuilder result = new StringBuilder();
    for (byte b : hash) {
      result.append(String.format("%02x", b));
    }
    return result.toString();
  }
}
