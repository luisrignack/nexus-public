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
package org.sonatype.nexus.content.helm.internal.recipe;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import javax.annotation.Nonnull;

import org.sonatype.nexus.content.helm.HelmContentFacet;
import org.sonatype.nexus.repository.cache.CacheController;
import org.sonatype.nexus.repository.content.facet.ContentProxyFacetSupport;
import org.sonatype.nexus.repository.helm.HelmCoordinatesHelper;
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

import static org.sonatype.nexus.repository.view.ContentTypes.TEXT_PLAIN;

/**
 * Proxy facet for Helm charts.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class HelmProxyFacet
    extends ContentProxyFacetSupport
{
  @Override
  protected Content getCachedContent(final Context context) throws IOException {
    return content().get(assetPath(context)).orElse(null);
  }

  @Override
  protected Content store(final Context context, final Content content) throws IOException {
    String path = assetPath(context);

    if (HelmCoordinatesHelper.isIndex(path) || "/".equals(path)) {
      byte[] rewritten = rewriteIndex(path, getUrl(context), content);
      Content transformed = new Content(content, new BytesPayload(rewritten, "text/yaml"));
      return content().put("/index.yaml", transformed);
    }

    return content().put(path, content);
  }

  @Override
  protected String getUrl(@Nonnull final Context context) {
    String path = HelmCoordinatesHelper.stripLeadingSlash(assetPath(context));
    String remote = HelmCoordinatesHelper.stripTrailingSlash(getRemoteUrl().toString());
    if (path.isEmpty()) {
      return remote + "/index.yaml";
    }
    if (HelmCoordinatesHelper.isIndex("/" + path)) {
      return remote + "/index.yaml";
    }
    return remote + "/" + path;
  }

  @Override
  protected HttpRequestBase buildFetchHttpRequest(
      final java.net.URI uri,
      final Context context,
      final Content stale)
  {
    HttpRequestBase request = super.buildFetchHttpRequest(uri, context, stale);
    request.setHeader(HttpHeaders.ACCEPT_ENCODING, "identity");
    return request;
  }

  @Nonnull
  @Override
  protected CacheController getCacheController(@Nonnull final Context context) {
    String path = assetPath(context);
    if (HelmCoordinatesHelper.isIndex(path)) {
      return cacheControllerHolder.getMetadataCacheController();
    }
    return cacheControllerHolder.getContentCacheController();
  }

  private byte[] rewriteIndex(final String path, final String url, final Content content) throws IOException {
    // Keep index as-is; Helm clients can download chart URLs directly
    try (InputStream input = content.openInputStream()) {
      return ByteStreams.toByteArray(input);
    }
  }

  private HelmContentFacet content() {
    return getRepository().facet(HelmContentFacet.class);
  }

  private String assetPath(final Context context) {
    TokenMatcher.State state = context.getAttributes().require(TokenMatcher.State.class);
    String path = state.getTokens().get(HelmRecipeSupport.PATH_NAME);
    String decoded = HelmCoordinatesHelper.decode(path);
    if (decoded == null || decoded.isEmpty()) {
      return "/";
    }
    return decoded.startsWith("/") ? decoded : "/" + decoded;
  }
}
