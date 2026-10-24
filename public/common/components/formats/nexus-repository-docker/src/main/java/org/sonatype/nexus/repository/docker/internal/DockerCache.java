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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.sonatype.nexus.repository.docker.internal.DockerRegistryConfig.Upstream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

  private static final ObjectMapper MAPPER = new ObjectMapper();

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

  /**
   * Summary of a Docker cache garbage collection run.
   */
  public static final class GcResult
  {
    private final int deletedFiles;

    private final long deletedBytes;

    private final int retainedFiles;

    private final long retainedBytes;

    private final int protectedFiles;

    private final boolean dryRun;

    public GcResult(
        final int deletedFiles,
        final long deletedBytes,
        final int retainedFiles,
        final long retainedBytes,
        final int protectedFiles,
        final boolean dryRun)
    {
      this.deletedFiles = deletedFiles;
      this.deletedBytes = deletedBytes;
      this.retainedFiles = retainedFiles;
      this.retainedBytes = retainedBytes;
      this.protectedFiles = protectedFiles;
      this.dryRun = dryRun;
    }

    public int deletedFiles() {
      return deletedFiles;
    }

    public long deletedBytes() {
      return deletedBytes;
    }

    public int retainedFiles() {
      return retainedFiles;
    }

    public long retainedBytes() {
      return retainedBytes;
    }

    public int protectedFiles() {
      return protectedFiles;
    }

    public boolean dryRun() {
      return dryRun;
    }
  }

  /**
   * Runs garbage collection on the Docker disk cache across all upstreams (or a specific upstream when
   * {@code upstreamFilter} is non-blank).
   *
   * @param maxSizeBytesPerUpstream maximum total bytes per upstream (0 or negative disables size limit)
   * @param maxAgeSeconds           maximum age in seconds for cache entries (0 or negative disables age limit)
   * @param dryRun                  when {@code true}, calculates the report without deleting files
   * @param upstreamFilter          optional upstream directory name; {@code null} or blank cleans all upstreams
   */
  public GcResult gc(
      final long maxSizeBytesPerUpstream,
      final long maxAgeSeconds,
      final boolean dryRun,
      final String upstreamFilter) throws IOException
  {
    Path root = config.getCacheDir();
    if (!Files.isDirectory(root)) {
      return new GcResult(0, 0L, 0, 0L, 0, dryRun);
    }

    int deletedFiles = 0;
    long deletedBytes = 0L;
    int retainedFiles = 0;
    long retainedBytes = 0L;
    int protectedFiles = 0;

    List<Path> upstreamDirs = new ArrayList<>();
    try (Stream<Path> children = Files.list(root)) {
      children.filter(Files::isDirectory).forEach(dir -> {
        if (upstreamFilter == null || upstreamFilter.isBlank()
            || dir.getFileName().toString().equalsIgnoreCase(upstreamFilter.trim())) {
          upstreamDirs.add(dir);
        }
      });
    }

    long now = System.currentTimeMillis();
    for (Path upstreamDir : upstreamDirs) {
      GcResult single = gcUpstream(upstreamDir, maxSizeBytesPerUpstream, maxAgeSeconds, dryRun, now);
      deletedFiles += single.deletedFiles();
      deletedBytes += single.deletedBytes();
      retainedFiles += single.retainedFiles();
      retainedBytes += single.retainedBytes();
      protectedFiles += single.protectedFiles();
    }

    log.info(
        "Docker cache GC (dryRun={}): deleted {} files ({} bytes), retained {} files ({} bytes), protected {} referenced entries",
        dryRun, deletedFiles, deletedBytes, retainedFiles, retainedBytes, protectedFiles);
    return new GcResult(deletedFiles, deletedBytes, retainedFiles, retainedBytes, protectedFiles, dryRun);
  }

  private GcResult gcUpstream(
      final Path upstreamDir,
      final long maxSizeBytes,
      final long maxAgeSeconds,
      final boolean dryRun,
      final long now) throws IOException
  {
    List<CachedFile> allFiles = new ArrayList<>();
    try (Stream<Path> walk = Files.walk(upstreamDir)) {
      walk.filter(Files::isRegularFile)
          .filter(p -> !p.getFileName().toString().endsWith(".meta"))
          .filter(p -> !p.getFileName().toString().endsWith(".tmp"))
          .forEach(p -> {
            Entry entry = read(p);
            if (entry != null) {
              try {
                long size = Files.size(p);
                Path rel = upstreamDir.relativize(p);
                String category = rel.getNameCount() > 0 ? rel.getName(0).toString() : "";
                boolean byTagManifest = "manifests".equals(category)
                    && rel.getNameCount() > 1
                    && "by-tag".equals(rel.getName(1).toString());
                boolean byDigestManifest = "manifests".equals(category)
                    && rel.getNameCount() > 1
                    && "by-digest".equals(rel.getName(1).toString());
                boolean blob = "blobs".equals(category);
                allFiles.add(new CachedFile(entry, size, byTagManifest, byDigestManifest, blob));
              }
              catch (IOException ignored) {
                // ignore concurrently removed files
              }
            }
          });
    }

    // 1. Determine active manifests (not expired by maxAgeSeconds) and collect referenced blob/manifest digests
    Set<String> referencedSafeDigests = new HashSet<>();
    List<CachedFile> activeManifests = new ArrayList<>();
    for (CachedFile cf : allFiles) {
      if (cf.byTagManifest || cf.byDigestManifest) {
        boolean expired = maxAgeSeconds > 0 && (now - cf.entry.cachedAt()) > maxAgeSeconds * 1000L;
        if (!expired) {
          // If only size limit is used (maxAgeSeconds <= 0), by-tag manifests are treated as roots of live references
          if (maxAgeSeconds > 0 || cf.byTagManifest) {
            activeManifests.add(cf);
          }
        }
      }
    }

    for (CachedFile manifest : activeManifests) {
      collectReferencedDigests(upstreamDir, manifest.entry.file(), referencedSafeDigests, 0);
    }

    int deletedFiles = 0;
    long deletedBytes = 0L;
    int protectedFiles = 0;
    List<CachedFile> survivors = new ArrayList<>();

    // 2. Evict entries exceeding maxAgeSeconds unless protected by an active manifest reference
    for (CachedFile cf : allFiles) {
      boolean expired = maxAgeSeconds > 0 && (now - cf.entry.cachedAt()) > maxAgeSeconds * 1000L;
      if (expired) {
        if (isReferenced(cf, referencedSafeDigests)) {
          protectedFiles++;
          survivors.add(cf);
        }
        else {
          deleteEntry(cf.entry.file(), dryRun);
          deletedFiles++;
          deletedBytes += cf.size;
        }
      }
      else {
        survivors.add(cf);
      }
    }

    // 3. Enforce maxSizeBytes using LRU (oldest cachedAt first), evicting unreferenced entries before referenced ones
    if (maxSizeBytes > 0) {
      long currentBytes = survivors.stream().mapToLong(cf -> cf.size).sum();
      if (currentBytes > maxSizeBytes) {
        survivors.sort(Comparator.comparingLong(cf -> cf.entry.cachedAt()));
        List<CachedFile> keptAfterLru = new ArrayList<>();
        for (CachedFile cf : survivors) {
          if (currentBytes > maxSizeBytes) {
            if (isReferenced(cf, referencedSafeDigests)) {
              protectedFiles++;
              keptAfterLru.add(cf);
            }
            else {
              deleteEntry(cf.entry.file(), dryRun);
              deletedFiles++;
              deletedBytes += cf.size;
              currentBytes -= cf.size;
            }
          }
          else {
            keptAfterLru.add(cf);
          }
        }
        survivors = keptAfterLru;
      }
    }

    int retainedFiles = survivors.size();
    long retainedBytes = survivors.stream().mapToLong(cf -> cf.size).sum();
    return new GcResult(deletedFiles, deletedBytes, retainedFiles, retainedBytes, protectedFiles, dryRun);
  }

  private void collectReferencedDigests(
      final Path upstreamDir,
      final Path manifestFile,
      final Set<String> referencedSafeDigests,
      final int depth)
  {
    if (depth > 3 || !Files.isRegularFile(manifestFile)) {
      return;
    }
    try (InputStream in = Files.newInputStream(manifestFile)) {
      JsonNode root = MAPPER.readTree(in);
      if (root == null || !root.isObject()) {
        return;
      }
      JsonNode configNode = root.get("config");
      if (configNode != null && configNode.hasNonNull("digest")) {
        referencedSafeDigests.add(safeDigest(configNode.get("digest").asText()));
      }
      JsonNode layers = root.get("layers");
      if (layers != null && layers.isArray()) {
        for (JsonNode layer : layers) {
          if (layer.hasNonNull("digest")) {
            referencedSafeDigests.add(safeDigest(layer.get("digest").asText()));
          }
        }
      }
      JsonNode manifests = root.get("manifests");
      if (manifests != null && manifests.isArray()) {
        for (JsonNode child : manifests) {
          if (child.hasNonNull("digest")) {
            String safe = safeDigest(child.get("digest").asText());
            if (referencedSafeDigests.add(safe)) {
              Path childPath = upstreamDir.resolve("manifests").resolve("by-digest").resolve(safe);
              collectReferencedDigests(upstreamDir, childPath, referencedSafeDigests, depth + 1);
            }
          }
        }
      }
    }
    catch (Exception e) {
      log.debug("Could not parse manifest {} for GC reference scanning", manifestFile, e);
    }
  }

  private static boolean isReferenced(final CachedFile cf, final Set<String> referencedSafeDigests) {
    if (!cf.blob && !cf.byDigestManifest) {
      return false;
    }
    String fileName = cf.entry.file().getFileName().toString();
    if (referencedSafeDigests.contains(fileName)) {
      return true;
    }
    return cf.entry.digest() != null && referencedSafeDigests.contains(safeDigest(cf.entry.digest()));
  }

  private static void deleteEntry(final Path file, final boolean dryRun) throws IOException {
    if (!dryRun) {
      Files.deleteIfExists(file);
      Files.deleteIfExists(metaPath(file));
    }
  }

  private static final class CachedFile
  {
    final Entry entry;

    final long size;

    final boolean byTagManifest;

    final boolean byDigestManifest;

    final boolean blob;

    CachedFile(
        final Entry entry,
        final long size,
        final boolean byTagManifest,
        final boolean byDigestManifest,
        final boolean blob)
    {
      this.entry = entry;
      this.size = size;
      this.byTagManifest = byTagManifest;
      this.byDigestManifest = byDigestManifest;
      this.blob = blob;
    }
  }
}
