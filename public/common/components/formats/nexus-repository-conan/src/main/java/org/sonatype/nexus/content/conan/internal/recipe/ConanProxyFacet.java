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
package org.sonatype.nexus.content.conan.internal.recipe;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import javax.annotation.Nonnull;

import org.sonatype.nexus.content.conan.ConanContentFacet;
import org.sonatype.nexus.repository.cache.CacheController;
import org.sonatype.nexus.repository.content.facet.ContentProxyFacetSupport;
import org.sonatype.nexus.repository.conan.ConanCoordinates;
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
 * Proxy facet for the Conan format.
 *
 * <p>Requests are forwarded to {@code <remote>/<request path>} (query parameters included), API documents are
 * cached as metadata and {@code download_urls} bodies are rewritten on the way out so clients download through
 * Nexus instead of straight from the remote.</p>
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ConanProxyFacet
    extends ContentProxyFacetSupport
{
  @Override
  protected Content getCachedContent(final Context context) throws IOException {
    String path = assetPath(context);
    if (isVolatile(path)) {
      return null;
    }
    return content().get(path).orElse(null);
  }

  @Override
  protected Content store(final Context context, final Content content) throws IOException {
    String path = assetPath(context);
    if (isVolatile(path)) {
      // query dependent documents (search) are never cacheable
      return content;
    }

    if (path.endsWith("/download_urls")) {
      return content().put(path, rewriteDownloadUrls(content));
    }
    return content().put(path, content);
  }

  @Override
  protected String getUrl(@Nonnull final Context context) {
    String remote = trimTrailingSlash(getRemoteUrl().toString());
    return remote + "/" + requestPath(context) + query(context);
  }

  @Nonnull
  @Override
  protected CacheController getCacheController(@Nonnull final Context context) {
    String path = assetPath(context);
    if (isVolatile(path) || isMetadata(path)) {
      return cacheControllerHolder.getMetadataCacheController();
    }
    return cacheControllerHolder.getContentCacheController();
  }

  @Override
  protected HttpRequestBase buildFetchHttpRequest(
      final java.net.URI uri,
      final Context context,
      final Content stale)
  {
    HttpRequestBase request = super.buildFetchHttpRequest(uri, context, stale);
    // keep the API documents uncompressed so they can be rewritten
    request.setHeader(HttpHeaders.ACCEPT_ENCODING, "identity");
    return request;
  }

  private Content rewriteDownloadUrls(final Content content) throws IOException {
    String body;
    try (InputStream input = content.openInputStream()) {
      body = new String(ByteStreams.toByteArray(input), StandardCharsets.UTF_8);
    }

    String rewritten = ConanDownloadUrlsRewriter.rewriteFromRemote(
        body,
        getRemoteUrl().toString(),
        getRepository().getUrl());

    if (rewritten.equals(body)) {
      return content;
    }
    return new Content(content,
        new BytesPayload(rewritten.getBytes(StandardCharsets.UTF_8), APPLICATION_JSON));
  }

  /**
   * Search results depend on the query string and pings are trivial, neither is worth caching.
   */
  private static boolean isVolatile(final String path) {
    return path.endsWith("/search") || path.endsWith("/ping");
  }

  private static boolean isMetadata(final String path) {
    return path.endsWith("/revisions")
        || path.endsWith("/files")
        || path.endsWith("/download_urls");
  }

  private ConanContentFacet content() {
    return getRepository().facet(ConanContentFacet.class);
  }

  /**
   * The request path including the {@code v1/} or {@code v2/} API prefix, as sent by the client.
   */
  private static String requestPath(final Context context) {
    TokenMatcher.State state = context.getAttributes().get(TokenMatcher.State.class);
    String path = state != null ? state.getTokens().get(ConanRecipeSupport.PATH_NAME) : null;
    if (path == null) {
      path = context.getRequest().getPath();
    }
    return ConanCoordinates.normalize(path);
  }

  /**
   * Storage path of the request: the API prefix is dropped so cached assets line up with the layout used by
   * hosted repositories.
   */
  private static String assetPath(final Context context) {
    String path = requestPath(context);
    if (path.startsWith("v1/") || path.startsWith("v2/")) {
      return path.substring(3);
    }
    return path;
  }

  private static String query(final Context context) {
    Parameters parameters = context.getRequest().getParameters();
    if (parameters == null || parameters.isEmpty()) {
      return "";
    }

    StringBuilder query = new StringBuilder();
    for (final String name : parameters.names()) {
      for (final String value : parameters.getAll(name)) {
        if (query.length() > 0) {
          query.append('&');
        }
        query.append(encode(name)).append('=').append(encode(value));
      }
    }
    return query.length() == 0 ? "" : "?" + query;
  }

  private static String encode(final String value) {
    try {
      return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
    }
    catch (final UnsupportedEncodingException e) {
      return value;
    }
  }

  private static String trimTrailingSlash(final String value) {
    String result = value;
    while (result.endsWith("/")) {
      result = result.substring(0, result.length() - 1);
    }
    return result;
  }
}
