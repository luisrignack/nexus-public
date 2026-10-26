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

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.annotation.Nonnull;

import org.sonatype.nexus.content.alpine.AlpineContentFacet;
import org.sonatype.nexus.repository.cache.CacheController;
import org.sonatype.nexus.repository.content.facet.ContentProxyFacetSupport;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.Context;
import org.sonatype.nexus.repository.view.Parameters;
import org.sonatype.nexus.repository.view.matchers.token.TokenMatcher;

import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.HttpRequestBase;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Proxy facet for Alpine repositories.
 *
 * <p>Alpine repositories are plain directory trees: {@code <branch>/<repo>/<arch>/<file>}. The
 * upstream {@code APKINDEX.tar.gz} contains no self-references, so bodies are cached byte for byte
 * and no rewriting is needed.</p>
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class AlpineProxyFacet
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
    String path = assetPath(context);
    String remote = getRemoteUrl().toString();
    while (remote.endsWith("/")) {
      remote = remote.substring(0, remote.length() - 1);
    }
    return remote + path + query(context.getRequest().getParameters());
  }

  @Override
  protected HttpRequestBase buildFetchHttpRequest(
      final java.net.URI uri,
      final Context context,
      final Content stale)
  {
    HttpRequestBase request = super.buildFetchHttpRequest(uri, context, stale);
    request.setHeader(HttpHeaders.ACCEPT, "*/*");
    // APKINDEX.tar.gz and .apk files must be cached exactly as published
    request.setHeader(HttpHeaders.ACCEPT_ENCODING, "identity");
    request.setHeader(HttpHeaders.USER_AGENT, "nexus-alpine-proxy/1.0");
    return request;
  }

  @Nonnull
  @Override
  protected CacheController getCacheController(@Nonnull final Context context) {
    if (AlpineContentHandler.isIndex(assetPath(context))) {
      return cacheControllerHolder.getMetadataCacheController();
    }
    return cacheControllerHolder.getContentCacheController();
  }

  private static String query(final Parameters parameters) {
    if (parameters == null || parameters.isEmpty()) {
      return "";
    }
    StringBuilder query = new StringBuilder();
    for (Map.Entry<String, String> entry : parameters.entries()) {
      query.append(query.length() == 0 ? '?' : '&')
          .append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
          .append('=')
          .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
    }
    return query.toString();
  }

  private AlpineContentFacet content() {
    return getRepository().facet(AlpineContentFacet.class);
  }

  private static String assetPath(final Context context) {
    TokenMatcher.State state = context.getAttributes().require(TokenMatcher.State.class);
    String path = state.getTokens().get(AlpineRecipeSupport.PATH_NAME);
    if (path == null || path.isEmpty()) {
      return "/";
    }
    return path.startsWith("/") ? path : "/" + path;
  }
}
