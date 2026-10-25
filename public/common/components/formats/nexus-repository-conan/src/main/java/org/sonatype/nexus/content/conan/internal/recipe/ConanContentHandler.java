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
package org.sonatype.nexus.content.conan.internal.recipe;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.annotation.Nonnull;
import jakarta.inject.Singleton;

import org.sonatype.nexus.content.conan.ConanContentFacet;
import org.sonatype.nexus.repository.conan.ConanApiPath;
import org.sonatype.nexus.repository.conan.ConanCoordinates;
import org.sonatype.nexus.repository.content.fluent.FluentAsset;
import org.sonatype.nexus.repository.view.Context;
import org.sonatype.nexus.repository.view.Handler;
import org.sonatype.nexus.repository.view.Request;
import org.sonatype.nexus.repository.view.Response;
import org.sonatype.nexus.repository.view.matchers.token.TokenMatcher;
import org.sonatype.nexus.repository.view.payloads.BytesPayload;

import org.springframework.stereotype.Component;

import static org.sonatype.nexus.repository.http.HttpMethods.GET;
import static org.sonatype.nexus.repository.http.HttpMethods.HEAD;
import static org.sonatype.nexus.repository.http.HttpMethods.PUT;
import static org.sonatype.nexus.repository.http.HttpResponses.badRequest;
import static org.sonatype.nexus.repository.http.HttpResponses.created;
import static org.sonatype.nexus.repository.http.HttpResponses.methodNotAllowed;
import static org.sonatype.nexus.repository.http.HttpResponses.notFound;
import static org.sonatype.nexus.repository.http.HttpResponses.ok;
import static org.sonatype.nexus.repository.view.ContentTypes.APPLICATION_JSON;

/**
 * Serves the Conan HTTP API from a hosted repository: revision listings, file listings, single files (download and
 * upload) and the v1 {@code search} / {@code download_urls} documents.
 */
