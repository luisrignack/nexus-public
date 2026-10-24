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

import java.util.Map;

import org.sonatype.nexus.repository.docker.internal.DockerRegistryService.Range;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DockerRegistryServiceTest
{
  @Test
  void parsesBearerChallenge() {
    Map<String, String> parameters = DockerRegistryService.parseChallenge(
        "realm=\"https://auth.docker.io/token\",service=\"registry.docker.io\"," +
            "scope=\"repository:library/nginx:pull\"");

    assertThat(parameters)
        .containsEntry("realm", "https://auth.docker.io/token")
        .containsEntry("service", "registry.docker.io")
        .containsEntry("scope", "repository:library/nginx:pull");
  }

  @Test
  void parsesUnquotedChallengeParameters() {
    Map<String, String> parameters = DockerRegistryService.parseChallenge("realm=https://example.com/token,service=x");

    assertThat(parameters)
        .containsEntry("realm", "https://example.com/token")
        .containsEntry("service", "x");
  }

  @Test
  void parsesClosedRange() {
    Range range = DockerRegistryService.parseRange("bytes=0-99", 1000);

    assertThat(range.start).isZero();
    assertThat(range.end).isEqualTo(99);
    assertThat(range.length()).isEqualTo(100);
  }

  @Test
  void parsesOpenEndedRange() {
    Range range = DockerRegistryService.parseRange("bytes=900-", 1000);

    assertThat(range.start).isEqualTo(900);
    assertThat(range.end).isEqualTo(999);
  }

  @Test
  void parsesSuffixRange() {
    Range range = DockerRegistryService.parseRange("bytes=-100", 1000);

    assertThat(range.start).isEqualTo(900);
    assertThat(range.end).isEqualTo(999);
  }

  @Test
  void clampsRangeToContentLength() {
    Range range = DockerRegistryService.parseRange("bytes=990-5000", 1000);

    assertThat(range.start).isEqualTo(990);
    assertThat(range.end).isEqualTo(999);
  }

  @Test
  void rejectsUnsatisfiableRanges() {
    assertThat(DockerRegistryService.parseRange("bytes=1000-2000", 1000))
        .isSameAs(Range.UNSATISFIABLE);
    assertThat(DockerRegistryService.parseRange("bytes=10-5", 1000))
        .isSameAs(Range.UNSATISFIABLE);
    assertThat(DockerRegistryService.parseRange("bytes=abc-def", 1000))
        .isSameAs(Range.UNSATISFIABLE);
  }

  @Test
  void ignoresUnsupportedRangeHeaders() {
    assertThat(DockerRegistryService.parseRange(null, 1000)).isNull();
    assertThat(DockerRegistryService.parseRange("items=0-10", 1000)).isNull();
    assertThat(DockerRegistryService.parseRange("bytes=0-10,20-30", 1000)).isNull();
  }

  @Test
  void validatesRepositoryPaths() {
    assertThat(DockerRegistryService.validRepository("library/nginx")).isTrue();
    assertThat(DockerRegistryService.validRepository("owner/image")).isTrue();
    assertThat(DockerRegistryService.validRepository("")).isFalse();
    assertThat(DockerRegistryService.validRepository("library//nginx")).isFalse();
    assertThat(DockerRegistryService.validRepository("library/../secret")).isFalse();
    assertThat(DockerRegistryService.validRepository("library/./nginx")).isFalse();
    assertThat(DockerRegistryService.validRepository("library\\nginx")).isFalse();
  }

  @Test
  void recognizesDigests() {
    assertThat(DockerRegistryService.isDigest("sha256:" + "a".repeat(64))).isTrue();
    assertThat(DockerRegistryService.isDigest("sha512:" + "A".repeat(128))).isTrue();
    assertThat(DockerRegistryService.isDigest("latest")).isFalse();
    assertThat(DockerRegistryService.isDigest("sha256:short")).isFalse();
    assertThat(DockerRegistryService.isDigest(null)).isFalse();
  }
}
