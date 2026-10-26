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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.jupiter.api.Test;

import com.google.common.io.ByteStreams;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

class AlpineIndexBuilderTest
{
  private static final String MUSL = """
      C:Q1hOSEIsj9SZrWsMv32YAiOX4vQCo=
      P:musl
      V:1.2.5-r4
      A:x86_64
      S:408411
      I:667648
      T:the musl c library (libc) implementation
      U:https://musl.libc.org/
      L:MIT
      o:musl
      m:Natanael Copa <ncopa@alpinelinux.org>
      t:1792598648
      c:cb3382a0eb89834b7845df246077b941982d712d
      p:so:libc.musl-x86_64.so.1=1

      """;

  private static final String BUSYBOX = """
      C:Q1AAAAAAAAAAAAAAAAAAAAAAAAAAA=
      P:busybox
      V:1.36.1-r29
      A:x86_64
      S:1000000
      I:2000000
      T:Size optimized toolbox of many common UNIX utilities
      U:https://busybox.net/
      L:GPL-2.0-only

      """;

  @Test
  void packsStanzasIntoApkIndexTarGz() throws Exception {
    byte[] archive = AlpineIndexBuilder.build(List.of(MUSL, BUSYBOX));

    try (TarArchiveInputStream tar = new TarArchiveInputStream(
        new GZIPInputStream(new ByteArrayInputStream(archive))))
    {
      TarArchiveEntry entry = tar.getNextEntry();
      assertThat(entry.getName(), equalTo("APKINDEX"));
      String index = new String(ByteStreams.toByteArray(tar), StandardCharsets.UTF_8);
      assertThat(index, equalTo(MUSL + BUSYBOX));
    }
  }

  @Test
  void parsesBackTheStanzasOfAnIndex() throws Exception {
    byte[] archive = AlpineIndexBuilder.build(List.of(MUSL, BUSYBOX));

    List<String> stanzas = AlpineIndexBuilder.parse(archive);

    assertThat(stanzas.size(), is(2));
    assertThat(stanzas.get(0), equalTo(MUSL));
    assertThat(stanzas.get(1), equalTo(BUSYBOX));
  }

  @Test
  void identifiesPackagesByNameAndVersion() {
    assertThat(AlpineIndexBuilder.packageKey(MUSL), equalTo("musl\0" + "1.2.5-r4"));
    assertThat(AlpineIndexBuilder.packageKey(BUSYBOX), equalTo("busybox\0" + "1.36.1-r29"));
    assertThat(AlpineIndexBuilder.packageKey(MUSL).equals(AlpineIndexBuilder.packageKey(BUSYBOX)), is(false));
  }

  @Test
  void toleratesBrokenArchives() {
    assertThat(AlpineIndexBuilder.parse("not gzip".getBytes(StandardCharsets.UTF_8)).isEmpty(), is(true));
    assertThat(AlpineIndexBuilder.parse(new byte[0]).isEmpty(), is(true));
  }

  @Test
  void generatedIndexIsReadableAndKeepsStanzaOrder() throws Exception {
    List<String> stanzas = AlpineIndexBuilder.parse(AlpineIndexBuilder.build(List.of(BUSYBOX, MUSL)));
    assertThat(stanzas, contains(BUSYBOX, MUSL));
  }
}
