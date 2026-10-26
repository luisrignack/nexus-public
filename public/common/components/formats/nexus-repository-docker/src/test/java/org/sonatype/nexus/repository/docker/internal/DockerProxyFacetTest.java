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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DockerProxyFacetTest
{
  @Test
  void normalizesRegistryUrls() {
    assertThat(DockerProxyFacetImpl.normalize("https://registry.example.com/"))
        .isEqualTo("https://registry.example.com");
    assertThat(DockerProxyFacetImpl.normalize("https://registry.example.com/v2/"))
        .isEqualTo("https://registry.example.com");
    assertThat(DockerProxyFacetImpl.normalize("https://registry.example.com/v2"))
        .isEqualTo("https://registry.example.com");
    assertThat(DockerProxyFacetImpl.normalize("https://registry.example.com/base/"))
        .isEqualTo("https://registry.example.com/base");
  }

  @Test
  void recognizesDockerHub() {
    assertThat(DockerProxyFacetImpl.isDockerHub("https://index.docker.io/")).isTrue();
    assertThat(DockerProxyFacetImpl.isDockerHub("https://registry-1.docker.io")).isTrue();
    assertThat(DockerProxyFacetImpl.isDockerHub("https://docker.m.daocloud.io")).isTrue();
    assertThat(DockerProxyFacetImpl.isDockerHub("docker.io")).isTrue();
    assertThat(DockerProxyFacetImpl.isDockerHub("https://ghcr.io")).isFalse();
    assertThat(DockerProxyFacetImpl.isDockerHub("https://quay.io")).isFalse();
    assertThat(DockerProxyFacetImpl.isDockerHub("https://ghcr.m.daocloud.io")).isFalse();
    assertThat(DockerProxyFacetImpl.isDockerHub("https://quay.m.daocloud.io")).isFalse();
    assertThat(DockerProxyFacetImpl.isDockerHub("https://gcr.m.daocloud.io")).isFalse();
    assertThat(DockerProxyFacetImpl.isDockerHub(null)).isFalse();
  }
}
