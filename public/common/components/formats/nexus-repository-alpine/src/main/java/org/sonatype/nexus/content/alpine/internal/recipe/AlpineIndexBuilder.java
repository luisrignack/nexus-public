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
package org.sonatype.nexus.content.alpine.internal.recipe;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

import com.google.common.io.ByteStreams;

/**
 * Builds and merges Alpine {@code APKINDEX.tar.gz} documents.
 *
 * <p>The index is a gzip-compressed tar archive that contains a single {@code APKINDEX} member
 * whose stanzas are separated by blank lines. No signature is added: the packages keep their own
 * signatures and apk accepts indexes served over HTTP(S) without index signatures.</p>
 */
public final class AlpineIndexBuilder
{
  public static final String INDEX_FILE_NAME = "APKINDEX.tar.gz";

  private static final String INDEX_ENTRY_NAME = "APKINDEX";

  private AlpineIndexBuilder() {
    // utility class
  }

  /**
   * Packs the given stanzas (each one already terminated by a blank line) into an
   * {@code APKINDEX.tar.gz}.
   */
  public static byte[] build(final List<String> stanzas) throws IOException {
    StringBuilder index = new StringBuilder();
    for (String stanza : stanzas) {
      index.append(stanza);
      if (!stanza.endsWith("\n\n")) {
        if (stanza.endsWith("\n")) {
          index.append('\n');
        }
        else {
          index.append("\n\n");
        }
      }
    }
    byte[] content = index.toString().getBytes(StandardCharsets.UTF_8);

    ByteArrayOutputStream tarBytes = new ByteArrayOutputStream();
    try (TarArchiveOutputStream tar = new TarArchiveOutputStream(tarBytes)) {
      tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
      TarArchiveEntry entry = new TarArchiveEntry(INDEX_ENTRY_NAME);
      entry.setSize(content.length);
      entry.setMode(0644);
      entry.setModTime(0L);
      tar.putArchiveEntry(entry);
      tar.write(content);
      tar.closeArchiveEntry();
      tar.finish();
    }

    ByteArrayOutputStream gzipBytes = new ByteArrayOutputStream();
    try (GZIPOutputStream gzip = new GZIPOutputStream(gzipBytes)) {
      gzip.write(tarBytes.toByteArray());
    }
    return gzipBytes.toByteArray();
  }

  /**
   * Extracts the stanzas of an {@code APKINDEX.tar.gz}. Unreadable documents yield an empty list.
   */
  public static List<String> parse(final byte[] archive) {
    List<String> stanzas = new ArrayList<>();
    if (archive == null || archive.length == 0) {
      return stanzas;
    }
    try (TarArchiveInputStream tar = new TarArchiveInputStream(
        new GZIPInputStream(new ByteArrayInputStream(archive))))
    {
      TarArchiveEntry entry;
      while ((entry = tar.getNextEntry()) != null) {
        if (entry.isDirectory() || !INDEX_ENTRY_NAME.equals(entry.getName())) {
          continue;
        }
        String index = new String(ByteStreams.toByteArray(tar), StandardCharsets.UTF_8);
        for (String stanza : index.split("\n\n")) {
          String trimmed = stanza.trim();
          if (!trimmed.isEmpty()) {
            stanzas.add(trimmed + "\n\n");
          }
        }
        break;
      }
    }
    catch (IOException e) {
      // a member may be offline or serving something that is not an APKINDEX
      return new ArrayList<>();
    }
    return stanzas;
  }

  /**
   * Identity of a stanza inside an index: package name plus version. The first member of a group
   * that provides a given package wins.
   */
  public static String packageKey(final String stanza) {
    String name = null;
    String version = null;
    for (String line : stanza.split("\n")) {
      if (line.startsWith("P:")) {
        name = line.substring(2);
      }
      else if (line.startsWith("V:")) {
        version = line.substring(2);
      }
    }
    return (name == null ? "" : name) + '\0' + (version == null ? "" : version);
  }
}
