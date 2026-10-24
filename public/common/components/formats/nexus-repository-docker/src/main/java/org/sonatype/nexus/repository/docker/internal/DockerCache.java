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
package org.sonatype.nexus.repository.docker.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Properties;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.sonatype.nexus.repository.docker.internal.DockerRegistryConfig.Upstream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Disk cache for Docker manifests, tags and blobs.
 *
 * <p>Blobs and manifests requested by digest are immutable and cached forever. Manifests requested by tag and tag
 * lists have a configurable TTL, because tags are mutable. Every cache entry has a small {@code .meta} sidecar with
 * the response headers needed to serve it later.</p>
 */
@Component
@Singleton
public class DockerCache
{
  private static final Logger log = LoggerFactory.getLogger(DockerCache.class);

  private final DockerRegistryConfig config;

  @Inject
  public DockerCache(final DockerRegistryConfig config) {
    this.config = config;
  }

  public Entry blob(final Upstream upstream, final String digest) {
    return read(blobPath(upstream, digest));
  }

  public Entry manifestByDigest(final Upstream upstream, final String digest) {
    return read(manifestDigestPath(upstream, digest));
  }

  public Entry manifestByTag(final Upstream upstream, final String repository, final String reference) {
    return read(manifestTagPath(upstream, repository, reference));
  }

  public Entry tags(final Upstream upstream, final String repository) {
    return read(tagsPath(upstream, repository));
  }

  public boolean isFresh(final Entry entry, final long ttlSeconds) {
    return entry != null && System.currentTimeMillis() - entry.cachedAt() < ttlSeconds * 1000L;
  }

  /**
   * Stores a blob that has just been downloaded completely.
   */
  public Entry storeBlob(
      final Upstream upstream,
      final String digest,
      final String contentType,
      final String etag,
      final Path downloaded) throws IOException
  {
    Path target = blobPath(upstream, digest);
    return store(target, contentType, digest, etag, downloaded);
  }

  public Entry storeManifest(
      final Upstream upstream,
      final String repository,
      final String reference,
      final boolean byDigest,
      final String contentType,
      final String digest,
      final String etag,
      final Path downloaded) throws IOException
  {
    Path target = byDigest ? manifestDigestPath(upstream, reference)
        : manifestTagPath(upstream, repository, reference);
    return store(target, contentType, digest, etag, downloaded);
  }

  public Entry storeTags(
      final Upstream upstream,
      final String repository,
      final String contentType,
      final Path downloaded) throws IOException
  {
    return store(tagsPath(upstream, repository), contentType, null, null, downloaded);
  }

  /**
   * Creates the cache directory, failing early when the base directory is not writable.
   */
  public void initialize() throws IOException {
    Files.createDirectories(config.getCacheDir());
    log.info("Docker cache directory: {}", config.getCacheDir());
  }

  /**
   * A cached response.
   */
  public static final class Entry
  {
    private final Path file;

    private final String contentType;

    private final String digest;

    private final String etag;

    private final long cachedAt;

    Entry(final Path file, final String contentType, final String digest, final String etag, final long cachedAt) {
      this.file = file;
      this.contentType = contentType;
      this.digest = digest;
      this.etag = etag;
      this.cachedAt = cachedAt;
    }

    public Path file() {
      return file;
    }

    public String contentType() {
      return contentType;
    }

    public String digest() {
      return digest;
    }

    public String etag() {
      return etag;
    }

    public long cachedAt() {
      return cachedAt;
    }
  }

