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

import java.io.InputStream;
import java.net.URI;
import java.util.Iterator;
import java.util.Map;

import org.sonatype.nexus.repository.http.HttpResponses;
import org.sonatype.nexus.repository.npm.internal.NpmCoordinatesHelper;
import org.sonatype.nexus.repository.proxy.ProxyHandler;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.Context;
import org.sonatype.nexus.repository.view.Payload;
import org.sonatype.nexus.repository.view.Response;
import org.sonatype.nexus.repository.view.matchers.token.TokenMatcher;
import org.sonatype.nexus.repository.view.payloads.BytesPayload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.io.ByteStreams;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import static org.sonatype.nexus.repository.view.ContentTypes.APPLICATION_JSON;

/**
 * Proxy handler that rewrites npm packument tarball URLs so clients download tarballs from the proxy
 * instead of the upstream registry.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class NpmProxyHandler
    extends ProxyHandler
{
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  protected Response buildPayloadResponse(final Context context, final Payload payload) {
    String path = NpmCoordinatesHelper.stripLeadingSlash(
        NpmCoordinatesHelper.decode(contentPath(context)));

    if (!(payload instanceof Content) || isTarball(path)) {
      return super.buildPayloadResponse(context, payload);
    }

    byte[] bytes;
    try (InputStream input = payload.openInputStream()) {
      bytes = ByteStreams.toByteArray(input);
    }
    catch (Exception e) {
      log.debug("Could not read npm metadata for {}", path, e);
      return super.buildPayloadResponse(context, payload);
    }

    try {
      JsonNode root = MAPPER.readTree(bytes);
      if (root instanceof ObjectNode) {
        rewriteTarballs((ObjectNode) root, context.getRepository().getUrl());
        bytes = MAPPER.writeValueAsBytes(root);
      }
    }
    catch (Exception e) {
      log.debug("Could not rewrite npm metadata for {}", path, e);
    }

    String contentType = payload.getContentType() != null ? payload.getContentType() : APPLICATION_JSON;
    Payload transformed = new BytesPayload(bytes, contentType);
    return HttpResponses.ok(new Content((Content) payload, transformed));
  }

  private static void rewriteTarballs(final ObjectNode packument, final String baseUrl) {
    JsonNode versions = packument.path("versions");
    if (!versions.isObject()) {
      return;
    }

    Iterator<Map.Entry<String, JsonNode>> entries = versions.fields();
    while (entries.hasNext()) {
      JsonNode version = entries.next().getValue();
      JsonNode dist = version.path("dist");
      if (!dist.isObject()) {
        continue;
      }
      JsonNode tarball = dist.get("tarball");
      if (tarball == null || !tarball.isTextual()) {
        continue;
      }
      try {
        URI uri = URI.create(tarball.asText());
        String tarballPath = uri.getPath();
        if (tarballPath != null && !tarballPath.isEmpty()) {
          ((ObjectNode) dist).put("tarball", baseUrl + (tarballPath.startsWith("/") ? "" : "/") + tarballPath);
        }
      }
      catch (IllegalArgumentException e) {
        // Leave malformed upstream URLs untouched.
      }
    }
  }

  private static boolean isTarball(final String path) {
    return path.contains("/-/") && (path.endsWith(".tgz") || path.endsWith(".tar.gz"));
  }

  private static String contentPath(final Context context) {
    TokenMatcher.State state = context.getAttributes().require(TokenMatcher.State.class);
    return state.getTokens().get(NpmRecipeSupport.PATH_NAME);
  }
}
