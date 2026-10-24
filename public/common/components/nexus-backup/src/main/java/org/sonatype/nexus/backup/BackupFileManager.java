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
package org.sonatype.nexus.backup;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Filesystem support for the consistent backup task: recursive verified copy of trees and
 * manifest writing.
 *
 * <p>Every copied file is hashed with SHA-256 while it is copied. When a hash log writer is
 * provided, each file is recorded in {@code sha256sum} format
 * ({@code <hash>  <relative path>}) so a restore can re-verify the whole tree. An aggregate
 * hash over all {@code <hash>  <relative path>} lines (sorted) is also returned for a quick
 * integrity check of the complete backup.</p>
 */
public class BackupFileManager
{
  public static final String MANIFEST_FILE = "backup-manifest.json";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /**
   * Result of copying a tree.
   */
  public static final class BackupStats
  {
    private final long fileCount;

    private final long totalBytes;

    private final String aggregateSha256;

    public BackupStats(final long fileCount, final long totalBytes, final String aggregateSha256) {
      this.fileCount = fileCount;
      this.totalBytes = totalBytes;
      this.aggregateSha256 = aggregateSha256;
    }

    public long fileCount() {
      return fileCount;
    }

    public long totalBytes() {
      return totalBytes;
    }

    public String aggregateSha256() {
      return aggregateSha256;
    }
  }

  /**
   * Copies every regular file from {@code source} to {@code target} preserving relative paths,
   * hashing each file with SHA-256 and optionally appending {@code <hash>  <relative path>}
   * lines to {@code hashLog}.
   */
  public BackupStats copyTree(
      final Path source,
      final Path target,
      @Nullable final BufferedWriter hashLog,
      @Nullable final org.sonatype.nexus.scheduling.Cancelable cancelable) throws IOException
  {
    List<Path> files;
    try (Stream<Path> walk = Files.walk(source)) {
      files = walk
          .filter(Files::isRegularFile)
          .sorted()
          .toList();
    }

    long fileCount = 0;
    long totalBytes = 0;
    List<String> hashLines = new java.util.ArrayList<>(files.size());

    for (Path file : files) {
      if (cancelable != null && cancelable.isCanceled()) {
        throw new IOException("Backup canceled");
      }

      Path relative = source.relativize(file);
      Path destination = target.resolve(relative.toString());

      Files.createDirectories(destination.getParent());
      String hash = copyAndHash(file, destination);
      long size = Files.size(destination);

      fileCount++;
      totalBytes += size;
      hashLines.add(hash + "  " + relative.toString().replace('\\', '/'));
    }

    if (hashLog != null) {
      for (String line : hashLines) {
        hashLog.write(line);
        hashLog.newLine();
      }
      hashLog.flush();
    }

    return new BackupStats(fileCount, totalBytes, aggregateSha256(hashLines));
  }

  /**
   * Writes the manifest JSON document into the backup directory.
   *
   * @return the manifest file path
   */
  public Path writeManifest(final Path backupDir, final ObjectNode manifest) throws IOException {
    Path manifestFile = backupDir.resolve(MANIFEST_FILE);
    MAPPER.writerWithDefaultPrettyPrinter().writeValue(manifestFile.toFile(), manifest);
    return manifestFile;
  }

  /**
   * Aggregate hash over sorted {@code <hash>  <relative path>} lines.
   */
  public static String aggregateSha256(final List<String> hashLines) {
    String joined = hashLines.stream()
        .sorted(Comparator.naturalOrder())
        .reduce((a, b) -> a + "\n" + b)
        .orElse("");
    MessageDigest digest = newDigest();
    digest.update(joined.getBytes(StandardCharsets.UTF_8));
    return hex(digest.digest());
  }

  /**
   * Copies {@code source} to {@code destination} and returns the SHA-256 of the content.
   */
  private static String copyAndHash(final Path source, final Path destination) throws IOException {
    MessageDigest digest = newDigest();
    try (InputStream input = Files.newInputStream(source)) {
      java.io.OutputStream output = Files.newOutputStream(destination);
      try {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
          digest.update(buffer, 0, read);
          output.write(buffer, 0, read);
        }
      }
      finally {
        output.close();
      }
    }
    catch (IOException e) {
      Files.deleteIfExists(destination);
      throw e;
    }
    return hex(digest.digest());
  }

  private static MessageDigest newDigest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    }
    catch (Exception e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  private static String hex(final byte[] bytes) {
    StringBuilder result = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      result.append(String.format("%02x", b));
    }
    return result.toString();
  }
}
