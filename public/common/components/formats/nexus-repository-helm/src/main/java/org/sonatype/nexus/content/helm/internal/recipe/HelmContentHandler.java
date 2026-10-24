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
import java.util.Optional;

import javax.annotation.Nonnull;
import jakarta.inject.Singleton;

import org.sonatype.nexus.content.helm.HelmContentFacet;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.helm.HelmCoordinatesHelper;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.Context;
import org.sonatype.nexus.repository.view.Handler;
import org.sonatype.nexus.repository.view.Request;
import org.sonatype.nexus.repository.view.Response;
import org.sonatype.nexus.repository.view.matchers.token.TokenMatcher;
import org.sonatype.nexus.repository.view.payloads.BytesPayload;

import org.springframework.stereotype.Component;

import static org.sonatype.nexus.repository.http.HttpMethods.DELETE;
import static org.sonatype.nexus.repository.http.HttpMethods.GET;
import static org.sonatype.nexus.repository.http.HttpMethods.HEAD;
import static org.sonatype.nexus.repository.http.HttpMethods.POST;
import static org.sonatype.nexus.repository.http.HttpMethods.PUT;
import static org.sonatype.nexus.repository.http.HttpResponses.badRequest;
import static org.sonatype.nexus.repository.http.HttpResponses.created;
import static org.sonatype.nexus.repository.http.HttpResponses.methodNotAllowed;
import static org.sonatype.nexus.repository.http.HttpResponses.noContent;
import static org.sonatype.nexus.repository.http.HttpResponses.notFound;
import static org.sonatype.nexus.repository.http.HttpResponses.ok;
import static org.sonatype.nexus.repository.view.ContentTypes.TEXT_PLAIN;

/**
 * Handles Helm chart uploads and index requests for hosted repositories.
 */
@Component
@Singleton
public class HelmContentHandler
    implements Handler
{
  @Nonnull
  @Override
  public Response handle(@Nonnull final Context context) throws Exception {
    String path = contentPath(context);
    String method = context.getRequest().getAction();

    Repository repository = context.getRepository();
    HelmContentFacet storage = repository.facet(HelmContentFacet.class);

    switch (method) {
      case HEAD:
      case GET:
        return get(storage, path);
      case POST:
      case PUT:
        return put(context, storage, path);
      case DELETE:
        return delete(storage, path);
      default:
        return methodNotAllowed(method, GET, HEAD, POST, PUT, DELETE);
    }
  }

  private Response get(final HelmContentFacet storage, final String path) throws IOException {
    if (HelmCoordinatesHelper.isIndex(path) || "/".equals(path)) {
      Optional<Content> cached = storage.get("/index.yaml");
      if (cached.isPresent()) {
        return ok(cached.get());
      }
      byte[] body = HelmIndexBuilder.buildIndex(storage);
      storage.put("/index.yaml", new BytesPayload(body, TEXT_PLAIN));
      return ok(new BytesPayload(body, "text/yaml"));
    }

    return storage.get(path)
        .map(content -> ok(content))
        .orElseGet(() -> notFound(path));
  }

  private Response put(final Context context, final HelmContentFacet storage, final String path) throws IOException {
    Request request = context.getRequest();
    if (request.getPayload() == null) {
      return badRequest("Missing payload");
    }

    String fileName = HelmCoordinatesHelper.fileName(path);
    Optional<String> version = HelmCoordinatesHelper.versionFromFileName(fileName);
    String chartName = HelmCoordinatesHelper.chartNameFromFileName(fileName);
    if (version.isPresent() && !chartName.isEmpty()) {
      storage.put(path, chartName, version.get(), request.getPayload());
    }
    else {
      storage.put(path, request.getPayload());
    }

    // regenerate index
    byte[] body = HelmIndexBuilder.buildIndex(storage);
    try {
      storage.put("/index.yaml", new BytesPayload(body, TEXT_PLAIN));
    }
    catch (Exception e) {
      // ignore index generation failures
    }

    return created();
  }

  private Response delete(final HelmContentFacet storage, final String path) throws IOException {
    if (HelmCoordinatesHelper.isIndex(path)) {
      return noContent();
    }
    boolean deleted = storage.delete(path);
    try {
      byte[] body = HelmIndexBuilder.buildIndex(storage);
      storage.put("/index.yaml", new BytesPayload(body, TEXT_PLAIN));
    }
    catch (Exception e) {
      // ignore
    }
    return deleted ? noContent() : notFound(path);
  }

  @Nonnull
  private String contentPath(final Context context) {
    TokenMatcher.State state = context.getAttributes().require(TokenMatcher.State.class);
    String path = state.getTokens().get(HelmRecipeSupport.PATH_NAME);
    String decoded = HelmCoordinatesHelper.decode(path);
    if (decoded == null || decoded.isEmpty()) {
      return "/";
    }
    return decoded.startsWith("/") ? decoded : "/" + decoded;
  }
}
