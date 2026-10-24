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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.sonatype.nexus.repository.docker.internal.DockerCache.Entry;
import org.sonatype.nexus.repository.docker.internal.DockerRegistryConfig.Resolved;
import org.sonatype.nexus.repository.docker.internal.DockerRegistryConfig.Upstream;
import org.sonatype.nexus.security.anonymous.AnonymousHelper;
import org.sonatype.nexus.security.anonymous.AnonymousManager;
import org.sonatype.nexus.security.authc.NexusBasicHttpAuthenticationFilter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Docker Registry v2 pull-through proxy.
 *
 * <p>The servlet accepts the standard registry API ({@code /v2/...}) and forwards read operations to one of the
 * configured mirrors. Blobs and manifests are cached on disk, Bearer tokens are renewed transparently and multi
 * architecture manifest lists are returned untouched (the {@code Accept} header is forwarded).</p>
 *
 * <p>Push is intentionally not implemented: Daocloud mirrors are read-only and this component is a pull-through
 * cache, not a hosted registry.</p>
 */
@Component
@Singleton
public class DockerRegistryService
{
  private static final Logger log = LoggerFactory.getLogger(DockerRegistryService.class);

  private static final String API_VERSION_HEADER = "Docker-Distribution-Api-Version";

  private static final String API_VERSION = "registry/2.0";

  private static final String MANIFESTS = "/manifests/";

  private static final String BLOBS = "/blobs/";

  private static final String TAGS_SUFFIX = "/tags/list";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final DockerRegistryConfig config;

  private final DockerCache cache;

  private final Provider<AnonymousManager> anonymousManager;

  private final HttpClient httpClient;

  private final Map<String, CachedToken> tokens = new ConcurrentHashMap<>();

