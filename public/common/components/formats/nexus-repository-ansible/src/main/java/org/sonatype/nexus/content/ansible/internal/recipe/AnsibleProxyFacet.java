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
package org.sonatype.nexus.content.ansible.internal.recipe;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import javax.annotation.Nonnull;

import org.sonatype.nexus.content.ansible.AnsibleContentFacet;
import org.sonatype.nexus.repository.ansible.AnsibleCoordinatesHelper;
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

import static org.sonatype.nexus.repository.view.ContentTypes.APPLICATION_JSON;

/**
 * Proxy facet for Ansible Galaxy repositories.
 *
 * <p>Bodies are cached as usual; JSON documents (API root, collection lists, versions, downloads)
 * are rewritten on the way in so every absolute URL points at this repository rather than at the
 * upstream Galaxy.</p>
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class AnsibleProxyFacet
    extends ContentProxyFacetSupport
{
  @Override
  protected Content getCachedContent(final Context context) throws IOException {
    return content().get(assetPath(context)).orElse(null);
  }

  @Override
  protected Content store(final Context context, final Content content) throws IOException {
    String path = assetPath(context);
    String remote = getRemoteUrl().toString();

    if (isJson(path)) {
      byte[] body = rewrite(read(content), remote, getRepository().getUrl())
          .getBytes(java.nio.charset.StandardCharsets.UTF_8);
      return content().put(path, new Content(content, new BytesPayload(body, APPLICATION_JSON)));
    }
    return content().put(path, content);
  }

  @Override
  protected String getUrl(@Nonnull final Context context) {
    String path = AnsibleCoordinatesHelper.stripLeadingSlash(assetPath(context));
    String remote = AnsibleCoordinatesHelper.stripTrailingSlash(getRemoteUrl().toString());
    if (path.isEmpty()) {
      return remote + "/api/v3/";
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
    // ask for JSON only when the path really addresses a document, otherwise a Galaxy
    // upstream may reject a tarball download with 406
    request.setHeader(HttpHeaders.ACCEPT, isJson(assetPath(context)) ? APPLICATION_JSON : "*/*");
    request.setHeader(HttpHeaders.ACCEPT_ENCODING, "identity");
    request.setHeader(HttpHeaders.USER_AGENT, "nexus-ansible-proxy/1.0");
    return request;
  }

  @Nonnull
  @Override
  protected CacheController getCacheController(@Nonnull final Context context) {
    String path = assetPath(context);
    if (isJson(path)) {
      return cacheControllerHolder.getMetadataCacheController();
    }
    return cacheControllerHolder.getContentCacheController();
  }

  /**
   * A path is treated as metadata (and therefore as JSON) when it addresses a Galaxy API
   * <em>document</em>: tarball downloads are content and must keep their bytes untouched.
   */
  static boolean isJson(final String path) {
    return AnsibleCoordinatesHelper.parse(path).isDocument();
  }

  private static String rewrite(final String body, final String remote, final String local) {
    if (body == null || body.isEmpty()) {
      return body;
    }
    String from = AnsibleCoordinatesHelper.stripTrailingSlash(remote);
    String to = AnsibleCoordinatesHelper.stripTrailingSlash(local);
    if (from.isEmpty() || to.isEmpty() || from.equals(to)) {
      return body;
    }
    // rewrite both plain and JSON-escaped variants of the upstream base URL
    return body.replace(from, to).replace(from.replace("/", "\\/"), to.replace("/", "\\/"));
  }

  private static String read(final Content content) throws IOException {
    try (InputStream input = content.openInputStream()) {
      return new String(ByteStreams.toByteArray(input), StandardCharsets.UTF_8);
    }
  }

  private AnsibleContentFacet content() {
    return getRepository().facet(AnsibleContentFacet.class);
  }

  private String assetPath(final Context context) {
    TokenMatcher.State state = context.getAttributes().require(TokenMatcher.State.class);
    String path = state.getTokens().get(AnsibleRecipeSupport.PATH_NAME);
    String decoded = AnsibleCoordinatesHelper.decode(path);
    if (decoded == null || decoded.isEmpty()) {
      return "/";
    }
    return decoded.startsWith("/") ? decoded : "/" + decoded;
  }
}
