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
package org.sonatype.nexus.content.go.internal.recipe;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import javax.annotation.Nonnull;

import org.sonatype.nexus.content.go.GoContentFacet;
import org.sonatype.nexus.repository.cache.CacheController;
import org.sonatype.nexus.repository.content.facet.ContentProxyFacetSupport;
import org.sonatype.nexus.repository.go.GoCoordinatesHelper;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.Context;
import org.sonatype.nexus.repository.view.matchers.token.TokenMatcher;

import com.google.common.io.ByteStreams;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.HttpRequestBase;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import static org.sonatype.nexus.repository.view.ContentTypes.APPLICATION_JSON;
import static org.sonatype.nexus.repository.view.ContentTypes.TEXT_PLAIN;

/**
 * Proxy facet for Go modules.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class GoProxyFacet
    extends ContentProxyFacetSupport
{
  @Override
  protected Content getCachedContent(final Context context) throws IOException {
    return content().get(assetPath(context)).orElse(null);
  }

  @Override
  protected Content store(final Context context, final Content content) throws IOException {
    String path = assetPath(context);
    Optional<GoCoordinatesHelper.Coordinates> coordinates = GoCoordinatesHelper.parse(path);
    if (coordinates.isPresent() && coordinates.get().isList()) {
      // cache the raw list as-is
    }
    return content().put(path, content);
  }

  @Override
  protected String getUrl(@Nonnull final Context context) {
    String path = GoCoordinatesHelper.stripLeadingSlash(assetPath(context));
    String remote = GoCoordinatesHelper.stripTrailingSlash(getRemoteUrl().toString());
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
    Optional<GoCoordinatesHelper.Coordinates> coordinates = GoCoordinatesHelper.parse(path);
    if (coordinates.isPresent() && (coordinates.get().isList() || coordinates.get().isInfo() || coordinates.get().isLatest())) {
      return cacheControllerHolder.getMetadataCacheController();
    }
    return cacheControllerHolder.getContentCacheController();
  }

  private GoContentFacet content() {
    return getRepository().facet(GoContentFacet.class);
  }

  private String assetPath(final Context context) {
    TokenMatcher.State state = context.getAttributes().require(TokenMatcher.State.class);
    String path = state.getTokens().get(GoRecipeSupport.PATH_NAME);
    String decoded = GoCoordinatesHelper.decode(path);
    String normalized = GoCoordinatesHelper.stripTrailingSlash(decoded == null ? "" : decoded);
    if (normalized.isEmpty()) {
      return "/";
    }
    return decoded != null && decoded.startsWith("/") ? decoded : "/" + (decoded == null ? "" : decoded);
  }
}
