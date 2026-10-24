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
import java.util.Optional;

import javax.annotation.Nonnull;
import jakarta.inject.Singleton;

import org.sonatype.nexus.content.go.GoContentFacet;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.go.GoCoordinatesHelper;
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
import static org.sonatype.nexus.repository.http.HttpMethods.PUT;
import static org.sonatype.nexus.repository.http.HttpResponses.badRequest;
import static org.sonatype.nexus.repository.http.HttpResponses.created;
import static org.sonatype.nexus.repository.http.HttpResponses.methodNotAllowed;
import static org.sonatype.nexus.repository.http.HttpResponses.noContent;
import static org.sonatype.nexus.repository.http.HttpResponses.notFound;
import static org.sonatype.nexus.repository.http.HttpResponses.ok;
import static org.sonatype.nexus.repository.view.ContentTypes.APPLICATION_JSON;
import static org.sonatype.nexus.repository.view.ContentTypes.APPLICATION_OCTET_STREAM;
import static org.sonatype.nexus.repository.view.ContentTypes.APPLICATION_ZIP;
import static org.sonatype.nexus.repository.view.ContentTypes.TEXT_PLAIN;

/**
 * Handles the Go module proxy protocol for hosted repositories.
 */
@Component
@Singleton
public class GoContentHandler
    implements Handler
{
  @Nonnull
  @Override
  public Response handle(@Nonnull final Context context) throws Exception {
    String path = contentPath(context);
    String method = context.getRequest().getAction();

    Repository repository = context.getRepository();
    GoContentFacet storage = repository.facet(GoContentFacet.class);

    Optional<GoCoordinatesHelper.Coordinates> coordinates = GoCoordinatesHelper.parse(path);
    if (!coordinates.isPresent()) {
      if (PUT.equals(method)) {
        return putRaw(context, storage, path);
      }
      return badRequest("Invalid Go module proxy path");
    }

    GoCoordinatesHelper.Coordinates coords = coordinates.get();

    switch (method) {
      case HEAD:
      case GET:
        return get(storage, coords, path);
      case PUT:
        return put(context, storage, coords, path);
      case DELETE:
        return delete(storage, coords);
      default:
        return methodNotAllowed(method, GET, HEAD, PUT, DELETE);
    }
  }

  private Response get(
      final GoContentFacet storage,
      final GoCoordinatesHelper.Coordinates coords,
      final String path) throws IOException
  {
    if (coords.isList()) {
      Optional<Content> cached = storage.get(path);
      if (cached.isPresent()) {
        return ok(cached.get());
      }
      byte[] body = GoIndexBuilder.buildVersionList(storage, coords.getModule());
      return ok(new BytesPayload(body, TEXT_PLAIN));
    }

    if (coords.isLatest()) {
      return notFound(path);
    }

    Optional<Content> asset = storage.get(path);
    if (asset.isPresent()) {
      return ok(asset.get());
    }

    if (coords.isInfo()) {
      return ok(new BytesPayload(GoIndexBuilder.buildInfo(coords.getModule(), coords.getVersion()), APPLICATION_JSON));
    }

    if (coords.isMod()) {
      return ok(new BytesPayload(GoIndexBuilder.buildMod(coords.getModule()), TEXT_PLAIN));
    }

    return notFound(path);
  }

  private Response put(
      final Context context,
      final GoContentFacet storage,
      final GoCoordinatesHelper.Coordinates coords,
      final String path) throws IOException
  {
    Request request = context.getRequest();
    if (request.getPayload() == null) {
      return badRequest("Missing payload");
    }

    storage.put(path, coords.getModule(), coords.getVersion(), request.getPayload());
    return created();
  }

  private Response putRaw(final Context context, final GoContentFacet storage, final String path) throws IOException {
    Request request = context.getRequest();
    if (request.getPayload() == null) {
      return badRequest("Missing payload");
    }
    storage.put(path, request.getPayload());
    return created();
  }

  private Response delete(
      final GoContentFacet storage,
      final GoCoordinatesHelper.Coordinates coords) throws IOException
  {
    if (coords.isList()) {
      return noContent();
    }
    if (coords.getVersion() != null) {
      String version = coords.getVersion();
      java.util.List<String> paths = java.util.Arrays.asList(
          modulePath(coords.getModule(), version, "info"),
          modulePath(coords.getModule(), version, "mod"),
          modulePath(coords.getModule(), version, "zip"));
      boolean deleted = false;
      for (String p : paths) {
        if (storage.delete(p)) {
          deleted = true;
        }
      }
      return deleted ? noContent() : notFound(coords.getModule() + "@" + version);
    }
    return notFound(coords.getModule());
  }

  private static String modulePath(final String module, final String version, final String extension) {
    return "/" + module + "/@v/" + version + "." + extension;
  }

  @Nonnull
  private String contentPath(final Context context) {
    TokenMatcher.State state = context.getAttributes().require(TokenMatcher.State.class);
    String path = state.getTokens().get(GoRecipeSupport.PATH_NAME);
    String decoded = GoCoordinatesHelper.decode(path);
    return decoded != null && decoded.startsWith("/") ? decoded : "/" + (decoded == null ? "" : decoded);
  }
}
