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
package org.sonatype.nexus.content.pypi.internal.recipe;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import javax.annotation.Nonnull;

import org.sonatype.nexus.content.pypi.PyPiContentFacet;
import org.sonatype.nexus.repository.cache.CacheController;
import org.sonatype.nexus.repository.content.facet.ContentProxyFacetSupport;
import org.sonatype.nexus.repository.pypi.PyPiCoordinatesHelper;
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

import static org.sonatype.nexus.repository.view.ContentTypes.TEXT_HTML;

/**
 * Proxy facet for PyPI.
 *
 * <p>Index pages are fetched from the remote, their links rewritten to point at this repository and stored
 * locally. Package downloads are resolved through the base64url token embedded in the rewritten link, so the
 * real upstream URL is used even when the remote is configured at a nested path.</p>
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class PyPiProxyFacet
    extends ContentProxyFacetSupport
{
  private static final String SIMPLE = "simple";

  @Override
  protected Content getCachedContent(final Context context) throws IOException {
    return content().get(assetPath(context)).orElse(null);
  }

  @Override
  protected Content store(final Context context, final Content content) throws IOException {
    String path = assetPath(context);

    if (isIndex(path)) {
      byte[] rewritten = rewriteIndex(path, getUrl(context), content);
      Content transformed = new Content(content, new BytesPayload(rewritten, TEXT_HTML));
      return content().put(path, transformed);
    }

    return content().put(path, content);
  }

  @Override
  protected String getUrl(@Nonnull final Context context) {
    String path = PyPiCoordinatesHelper.stripLeadingSlash(assetPath(context));

    Optional<String> upstream = PyPiCoordinatesHelper.upstreamTokenFromPackagePath(path);
    if (upstream.isPresent()) {
      return PyPiCoordinatesHelper.decodeUpstreamUrl(upstream.get()).orElse(path);
    }

    String remote = PyPiCoordinatesHelper.stripTrailingSlash(getRemoteUrl().toString());
    if (SIMPLE.equals(path) || path.startsWith(SIMPLE + "/")) {
      if (remote.endsWith("/" + SIMPLE)) {
        return remote + path.substring(SIMPLE.length());
      }
    }
    return remote + "/" + path;
  }

  @Override
  protected HttpRequestBase buildFetchHttpRequest(
      final URI uri,
      final Context context,
      final Content stale)
  {
    HttpRequestBase request = super.buildFetchHttpRequest(uri, context, stale);
    // Keep index pages uncompressed so their links can be rewritten.
    if (isIndex(uri.getPath())) {
      request.setHeader(HttpHeaders.ACCEPT_ENCODING, "identity");
    }
    return request;
  }

  @Nonnull
  @Override
  protected CacheController getCacheController(@Nonnull final Context context) {
    return isIndex(assetPath(context))
        ? cacheControllerHolder.getMetadataCacheController()
        : cacheControllerHolder.getContentCacheController();
  }

  private byte[] rewriteIndex(final String path, final String url, final Content content) throws IOException {
    String html;
    try (InputStream input = content.openInputStream()) {
      html = new String(ByteStreams.toByteArray(input), StandardCharsets.UTF_8);
    }

    if (PyPiCoordinatesHelper.projectFromIndexPath(path).isPresent()) {
      URI page = URI.create(url);
      return PyPiIndexBuilder.rewriteProjectIndex(html, page);
    }
    return PyPiIndexBuilder.rewriteRootIndex(html);
  }

  private static boolean isIndex(final String path) {
    String normalized = PyPiCoordinatesHelper.stripTrailingSlash(
        PyPiCoordinatesHelper.stripLeadingSlash(path));
    return SIMPLE.equals(normalized)
        || normalized.startsWith(SIMPLE + "/");
  }

  private PyPiContentFacet content() {
    return getRepository().facet(PyPiContentFacet.class);
  }

  private String assetPath(final Context context) {
    TokenMatcher.State state = context.getAttributes().require(TokenMatcher.State.class);
    String path = state.getTokens().get(PyPiRecipeSupport.PATH_NAME);
    String decoded = PyPiCoordinatesHelper.decode(path);
    String normalized = PyPiCoordinatesHelper.stripTrailingSlash(decoded);
    if (normalized.isEmpty()) {
      return "/";
    }
    return decoded.startsWith("/") ? decoded : "/" + decoded;
  }
}
