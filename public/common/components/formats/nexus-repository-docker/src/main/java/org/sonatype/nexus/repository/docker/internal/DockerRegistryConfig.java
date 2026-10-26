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

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import jakarta.inject.Inject;

import jakarta.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static java.util.Base64.getEncoder;

/**
 * Configuration of the Docker registry proxy.
 *
 * <p>Upstream registries are declared as a comma separated list of {@code name=url} pairs. The name is used as the
 * first path segment of the registry API, so with the default configuration the following pulls are possible:</p>
 *
 * <pre>
 * docker pull localhost:8081/ghcr/owner/image
 * docker pull localhost:8081/quay/prometheus/node-exporter
 * </pre>
 *
 * <p>Docker Hub is deliberately not part of the defaults: it is not reachable in the target environments and
 * every request would fail after a long timeout. A {@code dockerhub} upstream may still be configured explicitly,
 * but no request is sent to Docker Hub unless it is. Only mirrors whose manifests and blobs were verified to work
 * are enabled by default ({@code ghcr} and {@code quay}); {@code gcr} is not included because its mirror serves
 * manifests but rejects blob downloads.</p>
 */
@Component
@Singleton
public class DockerRegistryConfig
{
  private static final Logger log = LoggerFactory.getLogger(DockerRegistryConfig.class);

  public static final String DEFAULT_UPSTREAMS =
      "ghcr=https://ghcr.m.daocloud.io," +
      "quay=https://quay.m.daocloud.io";

  private final boolean enabled;

  private final Map<String, Upstream> upstreams;

  private final String defaultUpstream;

  private final Path cacheDir;

  private final long manifestTtlSeconds;

  private final long tagsTtlSeconds;

  private final int timeoutSeconds;

  private final boolean hubLibraryFallback;

  @Inject
  public DockerRegistryConfig(
      @Value("${nexus.docker.enabled:true}") final boolean enabled,
      @Value("${nexus.docker.upstreams:" + DEFAULT_UPSTREAMS + "}") final String upstreams,
      @Value("${nexus.docker.defaultUpstream:ghcr}") final String defaultUpstream,
      @Value("${nexus.docker.cacheDir:}") final String cacheDir,
      @Value("${nexus.docker.manifestTtlSeconds:300}") final long manifestTtlSeconds,
      @Value("${nexus.docker.tagsTtlSeconds:300}") final long tagsTtlSeconds,
      @Value("${nexus.docker.timeoutSeconds:60}") final int timeoutSeconds,
      @Value("${nexus.docker.hubLibraryFallback:true}") final boolean hubLibraryFallback)
  {
    this.enabled = enabled;
    this.upstreams = Collections.unmodifiableMap(parseUpstreams(upstreams));
    this.defaultUpstream = defaultUpstream == null
        ? null
        : defaultUpstream.trim().toLowerCase(Locale.ROOT);
    this.cacheDir = resolveCacheDir(cacheDir);
    this.manifestTtlSeconds = Math.max(manifestTtlSeconds, 0);
    this.tagsTtlSeconds = Math.max(tagsTtlSeconds, 0);
    this.timeoutSeconds = Math.max(timeoutSeconds, 1);
    this.hubLibraryFallback = hubLibraryFallback;

    if (this.upstreams.isEmpty()) {
      log.warn("No Docker upstream registries configured; the Docker proxy will answer 404");
    }
    else if (this.upstreams.get(this.defaultUpstream) == null) {
      log.warn("Default Docker upstream '{}' is not configured; known upstreams: {}",
          this.defaultUpstream, this.upstreams.keySet());
    }
  }

  public boolean isEnabled() {
    return enabled;
  }

  public Path getCacheDir() {
    return cacheDir;
  }

  public long getManifestTtlSeconds() {
    return manifestTtlSeconds;
  }

  public long getTagsTtlSeconds() {
    return tagsTtlSeconds;
  }

  public int getTimeoutSeconds() {
    return timeoutSeconds;
  }

  public boolean isHubLibraryFallback() {
    return hubLibraryFallback;
  }

  public Map<String, Upstream> getUpstreams() {
    return upstreams;
  }

  /**
   * Splits a request path into the upstream registry and the registry relative path. The first path segment selects
   * the upstream when it matches a configured name; otherwise the default upstream is used and the whole path is
   * passed through, unless the segment names a known registry that is not configured: those requests are rejected
   * rather than forwarded to the wrong registry.
   */
  public Resolved resolve(final String route) {
    Upstream fallback = upstreams.get(defaultUpstream);
    int slash = route.indexOf('/');
    String first = slash < 0 ? route : route.substring(0, slash);
    String rest = slash < 0 ? "" : route.substring(slash + 1);

    String normalized = first.toLowerCase(Locale.ROOT);
    Upstream named = upstreams.get(normalized);
    if (named != null && !rest.isEmpty()) {
      return new Resolved(named, rest);
    }
    if (named == null && isKnownRegistry(normalized)) {
      // a known registry that is not configured must not be silently sent to whichever upstream is the default
      return new Resolved(null, "");
    }
    return new Resolved(fallback, route);
  }

