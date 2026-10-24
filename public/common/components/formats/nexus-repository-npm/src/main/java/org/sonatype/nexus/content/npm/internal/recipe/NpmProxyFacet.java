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
package org.sonatype.nexus.content.npm.internal.recipe;

import java.io.IOException;

import javax.annotation.Nonnull;

import org.sonatype.nexus.content.npm.NpmContentFacet;
import org.sonatype.nexus.repository.cache.CacheController;
import org.sonatype.nexus.repository.content.facet.ContentProxyFacetSupport;
import org.sonatype.nexus.repository.npm.internal.NpmCoordinatesHelper;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.Context;
import org.sonatype.nexus.repository.view.matchers.token.TokenMatcher;

import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.HttpRequestBase;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * npm proxy facet.
 *
 * <p>Packuments and other metadata use the metadata cache controller, tarballs use the content cache controller.
 * Scoped package metadata is requested from the upstream registry with the scope separator encoded as
 * {@code %2F}; tarball paths keep their normal layout.</p>
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class NpmProxyFacet
    extends ContentProxyFacetSupport
{
  @Override
  protected Content getCachedContent(final Context context) throws IOException {
    return content().get(assetPath(context)).orElse(null);
  }

  @Override
  protected Content store(final Context context, final Content content) throws IOException {
    return content().put(assetPath(context), content);
  }

  @Override
  protected String getUrl(@Nonnull final Context context) {
    String path = NpmCoordinatesHelper.stripLeadingSlash(assetPath(context));
    if (isTarball(path)) {
      return path;
    }
    if (path.equals("-/v1/search")) {
      String query = queryString(context);
      if (!query.isEmpty()) {
        return path + '?' + query;
      }
    }
    return encodeMetadataPath(path);
  }

  /**
   * npm search uses query parameters ({@code text}, {@code size}, {@code from}) that must reach the upstream
   * registry when this repository proxies {@code /-/v1/search}.
   */
  private static String queryString(final Context context) {
    StringBuilder query = new StringBuilder();
    for (java.util.Map.Entry<String, String> entry : context.getRequest().getParameters().entries()) {
      if (query.length() > 0) {
        query.append('&');
      }
      query.append(java.net.URLEncoder.encode(entry.getKey(), java.nio.charset.StandardCharsets.UTF_8))
          .append('=')
          .append(java.net.URLEncoder.encode(entry.getValue(), java.nio.charset.StandardCharsets.UTF_8));
    }
    return query.toString();
  }

  @Override
  protected String encodeUrl(final String url) {
    return url;
  }

  @Override
  protected HttpRequestBase buildFetchHttpRequest(
      final java.net.URI uri,
      final Context context,
      final Content stale)
  {
    HttpRequestBase request = super.buildFetchHttpRequest(uri, context, stale);
    // Keep JSON documents uncompressed so the proxy handler can rewrite tarball URLs.
    request.setHeader(HttpHeaders.ACCEPT_ENCODING, "identity");
    return request;
  }

  @Nonnull
  @Override
  protected CacheController getCacheController(@Nonnull final Context context) {
    String path = NpmCoordinatesHelper.stripLeadingSlash(assetPath(context));
    return isTarball(path)
        ? cacheControllerHolder.getContentCacheController()
        : cacheControllerHolder.getMetadataCacheController();
  }

  private NpmContentFacet content() {
    return getRepository().facet(NpmContentFacet.class);
  }

  private static boolean isTarball(final String path) {
    return path.contains("/-/") && (path.endsWith(".tgz") || path.endsWith(".tar.gz"));
  }

  /**
   * Encodes the scope separator for the upstream metadata endpoint: {@code @scope/name} becomes
   * {@code @scope%2Fname}.
   */
  private static String encodeMetadataPath(final String path) {
    if (path.startsWith("@")) {
      int slash = path.indexOf('/');
      if (slash > 0) {
        return path.substring(0, slash) + "%2F" + path.substring(slash + 1);
      }
    }
    return path;
  }

  private String assetPath(final Context context) {
    TokenMatcher.State state = context.getAttributes().require(TokenMatcher.State.class);
    String path = state.getTokens().get(NpmRecipeSupport.PATH_NAME);
    return "/" + NpmCoordinatesHelper.stripLeadingSlash(NpmCoordinatesHelper.decode(path));
  }
}
