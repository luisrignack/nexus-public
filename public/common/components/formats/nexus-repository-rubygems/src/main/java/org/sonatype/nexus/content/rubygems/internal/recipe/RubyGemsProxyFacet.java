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
package org.sonatype.nexus.content.rubygems.internal.recipe;

import java.io.IOException;

import javax.annotation.Nonnull;

import org.sonatype.nexus.content.rubygems.RubyGemsContentFacet;
import org.sonatype.nexus.repository.cache.CacheController;
import org.sonatype.nexus.repository.content.facet.ContentProxyFacetSupport;
import org.sonatype.nexus.repository.rubygems.RubyGemsCoordinatesHelper;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.Context;
import org.sonatype.nexus.repository.view.matchers.token.TokenMatcher;

import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.HttpRequestBase;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Proxy facet for RubyGems.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class RubyGemsProxyFacet
    extends ContentProxyFacetSupport
{
  @Override
  protected Content getCachedContent(final Context context) throws IOException {
    return content().get(assetPath(context)).orElse(null);
  }

  @Override
  protected Content store(final Context context, final Content content) throws IOException {
    String path = assetPath(context);
    return content().put(path, content);
  }

  @Override
  protected String getUrl(@Nonnull final Context context) {
    String path = RubyGemsCoordinatesHelper.stripLeadingSlash(assetPath(context));
    String remote = RubyGemsCoordinatesHelper.stripTrailingSlash(getRemoteUrl().toString());
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
    if (path.contains(".gz") || path.contains("specs") || path.contains("dependencies")) {
      return cacheControllerHolder.getMetadataCacheController();
    }
    return cacheControllerHolder.getContentCacheController();
  }

  private RubyGemsContentFacet content() {
    return getRepository().facet(RubyGemsContentFacet.class);
  }

  private String assetPath(final Context context) {
    TokenMatcher.State state = context.getAttributes().require(TokenMatcher.State.class);
    String path = state.getTokens().get(RubyGemsRecipeSupport.PATH_NAME);
    String decoded = RubyGemsCoordinatesHelper.decode(path);
    if (decoded == null || decoded.isEmpty()) {
      return "/";
    }
    return decoded.startsWith("/") ? decoded : "/" + decoded;
  }
}