  /**
   * First path segments that name a well-known registry. When such a name has no configured upstream the request is
   * rejected instead of being forwarded to the default upstream (which would query the wrong registry).
   */
  private static boolean isKnownRegistry(final String firstSegment) {
    return "dockerhub".equals(firstSegment)
        || "docker".equals(firstSegment)
        || "docker.io".equals(firstSegment)
        || "index.docker.io".equals(firstSegment)
        || "registry-1.docker.io".equals(firstSegment)
        || "library".equals(firstSegment)
        || "ghcr".equals(firstSegment)
        || "quay".equals(firstSegment)
        || "gcr".equals(firstSegment);
  }

  /**
   * Docker Hub official images are addressed as {@code library/name} upstream but users usually type just the image
   * name. Returns the name to request from the upstream registry.
   */
  public String upstreamRepository(final Upstream upstream, final String repository) {
    if (hubLibraryFallback && upstream != null && upstream.libraryFallback()
        && repository.indexOf('/') < 0) {
      return "library/" + repository;
    }
    return repository;
  }

  private static Map<String, Upstream> parseUpstreams(final String value) {
    Map<String, Upstream> result = new LinkedHashMap<>();
    if (value == null || value.trim().isEmpty()) {
      return result;
    }

    for (String entry : value.split(",")) {
      String candidate = entry.trim();
      if (candidate.isEmpty()) {
        continue;
      }
      int equals = candidate.indexOf('=');
      if (equals <= 0 || equals == candidate.length() - 1) {
        log.warn("Ignoring Docker upstream '{}': expected name=url", candidate);
        continue;
      }

      String name = candidate.substring(0, equals).trim().toLowerCase(Locale.ROOT);
      String url = candidate.substring(equals + 1).trim();
      if (!url.startsWith("http://") && !url.startsWith("https://")) {
        log.warn("Ignoring Docker upstream '{}': unsupported URL {}", name, url);
        continue;
      }
      while (url.endsWith("/")) {
        url = url.substring(0, url.length() - 1);
      }

      String authorization = null;
      try {
        URI uri = URI.create(url);
        if (uri.getUserInfo() != null && !uri.getUserInfo().isEmpty()) {
          authorization = "Basic " + getEncoder().encodeToString(
              uri.getUserInfo().getBytes(StandardCharsets.UTF_8));
          url = new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), null, null).toString();
        }
      }
      catch (Exception e) {
        log.warn("Ignoring Docker upstream '{}': invalid URL {}", name, url, e);
        continue;
      }

      result.put(name, new Upstream(name, url, authorization, "dockerhub".equals(name)));
    }
    return result;
  }

  private static Path resolveCacheDir(final String configured) {
    if (configured != null && !configured.trim().isEmpty()) {
      return Paths.get(configured.trim());
    }
    String base = System.getProperty("karaf.data");
    if (base == null || base.isEmpty()) {
      base = System.getProperty("java.io.tmpdir", ".");
    }
    return Paths.get(base, "docker-cache");
  }

  /**
   * A remote registry.
   */
  public static final class Upstream
  {
    private final String name;

    private final String baseUrl;

    private final String authorization;

    private final boolean libraryFallback;

    /**
     * Creates an upstream using the default Docker Hub library fallback heuristic.
     */
    public Upstream(final String name, final String baseUrl, final String authorization) {
      this(name, baseUrl, authorization, "dockerhub".equals(name));
    }

    /**
     * Creates an upstream, optionally enabling the {@code library/} fallback used by Docker Hub official images.
     */
    public Upstream(
        final String name,
        final String baseUrl,
        final String authorization,
        final boolean libraryFallback)
    {
      this.name = name;
      this.baseUrl = baseUrl;
      this.authorization = authorization;
      this.libraryFallback = libraryFallback;
    }

    public String name() {
      return name;
    }

    public String baseUrl() {
      return baseUrl;
    }

    public String authorization() {
      return authorization;
    }

    public boolean libraryFallback() {
      return libraryFallback;
    }

    @Override
    public String toString() {
      return name + "=" + baseUrl;
    }
  }

  /**
   * Result of resolving a request against the configured upstreams.
   */
  public static final class Resolved
  {
    private final Upstream upstream;

    private final String path;

    Resolved(final Upstream upstream, final String path) {
      this.upstream = upstream;
      this.path = path;
    }

    public Upstream upstream() {
      return upstream;
    }

    public String path() {
      return path;
    }
  }
}
