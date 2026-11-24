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
package org.sonatype.nexus.content.pub.internal.recipe;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.annotation.Nonnull;

import org.sonatype.nexus.content.pub.PubContentFacet;
import org.sonatype.nexus.repository.cache.CacheController;
import org.sonatype.nexus.repository.content.facet.ContentProxyFacetSupport;
import org.sonatype.nexus.repository.pub.internal.PubCoordinatesHelper;
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

/**
 * Proxy facet for Pub repositories.
 *
 * <p>Upstream metadata contains absolute {@code archive_url} values pointing at pub.dev. If those
 * were cached unchanged the Pub client would leave the network behind Nexus on the next request.
 * Metadata bodies are therefore rewritten before being stored, while archives are cached byte for
 * byte.</p>
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class PubProxyFacet
    extends ContentProxyFacetSupport
{
  @Override
  protected Content getCachedContent(final Context context) throws IOException {
    return content().get(assetPath(context)).orElse(null);
  }

  @Override
  protected Content store(final Context context, final Content content) throws IOException {
    String path = assetPath(context);
    if (PubCoordinatesHelper.isMetadata(path)) {
      byte[] body;
      try (InputStream input = content.openInputStream()) {
        body = ByteStreams.toByteArray(input);
      }
      byte[] rewritten = PubMetadataBuilder.rewrite(repositoryUrl(), body);
      return content().put(path, new BytesPayload(rewritten, PubContentHandler.PUB_JSON));
    }
    return content().put(path, content);
  }

  @Override
  protected String getUrl(@Nonnull final Context context) {
    String path = assetPath(context);
    String remote = getRemoteUrl().toString();
    while (remote.endsWith("/")) {
      remote = remote.substring(0, remote.length() - 1);
    }
    return remote + (path.startsWith("/") ? path : "/" + path) + query(context.getRequest().getParameters());
  }

  @Override
  protected HttpRequestBase buildFetchHttpRequest(
      final java.net.URI uri,
      final Context context,
      final Content stale)
  {
    HttpRequestBase request = super.buildFetchHttpRequest(uri, context, stale);
    request.setHeader(HttpHeaders.ACCEPT, PubContentHandler.PUB_JSON);
    // Do not let an intermediary recompress JSON: the URLs must be rewritten before caching.
    request.setHeader(HttpHeaders.ACCEPT_ENCODING, "identity");
    request.setHeader(HttpHeaders.USER_AGENT, "nexus-pub-proxy/1.0");
    return request;
  }

  @Nonnull
  @Override
  protected CacheController getCacheController(@Nonnull final Context context) {
    return PubCoordinatesHelper.isArchive(assetPath(context))
        ? cacheControllerHolder.getContentCacheController()
        : cacheControllerHolder.getMetadataCacheController();
  }

  private String repositoryUrl() {
    String url = getRepository().getUrl();
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }

  private PubContentFacet content() {
    return getRepository().facet(PubContentFacet.class);
  }

  private static String assetPath(final Context context) {
    TokenMatcher.State state = context.getAttributes().require(TokenMatcher.State.class);
    String path = state.getTokens().get(PubRecipeSupport.PATH_NAME);
    if (path == null || path.isEmpty()) {
      return "/";
    }
    return path.startsWith("/") ? path : "/" + path;
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
}
