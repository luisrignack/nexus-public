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