  private Entry store(
      final Path target,
      final String contentType,
      final String digest,
      final String etag,
      final Path downloaded) throws IOException
  {
    Files.createDirectories(target.getParent());
    try {
      Files.move(downloaded, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
    catch (IOException e) {
      Files.move(downloaded, target, StandardCopyOption.REPLACE_EXISTING);
    }

    String effectiveDigest = digest;
    String effectiveEtag = etag;
    if (effectiveDigest == null) {
      effectiveDigest = sha256(target);
    }
    if (effectiveEtag == null) {
      effectiveEtag = '"' + effectiveDigest + '"';
    }

    Entry entry = new Entry(target, contentType, effectiveDigest, effectiveEtag, System.currentTimeMillis());
    writeMeta(entry);
    return entry;
  }

  private Entry read(final Path file) {
    try {
      if (!Files.isRegularFile(file)) {
        return null;
      }
      Path meta = metaPath(file);
      if (!Files.isRegularFile(meta)) {
        return new Entry(file, "application/octet-stream", null, null, Files.getLastModifiedTime(file).toMillis());
      }
      Properties properties = new Properties();
      try (InputStream input = Files.newInputStream(meta)) {
        properties.load(input);
      }
      return new Entry(
          file,
          properties.getProperty("contentType"),
          emptyToNull(properties.getProperty("digest")),
          emptyToNull(properties.getProperty("etag")),
          Long.parseLong(properties.getProperty("cachedAt", "0")));
    }
    catch (Exception e) {
      log.debug("Ignoring unreadable Docker cache entry {}", file, e);
      return null;
    }
  }

  private void writeMeta(final Entry entry) throws IOException {
    Properties properties = new Properties();
    properties.setProperty("contentType", entry.contentType() != null ? entry.contentType() : "");
    properties.setProperty("digest", entry.digest() != null ? entry.digest() : "");
    properties.setProperty("etag", entry.etag() != null ? entry.etag() : "");
    properties.setProperty("cachedAt", Long.toString(entry.cachedAt()));
    try (OutputStream output = Files.newOutputStream(metaPath(entry.file()))) {
      properties.store(output, null);
    }
  }

  private Path blobPath(final Upstream upstream, final String digest) {
    return config.getCacheDir().resolve(upstream.name()).resolve("blobs").resolve(safeDigest(digest));
  }

  private Path manifestDigestPath(final Upstream upstream, final String digest) {
    return config.getCacheDir().resolve(upstream.name()).resolve("manifests").resolve("by-digest")
        .resolve(safeDigest(digest));
  }

  private Path manifestTagPath(final Upstream upstream, final String repository, final String reference) {
    return config.getCacheDir().resolve(upstream.name()).resolve("manifests").resolve("by-tag")
        .resolve(safePath(repository)).resolve(safeSegment(reference));
  }

  private Path tagsPath(final Upstream upstream, final String repository) {
    return config.getCacheDir().resolve(upstream.name()).resolve("tags").resolve(safePath(repository))
        .resolve("list");
  }

  private static Path metaPath(final Path file) {
    return file.resolveSibling(file.getFileName() + ".meta");
  }

  private static String safeDigest(final String digest) {
    return digest.replace(':', '_').replace('/', '_');
  }

  private static String safePath(final String repository) {
    StringBuilder result = new StringBuilder();
    for (String segment : repository.split("/")) {
      if (segment.isEmpty()) {
        continue;
      }
      if (result.length() > 0) {
        result.append('/');
      }
      result.append(safeSegment(segment));
    }
    return result.length() > 0 ? result.toString() : "_";
  }

  private static String safeSegment(final String value) {
    String safe = value.replaceAll("[^A-Za-z0-9._-]", "_");
    if (safe.isEmpty() || ".".equals(safe) || "..".equals(safe)) {
      return "_";
    }
    return safe;
  }

  private static String sha256(final Path file) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (InputStream input = Files.newInputStream(file)) {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
          digest.update(buffer, 0, read);
        }
      }
      StringBuilder value = new StringBuilder("sha256:");
      for (byte b : digest.digest()) {
        value.append(String.format("%02x", b));
      }
      return value.toString();
    }
    catch (Exception e) {
      throw new IOException("Could not calculate digest", e);
    }
  }

  private static String emptyToNull(final String value) {
    return value == null || value.isEmpty() ? null : value;
  }
}
