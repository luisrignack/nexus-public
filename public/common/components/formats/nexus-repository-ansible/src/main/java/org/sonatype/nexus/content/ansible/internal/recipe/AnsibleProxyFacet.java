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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.annotation.Nonnull;

import org.sonatype.nexus.content.ansible.AnsibleContentFacet;
import org.sonatype.nexus.repository.ansible.AnsibleCoordinatesHelper;
import org.sonatype.nexus.repository.cache.CacheController;
import org.sonatype.nexus.repository.content.facet.ContentProxyFacetSupport;
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
 * Proxy facet for Ansible Galaxy repositories.
 *
 * <p>Bodies are cached as usual; JSON documents (API discovery, collection lists, versions) are
 * rewritten on the way in so both absolute and root-relative URLs point at this repository rather
 * than at the upstream Galaxy. Tarballs and unknown paths keep their original bytes.</p>
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class AnsibleProxyFacet
    extends ContentProxyFacetSupport
{
  @Override
  protected Content getCachedContent(final Context context) throws IOException {
    // Galaxy paginates with query parameters (limit/offset); every variant is a different
    // document, so query-bearing requests must not be answered from the path-keyed cache.
    if (hasQuery(context)) {
      return null;
    }
    return content().get(assetPath(context)).orElse(null);
  }

  @Override
  protected Content store(final Context context, final Content content) throws IOException {
    String path = assetPath(context);
    String remote = getRemoteUrl().toString();
    boolean cacheable = !hasQuery(context);

    if (isJson(path)) {
      byte[] body = rewrite(read(content), remote, getRepository().getUrl())
          .getBytes(StandardCharsets.UTF_8);
      Content rewritten = new Content(content, new BytesPayload(body, APPLICATION_JSON));
      return cacheable ? content().put(path, rewritten) : rewritten;
    }
    return cacheable ? content().put(path, content) : content;
  }

  @Override
  protected String getUrl(@Nonnull final Context context) {
    String path = AnsibleCoordinatesHelper.stripLeadingSlash(assetPath(context));
    String remote = AnsibleCoordinatesHelper.stripTrailingSlash(getRemoteUrl().toString());
    String url = path.isEmpty() ? remote + "/api/v3/" : remote + "/" + path;
    return appendQuery(url, context.getRequest().getParameters());
  }

  private static boolean hasQuery(final Context context) {
    Parameters parameters = context.getRequest().getParameters();
    return parameters != null && !parameters.isEmpty();
  }

  private static String appendQuery(final String url, final Parameters parameters) {
    if (parameters == null || parameters.isEmpty()) {
      return url;
    }
    StringBuilder query = new StringBuilder();
    for (Map.Entry<String, String> entry : parameters.entries()) {
      if (query.length() > 0) {
        query.append('&');
      }
      query.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
          .append('=')
          .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
    }
    return url + (url.contains("?") ? "&" : "?") + query;
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

  static String rewrite(final String body, final String remote, final String local) {
    if (body == null || body.isEmpty()) {
      return body;
    }
    String from = AnsibleCoordinatesHelper.stripTrailingSlash(remote);
    String to = AnsibleCoordinatesHelper.stripTrailingSlash(local);
    if (from.isEmpty() || to.isEmpty() || from.equals(to)) {
      return body;
    }
    // rewrite both plain and JSON-escaped variants of the upstream base URL
    String rewritten = body.replace(from, to).replace(from.replace("/", "\\/"), to.replace("/", "\\/"));
    // the public Galaxy API also answers with root-relative paths (/api/...). They must be
    // anchored at this repository, otherwise clients resolve them against the Nexus host root.
    String api = AnsibleCoordinatesHelper.API + "/";
    rewritten = rewritten.replace("\"" + api, "\"" + to + api);
    String escapedApi = api.replace("/", "\\/");
    String escapedTo = to.replace("/", "\\/");
    return rewritten.replace("\"" + escapedApi, "\"" + escapedTo + escapedApi);
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