@Component
@Singleton
public class ConanContentHandler
    implements Handler
{
  private static final String EXPORTS = "exports/";

  @Nonnull
  @Override
  public Response handle(@Nonnull final Context context) throws Exception {
    String path = contentPath(context);
    ConanApiPath apiPath = ConanApiPath.parse(path);
    String method = context.getRequest().getAction();

    switch (method) {
      case HEAD:
      case GET:
        return get(context, apiPath, path);
      case PUT:
        return put(context, apiPath, path);
      default:
        return methodNotAllowed(method, GET, HEAD, PUT);
    }
  }

  private Response get(final Context context, final ConanApiPath apiPath, final String path) throws Exception {
    switch (apiPath.kind()) {
      case PING:
        return ok();
      case SEARCH:
        return search(context);
      case RECIPE_REVISIONS:
      case PACKAGE_REVISIONS:
        return revisions(context, apiPath, path);
      case RECIPE_FILES:
      case PACKAGE_FILES:
        return files(context, apiPath);
      case RECIPE_FILE:
      case PACKAGE_FILE:
        return file(context, apiPath, path);
      case V1_DOWNLOAD_URLS:
      case V1_PACKAGE_DOWNLOAD_URLS:
        return downloadUrls(context, apiPath);
      default:
        return notFound(path);
    }
  }

  private Response put(final Context context, final ConanApiPath apiPath, final String path) throws Exception {
    if (apiPath.kind() == ConanApiPath.Kind.UNKNOWN) {
      return badRequest("Unsupported Conan upload path: " + path);
    }
    if (!apiPath.isUploadable()) {
      return badRequest("Conan uploads require a file endpoint, got: " + path);
    }
    Request request = context.getRequest();
    if (request.getPayload() == null) {
      return badRequest("Missing payload");
    }

    ConanContentFacet storage = storage(context);
    ConanCoordinates coordinates = apiPath.coordinates();
    FluentAsset asset;
    if (apiPath.kind() == ConanApiPath.Kind.RECIPE_FILE) {
      asset = storage.putRecipeFile(coordinates, apiPath.revision(), apiPath.filename(), request.getPayload());
    }
    else {
      asset = storage.putPackageFile(
          coordinates,
          apiPath.revision(),
          apiPath.packageId(),
          apiPath.packageRevision(),
          apiPath.filename(),
          request.getPayload());
    }
    asset.created(OffsetDateTime.now());
    return created();
  }

  //
  // v1 search
  //

  private Response search(final Context context) throws IOException {
    String query = context.getRequest().getParameters().get("q");
    Set<String> references = new TreeSet<>();
    for (final FluentAsset asset : storage(context).assetsWithPrefix(ConanCoordinates.STORAGE_ROOT + "/")) {
      String reference = referenceOf(asset.path());
      if (reference == null) {
        continue;
      }
      String name = reference.substring(0, reference.indexOf('/'));
      if (ConanSearchMatcher.matches(query, reference, name)) {
        references.add(reference);
      }
    }
    return ok(json(ConanMetadataBuilder.searchResultsJson(references)));
  }

  //
  // revision listings
  //

  private Response revisions(final Context context, final ConanApiPath apiPath, final String path)
      throws IOException
  {
    String prefix = apiPath.contentPrefix();
    Map<String, OffsetDateTime> revisions = new LinkedHashMap<>();
    for (final FluentAsset asset : storage(context).assetsWithPrefix(prefix)) {
      String revision = firstSegment(asset.path(), prefix);
      if (revision.isEmpty()) {
        continue;
      }
      OffsetDateTime time = asset.lastUpdated();
      OffsetDateTime current = revisions.get(revision);
      if (current == null || (time != null && time.isAfter(current))) {
        revisions.put(revision, time);
      }
    }

    if (revisions.isEmpty()) {
      return notFound(path);
    }

    String contentUrl = context.getRepository().getUrl() + "/" + ConanCoordinates.normalize(path);
    return ok(json(ConanMetadataBuilder.revisionsJson(apiPath.coordinates().reference(), revisions, contentUrl)));
  }

  //
  // file listings
  //

  private Response files(final Context context, final ConanApiPath apiPath) throws IOException {
    String prefix = apiPath.contentPrefix();
    ConanContentFacet storage = storage(context);

    Map<String, Map<String, Object>> files = new LinkedHashMap<>();
    for (final FluentAsset asset : storage.assetsWithPrefix(prefix)) {
      String relative = asset.path().substring(prefix.length());
      if (relative.isEmpty()) {
        continue;
      }
      Map<String, Object> attributes = new LinkedHashMap<>();
      attributes.put("size", sizeOf(asset));
      String md5 = storage.md5(asset);
      if (md5 != null) {
        attributes.put("md5", md5);
      }
      files.put(relative, attributes);
    }

    if (files.isEmpty()) {
      return notFound(apiPath.contentPrefix());
    }

    return ok(json(ConanMetadataBuilder.filesJson(
        apiPath.coordinates().reference(),
        apiPath.revision(),
        files)));
  }

  //
  // single files
  //

  private Response file(final Context context, final ConanApiPath apiPath, final String path) throws IOException {
    return storage(context).get(apiPath.filePath())
        .map(content -> ok(content))
        .orElseGet(() -> notFound(path));
  }

  //
  // v1 download_urls
  //

  private Response downloadUrls(final Context context, final ConanApiPath apiPath) throws IOException {
    ConanContentFacet storage = storage(context);
    ConanCoordinates coordinates = apiPath.coordinates();

    String revision = latestRevision(storage, coordinates);
    if (revision == null) {
      return notFound();
    }

    boolean recipe = apiPath.kind() == ConanApiPath.Kind.V1_DOWNLOAD_URLS;
    String packageId = recipe ? null : apiPath.packageId();
    String packageRevision = null;
    String prefix;

    if (recipe) {
      prefix = coordinates.recipeRevisionPrefix(revision);
    }
    else {
      packageRevision = latestPackageRevision(storage, coordinates, revision, packageId);
      if (packageRevision == null) {
        return notFound();
      }
      prefix = coordinates.packageFilesPrefix(revision, packageId, packageRevision);
    }

    String repositoryUrl = context.getRepository().getUrl();
    Map<String, Map<String, Object>> files = new LinkedHashMap<>();
    for (final FluentAsset asset : storage.assetsWithPrefix(prefix)) {
      String relative = asset.path().substring(prefix.length());
      if (relative.isEmpty()) {
        continue;
      }
      String filename = recipe && relative.startsWith(EXPORTS) ? relative.substring(EXPORTS.length()) : relative;
      String apiUrl = recipe
          ? repositoryUrl + "/v2/" + coordinates.filesApiPath(revision) + "/" + filename
          : repositoryUrl + "/v2/" + coordinates.packageFilesApiPath(revision, packageId, packageRevision) + "/"
              + filename;

      OffsetDateTime time = asset.lastUpdated();
      Map<String, Object> value = new LinkedHashMap<>();
      value.put("lastModified", time == null ? 0L : time.toInstant().toEpochMilli());
      value.put("url", apiUrl);
      files.put(relative, value);
    }

    if (files.isEmpty()) {
      return notFound();
    }

    return ok(json(ConanMetadataBuilder.downloadUrlsJson(files)));
  }

  //
  // helpers
  //

  private String latestRevision(final ConanContentFacet storage, final ConanCoordinates coordinates) {
    return latestSegment(storage, coordinates.revisionsPrefix());
  }

  private String latestPackageRevision(
      final ConanContentFacet storage,
      final ConanCoordinates coordinates,
      final String revision,
      final String packageId)
  {
    return latestSegment(storage, coordinates.packageRevisionPrefix(revision, packageId));
  }

  /**
   * Returns the first segment below {@code prefix} of the most recently updated asset.
   */
  private String latestSegment(final ConanContentFacet storage, final String prefix) {
    String latest = null;
    OffsetDateTime latestTime = null;
    for (final FluentAsset asset : storage.assetsWithPrefix(prefix)) {
      String candidate = firstSegment(asset.path(), prefix);
      if (candidate.isEmpty()) {
        continue;
      }
      OffsetDateTime time = asset.lastUpdated();
      if (latest == null || latestTime == null || (time != null && time.isAfter(latestTime))) {
        latest = candidate;
        latestTime = time;
      }
    }
    return latest;
  }

  private static String firstSegment(final String path, final String prefix) {
    if (!path.startsWith(prefix)) {
      return "";
    }
    String relative = path.substring(prefix.length());
    int slash = relative.indexOf('/');
    return slash < 0 ? relative : relative.substring(0, slash);
  }

  private static String referenceOf(final String path) {
    List<String> segments = ConanCoordinates.segments(path);
    if (segments.size() < 5 || !ConanCoordinates.STORAGE_ROOT.equals(segments.get(0))) {
      return null;
    }
    return segments.get(1) + "/" + segments.get(2) + "@" + segments.get(3) + "/" + segments.get(4);
  }

  private static long sizeOf(final FluentAsset asset) {
    return asset.blob().map(blob -> blob.blobSize()).orElse(0L);
  }

  private static BytesPayload json(final String body) {
    return new BytesPayload(body.getBytes(StandardCharsets.UTF_8), APPLICATION_JSON);
  }

  private ConanContentFacet storage(final Context context) {
    return context.getRepository().facet(ConanContentFacet.class);
  }

  private static String contentPath(final Context context) {
    TokenMatcher.State state = context.getAttributes().get(TokenMatcher.State.class);
    String path = state != null ? state.getTokens().get(ConanRecipeSupport.PATH_NAME) : null;
    if (path == null) {
      path = context.getRequest().getPath();
    }
    return ConanCoordinates.normalize(path);
  }
}
