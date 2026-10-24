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
import java.util.Optional;

import javax.annotation.Nonnull;
import jakarta.inject.Singleton;

import org.sonatype.nexus.content.rubygems.RubyGemsContentFacet;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.rubygems.RubyGemsCoordinatesHelper;
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
import static org.sonatype.nexus.repository.view.ContentTypes.APPLICATION_OCTET_STREAM;

/**
 * Handles RubyGems uploads and downloads for hosted repositories.
 */
@Component
@Singleton
public class RubyGemsContentHandler
    implements Handler
{
  @Nonnull
  @Override
  public Response handle(@Nonnull final Context context) throws Exception {
    String path = contentPath(context);
    String method = context.getRequest().getAction();

    Repository repository = context.getRepository();
    RubyGemsContentFacet storage = repository.facet(RubyGemsContentFacet.class);

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

  private Response get(final RubyGemsContentFacet storage, final String path) throws IOException {
    if (isSpecsIndex(path)) {
      Optional<Content> cached = storage.get(path);
      if (cached.isPresent()) {
        return ok(cached.get());
      }
      byte[] body = RubyGemsIndexBuilder.buildSpecsIndex(storage);
      storage.put(path, new BytesPayload(body, "application/x-gzip"));
      return ok(new BytesPayload(body, "application/x-gzip"));
    }

    if (isDependencyApi(path)) {
      Optional<Content> cached = storage.get(path);
      if (cached.isPresent()) {
        return ok(cached.get());
      }
      byte[] body = RubyGemsIndexBuilder.buildSpecsIndex(storage);
      storage.put(path, new BytesPayload(body, "application/octet-stream"));
      return ok(new BytesPayload(body, "application/octet-stream"));
    }

    return storage.get(path)
        .map(content -> ok(content))
        .orElseGet(() -> notFound(path));
  }

  private Response put(final Context context, final RubyGemsContentFacet storage, final String path) throws IOException {
    Request request = context.getRequest();
    if (request.getPayload() == null) {
      return badRequest("Missing payload");
    }

    if (path.equals(RubyGemsCoordinatesHelper.API_V1_GEMS)) {
      // For hosted, accept the push; store raw if possible
      storage.put(path, request.getPayload());
      regenerateIndexes(storage);
      return created();
    }

    String fileName = RubyGemsCoordinatesHelper.fileName(path);
    if (fileName.toLowerCase().endsWith(".gem")) {
      Optional<String> version = RubyGemsCoordinatesHelper.versionFromFileName(fileName);
      String gemName = RubyGemsCoordinatesHelper.gemNameFromFileName(fileName);
      if (version.isPresent() && !gemName.isEmpty()) {
        storage.put(path, gemName, version.get(), request.getPayload());
      }
      else {
        storage.put(path, request.getPayload());
      }
      regenerateIndexes(storage);
      return created();
    }

    storage.put(path, request.getPayload());
    return created();
  }

  private void regenerateIndexes(final RubyGemsContentFacet storage) {
    try {
      byte[] specs = RubyGemsIndexBuilder.buildSpecsIndex(storage);
      storage.put("/specs.4.8.gz", new BytesPayload(specs, "application/x-gzip"));
      storage.put("/latest_specs.4.8.gz", new BytesPayload(specs, "application/x-gzip"));
      storage.put("/prerelease_specs.4.8.gz", new BytesPayload(RubyGemsIndexBuilder.buildIndex(storage, false, true), "application/x-gzip"));
    }
    catch (Exception e) {
      // ignore
    }
  }

  private Response delete(final RubyGemsContentFacet storage, final String path) throws IOException {
    if (isSpecsIndex(path) || isDependencyApi(path)) {
      return noContent();
    }
    boolean deleted = storage.delete(path);
    if (deleted) {
      regenerateIndexes(storage);
    }
    return deleted ? noContent() : notFound(path);
  }

  private static boolean isSpecsIndex(final String path) {
    return "/specs.4.8.gz".equals(path)
        || "/latest_specs.4.8.gz".equals(path)
        || "/prerelease_specs.4.8.gz".equals(path)
        || "/Marshal.4.8.Z".equals(path);
  }

  private static boolean isDependencyApi(final String path) {
    return RubyGemsCoordinatesHelper.API_V1_DEPENDENCIES.equals(path)
        || path != null && path.startsWith(RubyGemsCoordinatesHelper.API_V1_DEPENDENCIES);
  }

  @Nonnull
  private String contentPath(final Context context) {
    TokenMatcher.State state = context.getAttributes().require(TokenMatcher.State.class);
    String path = state.getTokens().get(RubyGemsRecipeSupport.PATH_NAME);
    String decoded = RubyGemsCoordinatesHelper.decode(path);
    if (decoded == null || decoded.isEmpty()) {
      return "/";
    }
    return decoded.startsWith("/") ? decoded : "/" + decoded;
  }
}
