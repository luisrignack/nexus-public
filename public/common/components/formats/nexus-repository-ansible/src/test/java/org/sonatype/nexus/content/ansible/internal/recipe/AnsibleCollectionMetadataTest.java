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
package org.sonatype.nexus.content.ansible.internal.recipe;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.sonatype.goodies.testsupport.Test5Support;
import org.sonatype.nexus.repository.ansible.AnsibleCollectionMetadata;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnsibleCollectionMetadataTest
    extends Test5Support
{
  private static final String MANIFEST =
      "{\"namespace\":\"community\",\"name\":\"general\",\"version\":\"1.0.0\","
          + "\"description\":\"A collection\",\"authors\":[\"Ansible\",\"Community\"],"
          + "\"dependencies\":{\"ansible.posix\":\"*\"}}";

  @Test
  void parsesManifestFromJson() throws IOException {
    AnsibleCollectionMetadata metadata = AnsibleCollectionMetadata.fromJson(MANIFEST.getBytes(StandardCharsets.UTF_8));

    assertThat(metadata.getNamespace(), is("community"));
    assertThat(metadata.getName(), is("general"));
    assertThat(metadata.getVersion(), is("1.0.0"));
    assertThat(metadata.getDescription(), is("A collection"));
    assertThat(metadata.getQualifiedName(), is("community.general"));
    assertThat(metadata.getAuthors(), contains("Ansible", "Community"));
    assertThat(metadata.getDependencies(), is(Map.of("ansible.posix", "*")));
  }

  @Test
  void readsManifestFromCollectionTarball() throws IOException {
    byte[] tarball = tarball(MANIFEST);
    AnsibleCollectionMetadata metadata;
    try (ByteArrayInputStream input = new ByteArrayInputStream(tarball)) {
      metadata = AnsibleCollectionMetadata.read(input);
    }

    assertThat(metadata.getNamespace(), is("community"));
    assertThat(metadata.getName(), is("general"));
    assertThat(metadata.getVersion(), is("1.0.0"));
  }

  @Test
  void rejectsArchiveWithoutManifest() throws IOException {
    byte[] tarball = tarball(null);
    IOException error = assertThrows(IOException.class, () -> {
      try (ByteArrayInputStream input = new ByteArrayInputStream(tarball)) {
        AnsibleCollectionMetadata.read(input);
      }
    });
    assertThat(error.getMessage().contains("MANIFEST.json"), is(true));
  }

  @Test
  void rejectsManifestWithoutCoordinates() {
    assertThrows(IOException.class, () ->
        AnsibleCollectionMetadata.fromJson("{\"version\":\"1.0.0\"}".getBytes(StandardCharsets.UTF_8)));
    assertThrows(IOException.class, () ->
        AnsibleCollectionMetadata.fromJson("{\"namespace\":\"a\",\"name\":\"b\"}".getBytes(StandardCharsets.UTF_8)));
    assertThrows(IOException.class, () ->
        AnsibleCollectionMetadata.fromJson("not json".getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void toleratesManifestWithoutAuthorsOrDependencies() throws IOException {
    AnsibleCollectionMetadata metadata = AnsibleCollectionMetadata.fromJson(
        "{\"namespace\":\"ns\",\"name\":\"nm\",\"version\":\"2.0.0\"}".getBytes(StandardCharsets.UTF_8));

    assertThat(metadata.getAuthors(), is(List.of()));
    assertThat(metadata.getDependencies().isEmpty(), is(true));
  }

  /**
   * Builds a gzipped tarball laid out like a real collection, optionally carrying a manifest.
   */
  private static byte[] tarball(final String manifest) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (TarArchiveOutputStream tar = new TarArchiveOutputStream(
        new java.util.zip.GZIPOutputStream(bytes)))
    {
      String base = "files/ansible_collections/community/general/";
      add(tar, base + "plugins/modules/foo.py", "print('hi')");
      if (manifest != null) {
        add(tar, base + "MANIFEST.json", manifest);
      }
      tar.finish();
    }
    return bytes.toByteArray();
  }

  private static void add(final TarArchiveOutputStream tar, final String name, final String content)
      throws IOException
  {
    byte[] data = content.getBytes(StandardCharsets.UTF_8);
    TarArchiveEntry entry = new TarArchiveEntry(name);
    entry.setSize(data.length);
    tar.putArchiveEntry(entry);
    tar.write(data);
    tar.closeArchiveEntry();
  }
}
