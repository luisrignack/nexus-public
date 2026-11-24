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
package org.sonatype.nexus.repository.pub;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PubPackageMetadataTest
{
  private static final String PUBSPEC = """
      name: http
      version: 1.2.0
      description: A flexible HTTP client
      environment:
        sdk: flutter
      dependencies:
        sdk: core
      """;

  @Test
  void readsPubspecAndComputesArchiveChecksum() throws Exception {
    byte[] archive = archive("http/pubspec.yaml", PUBSPEC);

    PubPackageMetadata metadata = PubPackageMetadata.read(archive);

    assertThat(metadata.getName(), equalTo("http"));
    assertThat(metadata.getVersion(), equalTo("1.2.0"));
    assertThat(metadata.getPubspecJson(), containsString("\"name\":\"http\""));
    assertThat(metadata.getSha256(), equalTo(sha256(archive)));
  }

  @Test
  void rejectsArchivesWithoutPubspec() throws Exception {
    byte[] archive = archive("lib/main.dart", "void main() {}");

    assertThrows(IllegalArgumentException.class, () -> PubPackageMetadata.read(archive));
  }

  private static byte[] archive(final String entryName, final String content) throws Exception {
    ByteArrayOutputStream tarBytes = new ByteArrayOutputStream();
    try (TarArchiveOutputStream tar = new TarArchiveOutputStream(tarBytes)) {
      tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
      byte[] body = content.getBytes(StandardCharsets.UTF_8);
      TarArchiveEntry entry = new TarArchiveEntry(entryName);
      entry.setSize(body.length);
      tar.putArchiveEntry(entry);
      tar.write(body);
      tar.closeArchiveEntry();
    }
    ByteArrayOutputStream gzipped = new ByteArrayOutputStream();
    try (GzipCompressorOutputStream gzip = new GzipCompressorOutputStream(gzipped)) {
      gzip.write(tarBytes.toByteArray());
    }
    return gzipped.toByteArray();
  }

  private static String sha256(final byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }
}