  @Inject
  public DockerRegistryService(
      final DockerRegistryConfig config,
      final DockerCache cache,
      final Provider<AnonymousManager> anonymousManager)
  {
    this.config = config;
    this.cache = cache;
    this.anonymousManager = anonymousManager;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(Math.min(config.getTimeoutSeconds(), 30)))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
    try {
      cache.initialize();
    }
    catch (IOException e) {
      log.warn("Could not initialize the Docker cache directory {}", config.getCacheDir(), e);
    }
  }

  public void handle(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
    response.setHeader(API_VERSION_HEADER, API_VERSION);

    if (!config.isEnabled()) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    if (!authorized(response)) {
      return;
    }

    String path = request.getRequestURI();
    String contextPath = request.getContextPath();
    if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
      path = path.substring(contextPath.length());
    }
    if (!path.startsWith("/v2")) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    String tail = path.substring("/v2".length());
    if (tail.isEmpty() || "/".equals(tail)) {
      response.setStatus(HttpServletResponse.SC_OK);
      response.setContentType("application/json");
      response.getWriter().write("{}");
      return;
    }

    String method = request.getMethod().toUpperCase(Locale.ROOT);
    if (!"GET".equals(method) && !"HEAD".equals(method)) {
      response.setHeader("Allow", "GET, HEAD");
      response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
      return;
    }

    String route = tail.startsWith("/") ? tail.substring(1) : tail;
    Resolved resolved = config.resolve(route);
    if (resolved.upstream() == null || resolved.path().isEmpty()) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    try {
      String remaining = resolved.path();
      if (remaining.endsWith(TAGS_SUFFIX) && remaining.length() > TAGS_SUFFIX.length()) {
        String repository = remaining.substring(0, remaining.length() - TAGS_SUFFIX.length());
        if (validRepository(repository)) {
          handleTags(request, response, method, resolved.upstream(), repository);
          return;
        }
      }
      else {
        int index = remaining.lastIndexOf(MANIFESTS);
        if (index > 0) {
          String repository = remaining.substring(0, index);
          String reference = remaining.substring(index + MANIFESTS.length());
          if (validRepository(repository) && validReference(reference)) {
            handleManifest(request, response, method, resolved.upstream(), repository, reference);
            return;
          }
        }
        index = remaining.lastIndexOf(BLOBS);
        if (index > 0) {
          String repository = remaining.substring(0, index);
          String digest = remaining.substring(index + BLOBS.length());
          if (validRepository(repository) && isDigest(digest)) {
            handleBlob(request, response, method, resolved.upstream(), repository, digest);
            return;
          }
        }
      }
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      if (!response.isCommitted()) {
        response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "Docker registry request interrupted");
      }
    }
    catch (IOException e) {
      log.warn("Docker proxy failed for {} {}", method, path, e);
      if (!response.isCommitted()) {
        response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "Docker registry request failed");
      }
    }
  }

  /**
   * Accepts pull requests from authenticated users, or from anybody when anonymous access is enabled. When the
   * request is rejected a Basic challenge is sent so {@code docker login} can prompt for credentials.
   */
  private boolean authorized(final HttpServletResponse response) throws IOException {
    Subject subject = SecurityUtils.getSubject();
    if (subject != null && subject.isAuthenticated() && !AnonymousHelper.isAnonymous(subject)) {
      return true;
    }

    AnonymousManager manager = anonymousManager.get();
    if (manager != null && manager.isEnabled()) {
      return true;
    }

    response.setHeader("WWW-Authenticate",
        "BASIC realm=\"" + NexusBasicHttpAuthenticationFilter.BASIC_AUTH_REALM + "\"");
    response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
    return false;
  }

  private void handleManifest(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final String method,
      final Upstream upstream,
      final String repository,
      final String reference) throws IOException, InterruptedException
  {
    boolean byDigest = isDigest(reference);
    Entry cached = byDigest
        ? cache.manifestByDigest(upstream, reference)
        : cache.manifestByTag(upstream, repository, reference);

    if (cached != null && (byDigest || cache.isFresh(cached, config.getManifestTtlSeconds()))) {
      serveCached(request, response, method, cached);
      return;
    }

    if ("HEAD".equals(method)) {
      if (cached != null) {
        serveCached(request, response, method, cached);
        return;
      }
      proxyHead(response, upstream, repository, "manifests/" + reference, request);
      return;
    }

    String upstreamRepository = config.upstreamRepository(upstream, repository);
    try (Download download = download(upstream, upstreamRepository, "manifests/" + reference, request, method)) {
      if (download.status == HttpServletResponse.SC_OK && download.temp != null) {
        Entry stored = cache.storeManifest(
            upstream, repository, reference, byDigest,
            download.contentType(), download.digest(), download.etag(), download.temp);
        download.temp = null;
        serveCached(request, response, method, stored);
        return;
      }

      if (cached != null && download.status >= 500) {
        log.warn("Upstream {} returned {} for manifest {}:{}; serving stale cache",
            upstream.name(), download.status, repository, reference);
        serveCached(request, response, method, cached);
        return;
      }
      copyResponse(response, download);
    }
  }

  private void handleBlob(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final String method,
      final Upstream upstream,
      final String repository,
      final String digest) throws IOException, InterruptedException
  {
    Entry cached = cache.blob(upstream, digest);
    if (cached != null) {
      serveCached(request, response, method, cached);
      return;
    }

    String upstreamRepository = config.upstreamRepository(upstream, repository);
    if ("HEAD".equals(method)) {
      proxyHead(response, upstream, upstreamRepository, "blobs/" + digest, request);
      return;
    }

    String range = request.getHeader("Range");
    if (range != null && !range.isEmpty()) {
      // Range requests are usually resumes; do not cache the partial response.
      try (Download download = download(upstream, upstreamRepository, "blobs/" + digest, request, method)) {
        copyResponse(response, download);
      }
      return;
    }

    streamBlob(response, upstream, upstreamRepository, repository, digest, request);
  }

  /**
   * Streams a blob to the client while writing it to the cache at the same time. Sending the headers and first bytes
   * immediately is important: Docker gives up when a large layer produces no response headers within its timeout.
   */
  private void streamBlob(
      final HttpServletResponse response,
      final Upstream upstream,
      final String upstreamRepository,
      final String repository,
      final String digest,
      final HttpServletRequest request) throws IOException, InterruptedException
  {
    URI uri = URI.create(upstream.baseUrl() + "/v2/" + upstreamRepository + "/blobs/" + digest);
    HttpResponse<InputStream> upstreamResponse = send(upstream, upstreamRepository, uri, request, "GET");
    int status = upstreamResponse.statusCode();

    if (status != HttpServletResponse.SC_OK) {
      Download download = new Download(status, upstreamResponse.headers());
      download.body = upstreamResponse.body();
      try (Download ignored = download) {
        copyResponse(response, download);
      }
      return;
    }

    HttpHeaders headers = upstreamResponse.headers();
    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType(headers.firstValue("Content-Type").orElse("application/octet-stream"));
    headers.firstValueAsLong("Content-Length").ifPresent(response::setContentLengthLong);
    headers.firstValue("Docker-Content-Digest").ifPresent(value -> response.setHeader("Docker-Content-Digest", value));
    headers.firstValue("ETag").ifPresent(value -> response.setHeader("ETag", value));
    headers.firstValue("Accept-Ranges").ifPresent(value -> response.setHeader("Accept-Ranges", value));

    Path temp = Files.createTempFile(config.getCacheDir(), "docker-", ".tmp");
    boolean complete = false;
    try (InputStream input = upstreamResponse.body(); OutputStream file = Files.newOutputStream(temp)) {
      OutputStream client = response.getOutputStream();
      boolean clientAlive = true;
      byte[] buffer = new byte[65536];
      int read;
      while ((read = input.read(buffer)) != -1) {
        file.write(buffer, 0, read);
        if (clientAlive) {
          try {
            client.write(buffer, 0, read);
            client.flush();
          }
          catch (IOException e) {
            // Keep downloading so the cache entry is complete; Docker will retry the blob if needed.
            clientAlive = false;
            log.debug("Client disconnected while streaming blob {}", digest, e);
          }
        }
      }
      complete = true;
    }
    catch (IOException | RuntimeException e) {
      Files.deleteIfExists(temp);
      if (!response.isCommitted()) {
        response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "Could not stream Docker blob");
      }
      throw e;
    }

    if (complete) {
      try {
        cache.storeBlob(upstream, digest, headers.firstValue("Content-Type").orElse(null),
            headers.firstValue("ETag").orElse(null), temp);
      }
      catch (IOException e) {
        log.warn("Could not store Docker blob {} in the cache", digest, e);
        Files.deleteIfExists(temp);
      }
    }
  }

  private void handleTags(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final String method,
      final Upstream upstream,
      final String repository) throws IOException, InterruptedException
  {
    String query = request.getQueryString();
    Entry cached = query == null ? cache.tags(upstream, repository) : null;
    if (cached != null && cache.isFresh(cached, config.getTagsTtlSeconds())) {
      serveCached(request, response, method, cached);
      return;
    }

    if ("HEAD".equals(method)) {
      if (cached != null) {
        serveCached(request, response, method, cached);
        return;
      }
      proxyHead(response, upstream, repository, "tags/list", request);
      return;
    }

    String upstreamRepository = config.upstreamRepository(upstream, repository);
    String endpoint = "tags/list" + (query != null ? "?" + query : "");
    try (Download download = download(upstream, upstreamRepository, endpoint, request, method)) {
      if (download.status == HttpServletResponse.SC_OK && download.temp != null) {
        if (query == null) {
          Entry stored = cache.storeTags(upstream, repository, download.contentType(), download.temp);
          download.temp = null;
          serveCached(request, response, method, stored);
        }
        else {
          // Paginated tag lists are request specific and must not replace the full cached list.
          response.setStatus(HttpServletResponse.SC_OK);
          response.setContentType(download.contentType() != null ? download.contentType() : "application/json");
          response.setContentLengthLong(Files.size(download.temp));
          if (!"HEAD".equals(method)) {
            Files.copy(download.temp, response.getOutputStream());
            response.getOutputStream().flush();
          }
        }
        return;
      }
      if (cached != null && download.status >= 500) {
        serveCached(request, response, method, cached);
        return;
      }
      copyResponse(response, download);
    }
  }

  private void serveCached(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final String method,
      final Entry entry) throws IOException
  {
    Path file = entry.file();
    if (!Files.isRegularFile(file)) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    String etag = entry.etag();
    String ifNoneMatch = request.getHeader("If-None-Match");
    if (etag != null && ifNoneMatch != null && ifNoneMatch.contains(etag)) {
      response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
      response.setHeader("ETag", etag);
      return;
    }

    long size = Files.size(file);
    response.setContentType(contentType(entry));
    response.setHeader("Accept-Ranges", "bytes");
    if (entry.digest() != null) {
      response.setHeader("Docker-Content-Digest", entry.digest());
    }
    if (etag != null) {
      response.setHeader("ETag", etag);
    }

    if ("HEAD".equals(method)) {
      response.setContentLengthLong(size);
      response.setStatus(HttpServletResponse.SC_OK);
      return;
    }

    Range range = parseRange(request.getHeader("Range"), size);
    if (range == Range.UNSATISFIABLE) {
      response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
      response.setHeader("Content-Range", "bytes */" + size);
      return;
    }

    if (range != null) {
      response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
      response.setHeader("Content-Range", "bytes " + range.start + '-' + range.end + '/' + size);
      response.setContentLengthLong(range.length());
      try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file.toFile(), "r")) {
        raf.seek(range.start);
        OutputStream output = response.getOutputStream();
        byte[] buffer = new byte[65536];
        long remaining = range.length();
        while (remaining > 0) {
          int read = raf.read(buffer, 0, (int) Math.min(buffer.length, remaining));
          if (read < 0) {
            break;
          }
          output.write(buffer, 0, read);
          remaining -= read;
        }
        output.flush();
      }
      return;
    }

    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentLengthLong(size);
    try (InputStream input = Files.newInputStream(file)) {
      input.transferTo(response.getOutputStream());
      response.getOutputStream().flush();
    }
    catch (IOException e) {
      // Client went away; the cache entry remains valid.
      log.debug("Could not stream cached Docker content {}", file, e);
    }
  }

  private void proxyHead(
      final HttpServletResponse response,
      final Upstream upstream,
      final String repository,
      final String endpoint,
      final HttpServletRequest request) throws IOException, InterruptedException
  {
    try (Download download = download(upstream, repository, endpoint, request, "HEAD")) {
      copyResponseHeaders(download.headers, response);
      response.setStatus(download.status);
    }
  }

  private Download download(
      final Upstream upstream,
      final String repository,
      final String endpoint,
      final HttpServletRequest request,
      final String method) throws IOException, InterruptedException
  {
    URI uri = URI.create(upstream.baseUrl() + "/v2/" + repository + "/" + endpoint);
    HttpResponse<InputStream> response = send(upstream, repository, uri, request, method);
    Download download = new Download(response.statusCode(), response.headers());

    if ("GET".equals(method) && response.statusCode() == HttpServletResponse.SC_OK) {
      Path temp = Files.createTempFile(config.getCacheDir(), "docker-", ".tmp");
      try (InputStream input = response.body(); OutputStream output = Files.newOutputStream(temp)) {
        input.transferTo(output);
      }
      catch (IOException | RuntimeException e) {
        Files.deleteIfExists(temp);
        throw e;
      }
      download.temp = temp;
    }
    else {
      download.body = response.body();
    }
    return download;
  }

  private HttpResponse<InputStream> send(
      final Upstream upstream,
      final String repository,
      final URI uri,
      final HttpServletRequest request,
      final String method) throws IOException, InterruptedException
  {
    HttpResponse<InputStream> response = null;
    for (int attempt = 0; attempt < 2; attempt++) {
      HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
          .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
          .method(method, HttpRequest.BodyPublishers.noBody());

      copyRequestHeader(request, builder, "Accept");
      copyRequestHeader(request, builder, "Range");
      copyRequestHeader(request, builder, "If-None-Match");
      copyRequestHeader(request, builder, "If-Modified-Since");
      builder.header("User-Agent", "nexus-docker-proxy/1.0");

      String token = validToken(upstream, repository);
      if (token != null) {
        builder.header("Authorization", "Bearer " + token);
      }
      else if (upstream.authorization() != null) {
        builder.header("Authorization", upstream.authorization());
      }

      if (response != null) {
        response.body().close();
      }
      response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());

      if (response.statusCode() != HttpServletResponse.SC_UNAUTHORIZED || attempt > 0
          || upstream.authorization() != null) {
        return response;
      }

      String challenge = response.headers().firstValue("WWW-Authenticate").orElse(null);
      if (!fetchToken(upstream, repository, challenge)) {
        return response;
      }
    }
    return response;
  }

  private boolean fetchToken(final Upstream upstream, final String repository, final String challenge) {
    if (challenge == null || !challenge.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
      return false;
    }

    Map<String, String> parameters = parseChallenge(challenge.substring("Bearer ".length()));
    String realm = parameters.get("realm");
    if (realm == null || realm.isEmpty()) {
      return false;
    }

    String service = parameters.get("service");
    String scope = parameters.get("scope");
    if (scope == null || scope.isEmpty()) {
      scope = "repository:" + repository + ":pull";
    }

    try {
      StringBuilder url = new StringBuilder(realm);
      url.append(realm.contains("?") ? '&' : '?');
      if (service != null && !service.isEmpty()) {
        url.append("service=").append(encode(service)).append('&');
      }
      url.append("scope=").append(encode(scope));

      HttpRequest tokenRequest = HttpRequest.newBuilder(URI.create(url.toString()))
          .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
          .header("User-Agent", "nexus-docker-proxy/1.0")
          .GET()
          .build();

      HttpResponse<String> tokenResponse =
          httpClient.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
      if (tokenResponse.statusCode() != HttpServletResponse.SC_OK) {
        log.debug("Token request to {} for {} returned {}", realm, repository, tokenResponse.statusCode());
        return false;
      }

      JsonNode json = MAPPER.readTree(tokenResponse.body());
      String token = text(json, "token");
      if (token == null) {
        token = text(json, "access_token");
      }
      if (token == null) {
        return false;
      }

      long expiresIn = json.path("expires_in").asLong(120);
      long ttlMillis = Math.max(expiresIn - 30, 10) * 1000L;
      tokens.put(tokenKey(upstream, repository), new CachedToken(token, System.currentTimeMillis() + ttlMillis));
      return true;
    }
    catch (Exception e) {
      log.debug("Could not obtain Docker token from {} for {}", realm, repository, e);
      return false;
    }
  }

  private String validToken(final Upstream upstream, final String repository) {
    CachedToken cached = tokens.get(tokenKey(upstream, repository));
    if (cached == null) {
      return null;
    }
    if (cached.expiresAt <= System.currentTimeMillis()) {
      tokens.remove(tokenKey(upstream, repository));
      return null;
    }
    return cached.token;
  }

  private static String tokenKey(final Upstream upstream, final String repository) {
    return upstream.name() + '|' + repository;
  }

  static Map<String, String> parseChallenge(final String value) {
    Map<String, String> result = new LinkedHashMap<>();
    int index = 0;
    while (index < value.length()) {
      while (index < value.length() && (value.charAt(index) == ',' || Character.isWhitespace(value.charAt(index)))) {
        index++;
      }
      int equals = value.indexOf('=', index);
      if (equals < 0) {
        break;
      }
      String key = value.substring(index, equals).trim().toLowerCase(Locale.ROOT);
      index = equals + 1;
      String parameter;
      if (index < value.length() && value.charAt(index) == '"') {
        int end = value.indexOf('"', index + 1);
        if (end < 0) {
          end = value.length();
        }
        parameter = value.substring(index + 1, end);
        index = end + 1;
      }
      else {
        int comma = value.indexOf(',', index);
        if (comma < 0) {
          comma = value.length();
        }
        parameter = value.substring(index, comma).trim();
        index = comma;
      }
      result.put(key, parameter);
    }
    return result;
  }

  private static void copyRequestHeader(
      final HttpServletRequest request,
      final HttpRequest.Builder builder,
      final String name)
  {
    String value = request.getHeader(name);
    if (value != null && !value.isEmpty()) {
      builder.header(name, value);
    }
  }

  private void copyResponse(
      final HttpServletResponse response,
      final Download download) throws IOException
  {
    copyResponseHeaders(download.headers, response);
    response.setStatus(download.status);
    if (download.body != null) {
      try (InputStream input = download.body) {
        input.transferTo(response.getOutputStream());
        response.getOutputStream().flush();
      }
    }
    else if (download.status >= 400) {
      response.sendError(download.status);
    }
  }

  private static void copyResponseHeaders(final HttpHeaders headers, final HttpServletResponse response) {
    copyHeader(headers, response, "Content-Type");
    headers.firstValueAsLong("Content-Length").ifPresent(response::setContentLengthLong);
    copyHeader(headers, response, "Docker-Content-Digest");
    copyHeader(headers, response, "ETag");
    copyHeader(headers, response, "Last-Modified");
    copyHeader(headers, response, "Accept-Ranges");
    copyHeader(headers, response, "Content-Range");
    copyHeader(headers, response, "Cache-Control");
    copyHeader(headers, response, "Expires");
  }

  private static void copyHeader(
      final HttpHeaders headers,
      final HttpServletResponse response,
      final String name)
  {
    headers.firstValue(name).ifPresent(value -> response.setHeader(name, value));
  }

  private static String contentType(final Entry entry) {
    return entry.contentType() != null && !entry.contentType().isEmpty()
        ? entry.contentType()
        : "application/octet-stream";
  }

  private static String text(final JsonNode node, final String field) {
    JsonNode value = node.get(field);
    return value != null && value.isTextual() ? value.asText() : null;
  }

  private static String encode(final String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  static boolean validRepository(final String repository) {
    if (repository == null || repository.isEmpty() || repository.indexOf("..") >= 0
        || repository.indexOf(' ') >= 0 || repository.indexOf('\\') >= 0) {
      return false;
    }
    for (String segment : repository.split("/")) {
      if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
        return false;
      }
    }
    return true;
  }

  private static boolean validReference(final String reference) {
    return reference != null && !reference.isEmpty() && reference.indexOf('/') < 0
        && reference.indexOf("..") < 0 && reference.indexOf(' ') < 0;
  }

  static boolean isDigest(final String value) {
    return value != null && value.matches("[A-Za-z0-9_+.-]+:[0-9a-fA-F]{32,}");
  }

  static Range parseRange(final String header, final long size) {
    if (header == null || !header.startsWith("bytes=") || header.indexOf(',') >= 0) {
      return null;
    }
    String value = header.substring("bytes=".length()).trim();
    int dash = value.indexOf('-');
    if (dash < 0) {
      return null;
    }
    try {
      long start;
      long end;
      if (dash == 0) {
        long suffix = Long.parseLong(value.substring(1));
        start = Math.max(0, size - suffix);
        end = size - 1;
      }
      else {
        start = Long.parseLong(value.substring(0, dash));
        end = dash == value.length() - 1 ? size - 1 : Long.parseLong(value.substring(dash + 1));
      }
      if (start < 0 || start >= size || end < start) {
        return Range.UNSATISFIABLE;
      }
      end = Math.min(end, size - 1);
      return new Range(start, end);
    }
    catch (NumberFormatException e) {
      return Range.UNSATISFIABLE;
    }
  }

  static final class Range
  {
    static final Range UNSATISFIABLE = new Range(-1, -1);

    final long start;

    final long end;

    private Range(final long start, final long end) {
      this.start = start;
      this.end = end;
    }

    long length() {
      return end - start + 1;
    }
  }

  private static final class CachedToken
  {
    private final String token;

    private final long expiresAt;

    private CachedToken(final String token, final long expiresAt) {
      this.token = token;
      this.expiresAt = expiresAt;
    }
  }

  private static final class Download
      implements AutoCloseable
  {
    private final int status;

    private final HttpHeaders headers;

    private InputStream body;

    private Path temp;

    private Download(final int status, final HttpHeaders headers) {
      this.status = status;
      this.headers = headers;
    }

    private String contentType() {
      return headers.firstValue("Content-Type").orElse(null);
    }

    private String digest() {
      return headers.firstValue("Docker-Content-Digest").orElse(null);
    }

    private String etag() {
      return headers.firstValue("ETag").orElse(null);
    }

    @Override
    public void close() throws IOException {
      if (body != null) {
        body.close();
      }
      if (temp != null) {
        Files.deleteIfExists(temp);
      }
    }
  }
}
