/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype Nexus" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.content.nuget.internal.recipe;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import jakarta.inject.Singleton;

import org.sonatype.nexus.content.nuget.NugetContentFacet;
import org.sonatype.nexus.content.nuget.NugetPublishResult;
import org.sonatype.nexus.repository.InvalidContentException;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.nuget.NugetCoordinatesHelper;
import org.sonatype.nexus.repository.nuget.NugetIndexBuilder;
import org.sonatype.nexus.repository.nuget.NugetServiceIndex;
import org.sonatype.nexus.repository.nuget.NugetPackage;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.Context;
import org.sonatype.nexus.repository.view.Handler;
import org.sonatype.nexus.repository.view.Request;
import org.sonatype.nexus.repository.view.Response;
import org.sonatype.nexus.repository.view.matchers.token.TokenMatcher;
import org.sonatype.nexus.repository.view.payloads.BytesPayload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

/**
 * Serves the NuGet v3 (and v2 publish) endpoints of hosted repositories.
 *
 * <p>Every document generated here points back at this repository, so clients keep talking to it for the
 * following requests.</p>
 */
@Component
@Singleton
public class NugetContentHandler
    implements Handler
{
  private static final Logger log = LoggerFactory.getLogger(NugetContentHandler.class);

  @Nonnull
  @Override
  public Response handle(@Nonnull final Context context) throws Exception {
    String path = contentPath(context);
    String method = context.getRequest().getAction();

    switch (method) {
      case HEAD:
      case GET:
        return get(context, path);
      case PUT:
        return put(context, path);
      case DELETE:
        return delete(context, path);
      default:
        return methodNotAllowed(method, GET, HEAD, PUT, DELETE);
    }
  }

  private Response get(final Context context, final String path) throws IOException {
    Repository repository = context.getRepository();
    NugetContentFacet storage = repository.facet(NugetContentFacet.class);
    String base = repository.getUrl();

    if (path.isEmpty() || NugetCoordinatesHelper.INDEX_JSON.equals(path)) {
      return json(NugetServiceIndex.build(base));
    }

    if (path.startsWith(NugetCoordinatesHelper.FLAT_CONTAINER + "/")) {
      return flatContainer(storage, path);
    }

    if (path.startsWith(NugetCoordinatesHelper.REGISTRATION + "/")) {
      return registration(storage, base, path);
    }

    if (NugetCoordinatesHelper.QUERY.equals(path)) {
      return query(context, storage, base);
    }

    // v2 download alias: api/v2/package/{id}/{version}
    if (path.startsWith(NugetCoordinatesHelper.API_V2_PACKAGE + "/")) {
      String coordinates = path.substring(NugetCoordinatesHelper.API_V2_PACKAGE.length() + 1);
      String[] parts = coordinates.split("/");
      if (parts.length == 2) {
        return packageFile(storage, parts[0], parts[1], NugetCoordinatesHelper.NUPKG_EXTENSION);
      }
      return notFound(path);
    }

    return storage.get(NugetCoordinatesHelper.normalizePath(path))
        .map(content -> ok(content))
        .orElseGet(() -> notFound(path));
  }

  private Response flatContainer(final NugetContentFacet storage, final String path) throws IOException {
    String rest = path.substring(NugetCoordinatesHelper.FLAT_CONTAINER.length() + 1);
    String[] parts = rest.split("/");

    if (parts.length == 2 && NugetCoordinatesHelper.INDEX_JSON.equals(parts[1])) {
      List<String> versions = storage.versions(parts[0]);
      if (versions.isEmpty()) {
        return notFound(path);
      }
      return json(NugetIndexBuilder.versionsIndex(versions));
    }

    if (parts.length == 3) {
      String id = parts[0];
      String version = parts[1];
      String file = parts[2];
      if (file.toLowerCase(Locale.ROOT).endsWith(NugetCoordinatesHelper.NUPKG_EXTENSION)) {
        return packageFile(storage, id, version, NugetCoordinatesHelper.NUPKG_EXTENSION);
      }
      if (file.toLowerCase(Locale.ROOT).endsWith(NugetCoordinatesHelper.NUSPEC_EXTENSION)) {
        return packageFile(storage, id, version, NugetCoordinatesHelper.NUSPEC_EXTENSION);
      }
    }

    return notFound(path);
  }

  private Response packageFile(
      final NugetContentFacet storage,
      final String id,
      final String version,
      final String extension) throws IOException
  {
    String path = NugetCoordinatesHelper.NUPKG_EXTENSION.equals(extension)
        ? NugetCoordinatesHelper.nupkgPath(id, version)
        : NugetCoordinatesHelper.nuspecPath(id, version);

    return storage.get(path)
        .map(content -> ok(content))
        .orElseGet(() -> notFound(path));
  }

  private Response registration(final NugetContentFacet storage, final String base, final String path)
      throws IOException
  {
    String rest = path.substring(NugetCoordinatesHelper.REGISTRATION.length() + 1);
    String[] parts = rest.split("/");
    if (parts.length == 0 || parts[0].isEmpty()) {
      return notFound(path);
    }
    if (parts.length > 1 && !NugetCoordinatesHelper.INDEX_JSON.equals(parts[1])) {
      return notFound(path);
    }

    String id = parts[0];
    List<NugetPackage> packages = storage.packages().stream()
        .filter(nugetPackage -> id.equalsIgnoreCase(nugetPackage.getId()))
        .collect(Collectors.toList());
    if (packages.isEmpty()) {
      return notFound(path);
    }
    return json(NugetIndexBuilder.registrationIndex(base, id, packages));
  }

  private Response query(final Context context, final NugetContentFacet storage, final String base)
      throws IOException
  {
    Request request = context.getRequest();
    String term = request.getParameters().get("q");
    int skip = integer(request.getParameters().get("skip"), 0);
    int take = request.getParameters().contains("take")
        ? integer(request.getParameters().get("take"), NugetIndexBuilder.DEFAULT_TAKE)
        : -1;
    return json(NugetIndexBuilder.searchResults(base, storage.packages(), term, skip, take));
  }

  private Response put(final Context context, final String path) throws IOException {
    if (!path.startsWith(NugetCoordinatesHelper.API_V2_PACKAGE)) {
      return badRequest("Unsupported upload path: " + path);
    }

    Request request = context.getRequest();
    if (request.getPayload() == null) {
      return badRequest("Missing .nupkg payload");
    }

    NugetContentFacet storage = context.getRepository().facet(NugetContentFacet.class);
    try {
      NugetPublishResult result = storage.publish(request.getPayload());
      log.debug("Published {}/{} to {}", result.getId(), result.getVersion(), result.getNupkgPath());
      return created();
    }
    catch (InvalidContentException e) {
      return badRequest(e.getMessage());
    }
  }

  private Response delete(final Context context, final String path) throws IOException {
    NugetContentFacet storage = context.getRepository().facet(NugetContentFacet.class);
    boolean deleted = storage.delete(NugetCoordinatesHelper.normalizePath(path));
    return deleted ? noContent() : notFound(path);
  }

  private static Response json(final byte[] body) {
    return ok(new BytesPayload(body, APPLICATION_JSON));
  }

  private static int integer(final String value, final int defaultValue) {
    if (value == null || value.isEmpty()) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(value.trim());
    }
    catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  private static String contentPath(final Context context) {
    TokenMatcher.State state = context.getAttributes().get(TokenMatcher.State.class);
    String path = state != null
        ? state.getTokens().get(NugetRecipeSupport.PATH_NAME)
        : context.getRequest().getPath();
    if (path == null) {
      path = context.getRequest().getPath();
    }
    return NugetCoordinatesHelper.stripLeadingSlash(NugetCoordinatesHelper.decode(path));
  }
}
