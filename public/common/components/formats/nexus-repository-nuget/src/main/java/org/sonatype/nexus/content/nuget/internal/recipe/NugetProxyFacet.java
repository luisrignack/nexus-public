/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype Nexus" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.content.nuget.internal.recipe;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.sonatype.nexus.content.nuget.NugetContentFacet;
import org.sonatype.nexus.repository.cache.CacheController;
import org.sonatype.nexus.repository.cache.CacheInfo;
import org.sonatype.nexus.repository.config.Configuration;
import org.sonatype.nexus.repository.config.ConfigurationFacet;
import org.sonatype.nexus.repository.content.facet.ContentProxyFacetSupport;
import org.sonatype.nexus.repository.nuget.NugetCoordinatesHelper;
import org.sonatype.nexus.repository.nuget.NugetServiceIndex;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.Context;
import org.sonatype.nexus.repository.view.Parameters;
import org.sonatype.nexus.repository.view.matchers.token.TokenMatcher;
import org.sonatype.nexus.repository.view.payloads.BytesPayload;

import com.google.common.io.ByteStreams;

import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.HttpRequestBase;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import static org.sonatype.nexus.repository.view.ContentTypes.APPLICATION_JSON;

/**
 * Proxy facet for NuGet.
 *
 * <p>Documents fetched from the upstream (the service index, registration pages and search results) are rewritten
 * so every URL points back at this repository before they are cached. When the repository is configured for the
 * v2 protocol the service index is generated locally instead of being fetched.</p>
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class NugetProxyFacet
    extends ContentProxyFacetSupport
{
  public static final String CONFIG_SECTION = "nugetProxy";

  public static final String V2 = "V2";

  public static final String V3 = "V3";

  /**
   * Maps the {@code nugetProxy} section of the repository configuration.
   */
  public static class Config
  {
    public String nugetVersion = V3;

    public Integer queryCacheItemMaxAge = 3600;
  }

  private static final String PACKAGE_BASE_ADDRESS_ATTRIBUTE = "nuget.packageBaseAddress";

  private static final String REGISTRATION_BASE_ATTRIBUTE = "nuget.registrationBaseAddress";

  private static final String SEARCH_ENDPOINT_ATTRIBUTE = "nuget.searchEndpoint";

  private static final String PACKAGE_BASE_ADDRESS_TYPE = "PackageBaseAddress/3.";

  private static final String REGISTRATION_BASE_TYPE = "RegistrationsBaseUrl/";

  private Config nugetConfig = new Config();

  /**
   * Upstream search service discovered while rewriting a service index, {@code null} until then.
   */
  private volatile String searchEndpoint;

  /**
   * Upstream flat-container base discovered in the service index, {@code null} until then.
   */
  private volatile String packageBaseAddress;

  /**
   * Upstream registration base discovered in the service index, {@code null} until then.
   */
  private volatile String registrationBaseAddress;

  /**
   * Whether the endpoints persisted on the cached service index have already been loaded.
   */
  private volatile boolean resourcesLoaded;

  @Override
  protected void doConfigure(final Configuration configuration) throws Exception {
    super.doConfigure(configuration);
    Config read = facet(ConfigurationFacet.class).readSection(configuration, CONFIG_SECTION, Config.class);
    if (read != null) {
      nugetConfig = read;
    }
    if (nugetConfig.nugetVersion == null || nugetConfig.nugetVersion.trim().isEmpty()) {
      nugetConfig.nugetVersion = V3;
    }
    if (nugetConfig.queryCacheItemMaxAge == null || nugetConfig.queryCacheItemMaxAge < 0) {
      nugetConfig.queryCacheItemMaxAge = 3600;
    }
  }

  public String getNugetVersion() {
    return nugetConfig.nugetVersion;
  }

  public int getQueryCacheItemMaxAge() {
    return nugetConfig.queryCacheItemMaxAge;
  }

  @Override
  protected Content getCachedContent(final Context context) throws IOException {
    return content().get(cachePath(context)).orElse(null);
  }

  @Override
  protected Content store(final Context context, final Content content) throws IOException {
    String path = cachePath(context);

    if (isJsonPath(assetPath(context))) {
      byte[] rewritten = rewrite(context, content);
      Content transformed = rewritten != null
          ? new Content(content, new BytesPayload(rewritten, APPLICATION_JSON))
          : content;
      Map<String, Object> attributes = serviceIndexAttributes(context);
      return attributes.isEmpty()
          ? content().put(path, transformed)
          : content().put(path, transformed, attributes);
    }

    return content().put(path, content);
  }

  private Map<String, Object> serviceIndexAttributes(final Context context) {
    Map<String, Object> attributes = new HashMap<>();
    if (isServiceIndex(assetPath(context))) {
      if (searchEndpoint != null) {
        attributes.put(SEARCH_ENDPOINT_ATTRIBUTE, searchEndpoint);
      }
      if (packageBaseAddress != null) {
        attributes.put(PACKAGE_BASE_ADDRESS_ATTRIBUTE, packageBaseAddress);
      }
      if (registrationBaseAddress != null) {
        attributes.put(REGISTRATION_BASE_ATTRIBUTE, registrationBaseAddress);
      }
    }
    return attributes;
  }
  @Override
  protected Content fetch(final Context context, @Nullable final Content stale) throws IOException {
    if (isV2() && isServiceIndex(assetPath(context))) {
      // a v2 upstream has no service index: generate one that points back at this repository
      Content content = new Content(new BytesPayload(
          NugetServiceIndex.buildV2(context.getRepository().getUrl()), APPLICATION_JSON));
      content.getAttributes().set(CacheInfo.class, getCacheController(context).current());
      return content;
    }
    return super.fetch(context, stale);
  }

  @Override
  protected String getUrl(@Nonnull final Context context) {
    String path = NugetCoordinatesHelper.stripLeadingSlash(assetPath(context));
    Parameters parameters = context.getRequest().getParameters();

    loadDiscoveredResources();

    String remote;
    if (NugetCoordinatesHelper.QUERY.equals(path) && searchEndpoint != null) {
      // the upstream search service may live on another host and path than the configured remote url
      remote = NugetCoordinatesHelper.stripTrailingSlash(searchEndpoint);
      return appendQuery(remote, parameters);
    }

    if (path.startsWith(NugetCoordinatesHelper.FLAT_CONTAINER + "/")) {
      String base = flatContainerBase();
      if (base != null) {
        String suffix = path.substring(NugetCoordinatesHelper.FLAT_CONTAINER.length() + 1);
        return appendQuery(NugetCoordinatesHelper.ensureTrailingSlash(base) + suffix, parameters);
      }
    }

    if (path.startsWith("registration") && registrationBaseAddress != null) {
      int slash = path.indexOf('/');
      if (slash > 0) {
        return appendQuery(NugetServiceIndex.registrationUrl(registrationBaseAddress, path), parameters);
      }
    }

    remote = remoteBase();
    String url = remote + "/" + path;
    return appendQuery(url, parameters);
  }

  /**
   * Upstream flat-container base: the {@code PackageBaseAddress/3.0.0} resource of the service index when it has
   * been seen, otherwise a best-effort value derived from the configured service index URL.
   */
  @Nullable
  private String flatContainerBase() {
    if (packageBaseAddress != null) {
      return packageBaseAddress;
    }
    String remote = NugetCoordinatesHelper.stripTrailingSlash(getRemoteUrl().toString());
    String v3Index = "/v3/" + NugetCoordinatesHelper.INDEX_JSON;
    if (remote.endsWith(v3Index)) {
      return remote.substring(0, remote.length() - v3Index.length()) + "/v3-flatcontainer/";
    }
    String index = "/" + NugetCoordinatesHelper.INDEX_JSON;
    if (remote.endsWith(index)) {
      return remote.substring(0, remote.length() - index.length()) + "/v3-flatcontainer/";
    }
    return null;
  }

  /**
   * Reloads the upstream endpoints persisted as attributes of the cached service index, so a restart does not lose
   * them before the index is fetched again.
   */
  private void loadDiscoveredResources() {
    if (resourcesLoaded) {
      return;
    }
    resourcesLoaded = true;
    try {
      content().assetAttributes("/" + NugetCoordinatesHelper.INDEX_JSON).ifPresent(attributes -> {
        Object search = attributes.get(SEARCH_ENDPOINT_ATTRIBUTE);
        if (search != null) {
          searchEndpoint = String.valueOf(search);
        }
        Object packageBase = attributes.get(PACKAGE_BASE_ADDRESS_ATTRIBUTE);
        if (packageBase != null) {
          packageBaseAddress = String.valueOf(packageBase);
        }
        Object registrationBase = attributes.get(REGISTRATION_BASE_ATTRIBUTE);
        if (registrationBase != null) {
          registrationBaseAddress = String.valueOf(registrationBase);
        }
      });
    }
    catch (IOException e) {
      // fall back to the configured remote URL
    }
  }

  /**
   * Base URL the upstream paths are appended to: the configured remote URL minus its {@code index.json} file name
   * (the repository wizard pre-fills the service index URL) and minus any trailing slash.
   */
  private String remoteBase() {
    String remote = NugetCoordinatesHelper.stripTrailingSlash(getRemoteUrl().toString());
    if (remote.endsWith("/" + NugetCoordinatesHelper.INDEX_JSON)) {
      remote = remote.substring(0, remote.length() - ("/" + NugetCoordinatesHelper.INDEX_JSON).length());
    }
    return NugetCoordinatesHelper.stripTrailingSlash(remote);
  }

  private static String appendQuery(final String url, final Parameters parameters) {
    if (parameters.isEmpty()) {
      return url;
    }
    StringBuilder query = new StringBuilder(url).append('?');
    boolean first = true;
    for (Map.Entry<String, String> parameter : parameters.entries()) {
      if (!first) {
        query.append('&');
      }
      first = false;
      query.append(encode(parameter.getKey())).append('=').append(encode(parameter.getValue()));
    }
    return query.toString();
  }

  @Override
  protected HttpRequestBase buildFetchHttpRequest(
      final URI uri,
      final Context context,
      final Content stale)
  {
    HttpRequestBase request = super.buildFetchHttpRequest(uri, context, stale);
    if (isJsonPath(assetPath(context))) {
      // keep the document uncompressed so its URLs can be rewritten
      request.setHeader(HttpHeaders.ACCEPT_ENCODING, "identity");
    }
    return request;
  }

  @Nonnull
  @Override
  protected CacheController getCacheController(@Nonnull final Context context) {
    String path = NugetCoordinatesHelper.stripLeadingSlash(assetPath(context));

    if (NugetCoordinatesHelper.QUERY.equals(path)) {
      // search results honour the queryCacheItemMaxAge setting of the repository
      int maxAgeSeconds = Math.max(0, getQueryCacheItemMaxAge()) * 60;
      String token = cacheControllerHolder.getMetadataCacheController().current().getCacheToken();
      return new CacheController(maxAgeSeconds, token);
    }

    return isJsonPath(path)
        ? cacheControllerHolder.getMetadataCacheController()
        : cacheControllerHolder.getContentCacheController();
  }

  private boolean isV2() {
    return V2.equalsIgnoreCase(getNugetVersion());
  }

  /**
   * Rewrites absolute upstream URLs so they point at this repository; returns {@code null} when nothing had to be
   * changed.
   */
  @Nullable
  private byte[] rewrite(final Context context, final Content content) throws IOException {
    byte[] body;
    try (InputStream input = content.openInputStream()) {
      body = ByteStreams.toByteArray(input);
    }
    String json = new String(body, StandardCharsets.UTF_8);

    String localBase = context.getRepository().getUrl();

    // remember where the upstream expects search requests before the URL is replaced
    String searchService = NugetServiceIndex.searchServiceUrl(json);
    if (searchService != null) {
      searchEndpoint = searchService;
    }

    // remember the upstream flat-container and registration bases; the local service index advertises
    // relative paths such as /v3-flatcontainer/..., but the upstream base is not necessarily <remoteBase>/...
    String packageBase = NugetServiceIndex.resourceUrl(json, PACKAGE_BASE_ADDRESS_TYPE);
    if (packageBase != null) {
      packageBaseAddress = packageBase;
    }
    String registrationBase = NugetServiceIndex.resourceUrl(json, REGISTRATION_BASE_TYPE);
    if (registrationBase != null) {
      registrationBaseAddress = registrationBase;
    }

    String rewritten = NugetServiceIndex.rewriteUrls(json, remoteBase(), localBase);
    rewritten = NugetServiceIndex.localizeSearchResources(rewritten, localBase);

    return rewritten.equals(json) ? null : rewritten.getBytes(StandardCharsets.UTF_8);
  }

  private String cachePath(final Context context) {
    String path = assetPath(context);
    Parameters parameters = context.getRequest().getParameters();
    if (parameters.isEmpty()) {
      return path;
    }
    // query parameters take part of the cache key (for instance v3/query?q=...)
    return path + "?" + queryString(parameters);
  }

  private static String queryString(final Parameters parameters) {
    StringBuilder query = new StringBuilder();
    boolean first = true;
    for (Map.Entry<String, String> parameter : parameters.entries()) {
      if (!first) {
        query.append('&');
      }
      first = false;
      query.append(encode(parameter.getKey())).append('=').append(encode(parameter.getValue()));
    }
    return query.toString();
  }

  private static String encode(final String value) {
    try {
      return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name())
          .replace("+", "%20");
    }
    catch (UnsupportedEncodingException e) {
      return value;
    }
  }

  private static boolean isServiceIndex(final String path) {
    return NugetCoordinatesHelper.INDEX_JSON.equals(NugetCoordinatesHelper.stripLeadingSlash(path));
  }

  private static boolean isJsonPath(final String path) {
    String normalized = NugetCoordinatesHelper.stripLeadingSlash(path);
    return isServiceIndex(path)
        || NugetCoordinatesHelper.QUERY.equals(normalized)
        || normalized.startsWith(NugetCoordinatesHelper.QUERY + "/")
        || normalized.startsWith(NugetCoordinatesHelper.REGISTRATION + "/")
        || normalized.startsWith("Packages(")
        || normalized.startsWith("FindPackagesById");
  }

  private String assetPath(final Context context) {
    TokenMatcher.State state = context.getAttributes().require(TokenMatcher.State.class);
    String path = state.getTokens().get(NugetRecipeSupport.PATH_NAME);
    String normalized = NugetCoordinatesHelper.stripLeadingSlash(path);
    return normalized.isEmpty() ? "/" : "/" + normalized;
  }

  private NugetContentFacet content() {
    return getRepository().facet(NugetContentFacet.class);
  }
}
