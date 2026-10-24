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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.sonatype.nexus.scheduling.Cancelable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class BackupFileManagerTest
{
  private final BackupFileManager underTest = new BackupFileManager();

  @TempDir
  Path sourceDir;

  @TempDir
  Path targetRoot;

  @Test
  void copyTreeCopiesAllFilesAndComputesAggregateHash() throws Exception {
    Files.createDirectories(sourceDir.resolve("sub/dir"));
    Files.writeString(sourceDir.resolve("a.properties"), "size=3");
    Files.writeString(sourceDir.resolve("sub/blob1"), "abc");
    Files.writeString(sourceDir.resolve("sub/dir/blob2"), "hello");

    Path target = targetRoot.resolve("backup/blobs/default");
    BackupFileManager.BackupStats stats = underTest.copyTree(sourceDir, target, null, null);

    assertThat(stats.fileCount()).isEqualTo(3);
    assertThat(stats.totalBytes()).isEqualTo(6 + 3 + 5);
    assertThat(stats.aggregateSha256()).hasSize(64);

    assertThat(Files.readString(target.resolve("a.properties"))).isEqualTo("size=3");
    assertThat(Files.readString(target.resolve("sub/blob1"))).isEqualTo("abc");
    assertThat(Files.readString(target.resolve("sub/dir/blob2"))).isEqualTo("hello");
  }

  @Test
  void hashLogContainsSha256sumFormatLines() throws Exception {
    Files.createDirectories(sourceDir.resolve("sub"));
    Files.writeString(sourceDir.resolve("sub/blob1"), "abc");

    Path target = targetRoot.resolve("backup/blobs/default");
    Path hashesFile = targetRoot.resolve("backup/blobs/default.sha256sum");
    Files.createDirectories(hashesFile.getParent());
    try (BufferedWriter log = Files.newBufferedWriter(hashesFile, StandardCharsets.UTF_8)) {
      underTest.copyTree(sourceDir, target, log, null);
    }

    String logContent = Files.readString(hashesFile);
    // "abc" -> ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
    assertThat(logContent)
        .contains("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad  sub/blob1");
  }

  @Test
  void aggregateHashChangesWhenContentChanges() throws Exception {
    Files.writeString(sourceDir.resolve("a"), "one");
    Path target = targetRoot.resolve("t1");
    BackupFileManager.BackupStats first = underTest.copyTree(sourceDir, target, null, null);

    Files.writeString(sourceDir.resolve("a"), "two");
    Path target2 = targetRoot.resolve("t2");
    BackupFileManager.BackupStats second = underTest.copyTree(sourceDir, target2, null, null);

    assertThat(first.aggregateSha256()).isNotEqualTo(second.aggregateSha256());
  }

  @Test
  void aggregateHashIsStableAcrossFileOrder() {
    String first = BackupFileManager.aggregateSha256(java.util.List.of(
        "aa  b.txt", "bb  a.txt"));
    String second = BackupFileManager.aggregateSha256(java.util.List.of(
        "bb  a.txt", "aa  b.txt"));

    assertThat(first).isEqualTo(second);
  }

  @Test
  void manifestIsWrittenAsPrettyJson() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode manifest = mapper.createObjectNode();
    manifest.put("tool", "nexus-repository-backup");
    manifest.putArray("blobStores").addObject().put("name", "default");

    Path manifestFile = underTest.writeManifest(targetRoot, manifest);

    assertThat(manifestFile.getFileName().toString()).isEqualTo(BackupFileManager.MANIFEST_FILE);
    ObjectNode reread = (ObjectNode) mapper.readTree(manifestFile.toFile());
    assertThat(reread.get("tool").asText()).isEqualTo("nexus-repository-backup");
    assertThat(reread.get("blobStores").get(0).get("name").asText()).isEqualTo("default");
  }

  @Test
  void copyTreeStopsWhenCanceled() throws Exception {
    Files.writeString(sourceDir.resolve("a"), "data");

    Cancelable canceled = new Cancelable()
    {
      @Override
      public void cancel() {
      }

      @Override
      public boolean isCanceled() {
        return true;
      }
    };

    org.assertj.core.api.Assertions.assertThatThrownBy(
        () -> underTest.copyTree(sourceDir, targetRoot.resolve("x"), null, canceled))
        .isInstanceOf(java.io.IOException.class)
        .hasMessageContaining("canceled");
  }
}
