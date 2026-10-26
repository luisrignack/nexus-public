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

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;

import org.sonatype.nexus.repository.docker.internal.DockerRegistryConfig.Resolved;
import org.sonatype.nexus.repository.docker.internal.DockerRegistryConfig.Upstream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class DockerRegistryConfigTest
{
  private static final String UPSTREAMS =
      "dockerhub=https://docker.m.daocloud.io,gcr=https://gcr.m.daocloud.io,ghcr=https://ghcr.m.daocloud.io";

  @TempDir
  Path cacheDir;

  @Test
  void parsesUpstreamsAndStripsTrailingSlashes() {
    DockerRegistryConfig config = config(UPSTREAMS, "dockerhub");

    assertThat(config.getUpstreams()).containsOnlyKeys("dockerhub", "gcr", "ghcr");
    assertThat(config.getUpstreams().get("dockerhub").baseUrl()).isEqualTo("https://docker.m.daocloud.io");
  }

  @Test
  void ignoresMalformedUpstreams() {
    DockerRegistryConfig config = config(
        "dockerhub=https://docker.m.daocloud.io,broken,also-broken=ftp://example.com,=https://empty",
        "dockerhub");

    assertThat(config.getUpstreams()).containsOnlyKeys("dockerhub");
  }

  @Test
  void extractsCredentialsFromUpstreamUrl() {
    DockerRegistryConfig config = config("private=https://user:secret@registry.example.com/base/", "private");

    Upstream upstream = config.getUpstreams().get("private");
    assertThat(upstream.baseUrl()).isEqualTo("https://registry.example.com/base");
    assertThat(upstream.authorization()).isEqualTo(
        "Basic " + Base64.getEncoder().encodeToString("user:secret".getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void resolvesNamedUpstreamFromFirstPathSegment() {
    DockerRegistryConfig config = config(UPSTREAMS, "dockerhub");

    Resolved resolved = config.resolve("gcr/distroless/static/manifests/latest");

    assertThat(resolved.upstream().name()).isEqualTo("gcr");
    assertThat(resolved.path()).isEqualTo("distroless/static/manifests/latest");
  }

  @Test
  void resolvesNamedUpstreamCaseInsensitively() {
    DockerRegistryConfig config = config(UPSTREAMS, "dockerhub");

    assertThat(config.resolve("GHCR/owner/image/manifests/1").upstream().name()).isEqualTo("ghcr");
  }

  @Test
  void fallsBackToDefaultUpstreamForUnknownPrefix() {
    DockerRegistryConfig config = config(UPSTREAMS, "dockerhub");

    Resolved resolved = config.resolve("owner/image/manifests/latest");

    assertThat(resolved.upstream().name()).isEqualTo("dockerhub");
    assertThat(resolved.path()).isEqualTo("owner/image/manifests/latest");
  }

  @Test
  void unconfiguredRegistriesAreRejectedInsteadOfUsingTheDefaultUpstream() {
    DockerRegistryConfig config = config(
        "ghcr=https://ghcr.m.daocloud.io,quay=https://quay.m.daocloud.io",
        "ghcr");

    assertThat(config.resolve("library/nginx/manifests/latest").upstream()).isNull();
    assertThat(config.resolve("dockerhub/library/nginx/manifests/latest").upstream()).isNull();
    assertThat(config.resolve("docker.io/library/nginx/manifests/latest").upstream()).isNull();
    // a known registry that is not configured is rejected instead of being sent to the default upstream
    assertThat(config.resolve("gcr/google-containers/busybox/manifests/1.27").upstream()).isNull();
    // regular ghcr images still resolve to the default upstream
    assertThat(config.resolve("astral-sh/uv/manifests/0.5.0").upstream().name()).isEqualTo("ghcr");
  }

  @Test
  void treatsUpstreamNameAloneAsDefaultUpstreamPath() {
    DockerRegistryConfig config = config(UPSTREAMS, "dockerhub");

    Resolved resolved = config.resolve("gcr");

    assertThat(resolved.upstream().name()).isEqualTo("dockerhub");
    assertThat(resolved.path()).isEqualTo("gcr");
  }

  @Test
  void returnsNoUpstreamWhenDefaultIsNotConfigured() {
    DockerRegistryConfig config = config(UPSTREAMS, "missing");

    assertThat(config.resolve("library/nginx").upstream()).isNull();
  }

  @Test
  void addsLibraryPrefixForOfficialDockerHubImages() {
    DockerRegistryConfig config = config(UPSTREAMS, "dockerhub");
    Upstream dockerhub = config.getUpstreams().get("dockerhub");
    Upstream ghcr = config.getUpstreams().get("ghcr");

    assertThat(config.upstreamRepository(dockerhub, "nginx")).isEqualTo("library/nginx");
    assertThat(config.upstreamRepository(dockerhub, "library/nginx")).isEqualTo("library/nginx");
    assertThat(config.upstreamRepository(dockerhub, "bitnami/nginx")).isEqualTo("bitnami/nginx");
    assertThat(config.upstreamRepository(ghcr, "nginx")).isEqualTo("nginx");
  }

  @Test
  void canDisableLibraryFallback() {
    DockerRegistryConfig config = new DockerRegistryConfig(
        true, UPSTREAMS, "dockerhub", cacheDir.toString(), 300, 300, 60, false);

    assertThat(config.upstreamRepository(config.getUpstreams().get("dockerhub"), "nginx")).isEqualTo("nginx");
  }

  @Test
  void keepsDisabledState() {
    DockerRegistryConfig config = new DockerRegistryConfig(
        false, UPSTREAMS, "dockerhub", cacheDir.toString(), 300, 300, 60, true);

    assertThat(config.isEnabled()).isFalse();
  }

  private DockerRegistryConfig config(final String upstreams, final String defaultUpstream) {
    return new DockerRegistryConfig(true, upstreams, defaultUpstream, cacheDir.toString(), 300, 300, 60, true);
  }
}
