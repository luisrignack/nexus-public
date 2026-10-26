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
package org.sonatype.nexus.content.cargo.internal.recipe;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nonnull;

import org.sonatype.nexus.content.cargo.CargoContentFacet;
import org.sonatype.nexus.repository.cargo.CargoCoordinatesHelper;
import org.sonatype.nexus.repository.cache.CacheController;
import org.sonatype.nexus.repository.content.facet.ContentProxyFacetSupport;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.Context;
import org.sonatype.nexus.repository.view.matchers.token.TokenMatcher;
import org.sonatype.nexus.repository.view.payloads.BytesPayload;

import com.google.common.io.ByteStreams;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.HttpRequestBase;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import static org.sonatype.nexus.repository.cargo.CargoCoordinatesHelper.API_ROOT;
import static org.sonatype.nexus.repository.cargo.CargoCoordinatesHelper.INDEX_DIR;
import static org.sonatype.nexus.repository.view.ContentTypes.APPLICATION_JSON;

/**
 * Proxy facet for the cargo format.
 *
 * <p>Routing assumptions, documented in the module README:</p>
 * <ul>
 *   <li>{@code proxy.remoteUrl} is the root of a crates.io compatible sparse index, for example
 *   {@code https://index.crates.io}; {@code index/...} requests are forwarded with the {@code index/} prefix
 *   stripped.</li>
 *   <li>{@code api/v1/crates/...} requests (search, crate detail and {@code .crate} downloads) are forwarded to the
 *   {@code api} / {@code dl} bases announced by the upstream {@code index/config.json} - exactly like the cargo
 *   client does. The upstream values are remembered as asset attributes when the configuration is cached; until
 *   then requests fall back to {@code <remoteUrl>/api/v1/crates/...}.</li>
 *   <li>The cached {@code index/config.json} is rewritten so {@code dl} and {@code api} point at this
 *   repository.</li>
 * </ul>
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class CargoProxyFacet
    extends ContentProxyFacetSupport
{
  @Override
  protected Content getCachedContent(final Context context) throws IOException {
    return content().get(assetPath(context)).orElse(null);
  }

  @Override
  protected Content store(final Context context, final Content content) throws IOException {
    String path = assetPath(context);

    if (CargoCoordinatesHelper.isConfigPath(path)) {
      String upstream = read(content);
      String base = repositoryBase(context);

      Map<String, Object> attributes = new HashMap<>();
      attributes.put(CargoContentFacet.FILE_NAME_ATTRIBUTE, "config.json");

      String remoteDl = CargoIndexBuilder.jsonField(upstream, "dl");
      String remoteApi = CargoIndexBuilder.jsonField(upstream, "api");
      if (!remoteDl.isEmpty()) {
        attributes.put(CargoContentFacet.REMOTE_DL_ATTRIBUTE, remoteDl);
      }
      if (!remoteApi.isEmpty()) {
        attributes.put(CargoContentFacet.REMOTE_API_ATTRIBUTE, remoteApi);
      }

      String rewritten = CargoIndexBuilder.rewriteConfig(upstream, base);
      return content().put(path, new BytesPayload(rewritten.getBytes(StandardCharsets.UTF_8), APPLICATION_JSON),
          attributes);
    }

    if (CargoCoordinatesHelper.parseDownload(path).isPresent()) {
      // store the crate with the gzip content type instead of wrapping the returned content: the upstream payload
      // is closed once it has been ingested, so a wrapper around it cannot be streamed to the client afterwards
      return content().put(path, new CargoPayload(content.getPayload(), "application/gzip"));
    }

    return content().put(path, content);
  }

  @Override
  protected String getUrl(@Nonnull final Context context) {
    String path = assetPath(context);
    String remote = CargoCoordinatesHelper.stripTrailingSlash(getRemoteUrl().toString());

    final String url;
    if (path.startsWith(INDEX_DIR + "/")) {
      // the remote is the index root itself: index/config.json -> <remote>/config.json
      url = remote + "/" + path.substring(INDEX_DIR.length() + 1);
    }
    else if (API_ROOT.equals(path) || path.startsWith(API_ROOT + "/")) {
      String discovered = CargoCoordinatesHelper.parseDownload(path).isPresent()
          ? downloadUrl(path)
          : apiUrl(path);
      url = discovered != null ? discovered : remote + "/" + path;
    }
    else {
      url = remote + "/" + path;
    }

    String query = CargoCoordinatesHelper.queryString(context.getRequest().getParameters());
    return query.isEmpty() ? url : url + "?" + query;
  }

  /**
   * Upstream URL of a crate download. Cargo appends {@code /{crate}/{version}/download} to the {@code dl} base
   * announced by {@code config.json}, so the {@code api/v1/crates} prefix is replaced by that base.
   */
  private String downloadUrl(final String path) {
    Optional<String> dl = content().remoteDlBase();
    if (dl.isEmpty() || dl.get().contains("{")) {
      return null;
    }
    return CargoCoordinatesHelper.stripTrailingSlash(dl.get())
        + path.substring(CargoCoordinatesHelper.API_CRATES_PATH.length());
  }

  /**
   * Upstream URL of a crates.io API request. The {@code api} base is a root: cargo builds
   * {@code <api>/api/v1/crates/...} from it, but some registries advertise the full crates path already.
   */
  private String apiUrl(final String path) {
    Optional<String> api = content().remoteApiBase();
    if (api.isEmpty()) {
      return null;
    }
    String base = CargoCoordinatesHelper.stripTrailingSlash(api.get());
    if (base.endsWith("/" + CargoCoordinatesHelper.API_CRATES_PATH)) {
      return base + path.substring(CargoCoordinatesHelper.API_CRATES_PATH.length());
    }
    return base + "/" + path;
  }

  @Override
  protected HttpRequestBase buildFetchHttpRequest(
      final java.net.URI uri,
      final Context context,
      final Content stale)
  {
    HttpRequestBase request = super.buildFetchHttpRequest(uri, context, stale);
    // keep JSON documents uncompressed so they can be rewritten before caching
    request.setHeader(HttpHeaders.ACCEPT_ENCODING, "identity");
    return request;
  }

  @Nonnull
  @Override
  protected CacheController getCacheController(@Nonnull final Context context) {
    String path = assetPath(context);
    if (path.endsWith(CargoCoordinatesHelper.DOWNLOAD_SUFFIX)
        || CargoCoordinatesHelper.parseDownload(path).isPresent()) {
      return cacheControllerHolder.getContentCacheController();
    }
    if (path.startsWith(INDEX_DIR + "/") || API_ROOT.equals(path) || path.startsWith(API_ROOT + "/")) {
      return cacheControllerHolder.getMetadataCacheController();
    }
    return cacheControllerHolder.getContentCacheController();
  }

  private CargoContentFacet content() {
    return getRepository().facet(CargoContentFacet.class);
  }

  private static String repositoryBase(final Context context) {
    String url = context.getRepository().getUrl();
    return url.endsWith("/") ? url : url + "/";
  }

  private static String read(final Content content) throws IOException {
    try (java.io.InputStream input = content.openInputStream()) {
      return new String(ByteStreams.toByteArray(input), StandardCharsets.UTF_8);
    }
  }

  private String assetPath(final Context context) {
    TokenMatcher.State state = context.getAttributes().require(TokenMatcher.State.class);
    return CargoCoordinatesHelper.normalize(state.getTokens().get(CargoRecipeSupport.PATH_NAME));
  }
}
