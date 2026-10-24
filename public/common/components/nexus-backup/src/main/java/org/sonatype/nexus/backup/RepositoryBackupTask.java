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
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;

import org.sonatype.nexus.backup.BackupFileManager.BackupStats;
import org.sonatype.nexus.blobstore.api.BlobStore;
import org.sonatype.nexus.blobstore.api.BlobStoreManager;
import org.sonatype.nexus.common.app.ApplicationDirectories;
import org.sonatype.nexus.datastore.api.DataStore;
import org.sonatype.nexus.datastore.api.DataStoreManager;
import org.sonatype.nexus.scheduling.Cancelable;
import org.sonatype.nexus.scheduling.TaskSupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sonatype.nexus.datastore.api.DataStoreManager.DEFAULT_DATASTORE_NAME;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Backs up the datastore (logical snapshot) and the file-based blob stores into a timestamped
 * directory with a {@code backup-manifest.json} manifest and per-blob-store {@code sha256sum}
 * files for restore verification.
 *
 * <p>For an embedded H2 datastore the snapshot is taken with the online H2 BACKUP command. For an
 * external PostgreSQL datastore a logical {@code pg_dump} must be scheduled separately; the task
 * records that requirement in the manifest and still backs up the blob stores.</p>
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class RepositoryBackupTask
    extends TaskSupport
    implements Cancelable
{
  private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final DataStoreManager dataStoreManager;

  private final BlobStoreManager blobStoreManager;

  private final ApplicationDirectories applicationDirectories;

  private final BackupFileManager backupFileManager;

  @Inject
  public RepositoryBackupTask(
      final DataStoreManager dataStoreManager,
      final BlobStoreManager blobStoreManager,
      final ApplicationDirectories applicationDirectories,
      final BackupFileManager backupFileManager)
  {
    this.dataStoreManager = checkNotNull(dataStoreManager);
    this.blobStoreManager = checkNotNull(blobStoreManager);
    this.applicationDirectories = checkNotNull(applicationDirectories);
    this.backupFileManager = checkNotNull(backupFileManager);
  }

  @Override
  public String getMessage() {
    return "Backing up datastore and blob stores";
  }

  @Override
  protected Object execute() throws Exception {
    String location = checkNotNull(
        getConfiguration().getString(RepositoryBackupTaskDescriptor.LOCATION), "Backup location not configured");
    boolean includeBlobs = getConfiguration()
        .getBoolean(RepositoryBackupTaskDescriptor.INCLUDE_BLOBS, true);

    File backupRoot = applicationDirectories.getWorkDirectory(location);
    Path backupDir = backupRoot.toPath().resolve("backup-" + LocalDateTime.now().format(TIMESTAMP));
    Files.createDirectories(backupDir);

    log.info("Starting backup of datastore and blob stores to {}", backupDir);

    ObjectNode manifest = MAPPER.createObjectNode();
    manifest.put("tool", "nexus-repository-backup");
    manifest.put("startedAt", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));

    manifest.set("datastore", backupDatastore(backupDir));

    ArrayNode skippedBlobStores = MAPPER.createArrayNode();
    if (includeBlobs) {
      manifest.set("blobStores", backupBlobStores(backupDir, skippedBlobStores));
    }
    else {
      manifest.put("blobStoresSkipped", true);
    }
    manifest.set("skippedBlobStores", skippedBlobStores);

    manifest.put("finishedAt", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));

    Path manifestFile = backupFileManager.writeManifest(backupDir, manifest);
    log.info("Backup completed: {}", manifestFile);
    return null;
  }

  private ObjectNode backupDatastore(final Path backupDir) throws Exception {
    ObjectNode datastore = MAPPER.createObjectNode();
    datastore.put("name", DEFAULT_DATASTORE_NAME);
    datastore.put("type", isPostgreSql() ? "PostgreSQL" : "H2");

    if (isPostgreSql()) {
      datastore.put("requiresExternalDump", true);
      datastore.put("note", "Schedule a logical pg_dump of the PostgreSQL database; "
          + "this task does not take a database snapshot for external datastores");
      log.warn("External PostgreSQL datastore detected: schedule pg_dump separately; "
          + "only blob stores are included in this backup");
      return datastore;
    }

    Optional<DataStore<?>> store = dataStoreManager.get(DEFAULT_DATASTORE_NAME);
    if (store.isEmpty()) {
      datastore.put("error", "Datastore '" + DEFAULT_DATASTORE_NAME + "' not found");
      return datastore;
    }

    Path dbDir = backupDir.resolve("db");
    Files.createDirectories(dbDir);
    Path backupFile = dbDir.resolve(DEFAULT_DATASTORE_NAME + "-"
        + LocalDateTime.now().format(TIMESTAMP) + ".zip");

    long start = System.currentTimeMillis();
    store.get().backup(backupFile.toAbsolutePath().toString());
    log.info("Datastore snapshot written to {} in {} ms", backupFile, System.currentTimeMillis() - start);

    datastore.put("backupFile", backupDir.relativize(backupFile).toString());
    datastore.put("sha256", BackupFileManager.aggregateSha256(List.of(sha256Line(backupDir, backupFile))));
    return datastore;
  }

  private ArrayNode backupBlobStores(final Path backupDir, final ArrayNode skipped) throws IOException {
    ArrayNode blobStores = MAPPER.createArrayNode();
    Path blobsRoot = applicationDirectories.getWorkDirectory("blobs").toPath();

    for (BlobStore blobStore : blobStoreManager.browse()) {
      if (isCanceled()) {
        throw new IOException("Backup canceled");
      }

      String name = blobStore.getBlobStoreConfiguration().getName();
      Path source = blobsRoot.resolve(name);
      if (!Files.isDirectory(source)) {
        log.warn("Blob store '{}' has no file storage under {}; skipping (non-file blob store?)", name, source);
        skipped.add(name);
        continue;
      }

      Path target = backupDir.resolve("blobs").resolve(name);
      Path hashesFile = backupDir.resolve("blobs").resolve(name + ".sha256sum");
      Files.createDirectories(target);

      try (BufferedWriter hashLog = Files.newBufferedWriter(hashesFile, StandardCharsets.UTF_8)) {
        BackupStats stats = backupFileManager.copyTree(source, target, hashLog, this);
        ObjectNode entry = blobStores.addObject();
        entry.put("name", name);
        entry.put("fileCount", stats.fileCount());
        entry.put("totalBytes", stats.totalBytes());
        entry.put("sha256", stats.aggregateSha256());
        entry.put("hashesFile", backupDir.relativize(hashesFile).toString());
        log.info("Backed up blob store '{}': {} files, {} bytes", name, stats.fileCount(), stats.totalBytes());
      }
    }
    return blobStores;
  }

  private static String sha256Line(final Path backupDir, final Path file) throws IOException {
    MessageDigestHolder holder = new MessageDigestHolder();
    try (java.io.InputStream input = Files.newInputStream(file)) {
      byte[] buffer = new byte[8192];
      int read;
      while ((read = input.read(buffer)) != -1) {
        holder.digest.update(buffer, 0, read);
      }
    }
    StringBuilder hex = new StringBuilder();
    for (byte b : holder.digest.digest()) {
      hex.append(String.format("%02x", b));
    }
    return hex + "  " + backupDir.relativize(file).toString().replace('\\', '/');
  }

  private static final class MessageDigestHolder
  {
    final java.security.MessageDigest digest = newDigest();

    private static java.security.MessageDigest newDigest() {
      try {
        return java.security.MessageDigest.getInstance("SHA-256");
      }
      catch (Exception e) {
        throw new IllegalStateException("SHA-256 not available", e);
      }
    }
  }

  private static boolean isPostgreSql() {
    String jdbcUrl = System.getProperty("nexus.datastore.nexus.jdbcUrl",
        System.getenv().getOrDefault("NEXUS_DATASTORE_NEXUS_JDBCURL", ""));
    return jdbcUrl != null && jdbcUrl.contains("jdbc:postgresql");
  }
}
