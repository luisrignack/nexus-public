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
package org.sonatype.nexus.content.pub.internal.recipe;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import javax.annotation.Nonnull;
import jakarta.inject.Singleton;

import org.sonatype.nexus.content.pub.PubContentFacet;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.pub.internal.PubCoordinatesHelper;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.Context;
import org.sonatype.nexus.repository.view.Handler;
import org.sonatype.nexus.repository.view.PartPayload;
import org.sonatype.nexus.repository.view.Payload;
import org.sonatype.nexus.repository.view.Request;
import org.sonatype.nexus.repository.view.Response;
import org.sonatype.nexus.repository.view.Status;
import org.sonatype.nexus.repository.view.matchers.token.TokenMatcher;
import org.sonatype.nexus.repository.view.payloads.BytesPayload;

import com.google.common.io.ByteStreams;
import org.apache.http.HttpHeaders;
import org.springframework.stereotype.Component;

import static org.sonatype.nexus.repository.http.HttpMethods.DELETE;
import static org.sonatype.nexus.repository.http.HttpMethods.GET;
import static org.sonatype.nexus.repository.http.HttpMethods.HEAD;
import static org.sonatype.nexus.repository.http.HttpMethods.POST;
import static org.sonatype.nexus.repository.http.HttpMethods.PUT;
import static org.sonatype.nexus.repository.http.HttpResponses.badRequest;
import static org.sonatype.nexus.repository.http.HttpResponses.methodNotAllowed;
import static org.sonatype.nexus.repository.http.HttpResponses.noContent;
import static org.sonatype.nexus.repository.http.HttpResponses.notFound;
import static org.sonatype.nexus.repository.http.HttpResponses.ok;

/**
 * Serves and accepts the HTTP API of a hosted Pub repository (Hosted Pub Repository Spec v2).
 */
@Component
@Singleton
public class PubContentHandler
    implements Handler
{
  public static final String PUB_JSON = "application/vnd.pub.v2+json";

  @Nonnull
  @Override
  public Response handle(@Nonnull final Context context) throws Exception {
    String path = contentPath(context);
    String method = context.getRequest().getAction();
    Repository repository = context.getRepository();
    PubContentFacet storage = repository.facet(PubContentFacet.class);

    switch (method) {
      case HEAD:
      case GET:
        return get(context, storage, path);
      case POST:
        return upload(context, storage, path);
      case PUT:
        return put(context, storage, path);
      case DELETE:
        return PubCoordinatesHelper.isArchive(path)
            ? (storage.delete(path) ? noContent() : notFound(path))
            : methodNotAllowed(method, GET, HEAD, POST, PUT, DELETE);
      default:
        return methodNotAllowed(method, GET, HEAD, POST, PUT, DELETE);
    }
  }

  private Response get(
      final Context context,
      final PubContentFacet storage,
      final String path) throws Exception
  {
    if (PubCoordinatesHelper.NEW_UPLOAD_PATH.equals(path)) {
      String repositoryUrl = repositoryUrl(context);
      return json("{\"url\":\"" + repositoryUrl + PubCoordinatesHelper.UPLOAD_PATH + "\",\"fields\":{}}");
    }
    if (PubCoordinatesHelper.FINALIZE_PATH.equals(path)) {
      return json("{\"success\":{\"message\":\"Package published successfully.\"}}");
    }

    Optional<String> packageName = PubCoordinatesHelper.packageFromMetadata(path);
    if (packageName.isPresent()) {
      byte[] metadata = PubMetadataBuilder.build(
          packageName.get(), repositoryUrl(context), storage.packages(packageName.get()));
      return ok(payload(metadata, PUB_JSON));
    }

    Optional<Content> content = storage.get(path);
    return content.map(org.sonatype.nexus.repository.http.HttpResponses::ok).orElseGet(() -> notFound(path));
  }

  private Response upload(
      final Context context,
      final PubContentFacet storage,
      final String path) throws Exception
  {
    if (!PubCoordinatesHelper.UPLOAD_PATH.equals(path)) {
      return notFound(path);
    }
    Request request = context.getRequest();
    if (!request.isMultipart()) {
      return badRequest("Expected a multipart/form-data upload");
    }

    PartPayload file = null;
    Iterable<PartPayload> parts = request.getMultiparts();
    if (parts == null) {
      return badRequest("Missing multipart body");
    }
    for (PartPayload part : parts) {
      try {
        if ("file".equals(part.getFieldName())) {
          file = part;
          break;
        }
      }
      finally {
        if (part != file) {
          part.close();
        }
      }
    }
    if (file == null) {
      return badRequest("Missing 'file' part in the upload");
    }
    try {
      byte[] archive = read(file);
      storage.putPackage(archive);
    }
    catch (IllegalArgumentException e) {
      return badRequest(e.getMessage());
    }
    finally {
      file.close();
    }

    String finalizeUrl = repositoryUrl(context) + PubCoordinatesHelper.FINALIZE_PATH;
    return new Response.Builder()
        .status(new Status(true, 204))
        .header(HttpHeaders.LOCATION, finalizeUrl)
        .build();
  }

  private Response put(
      final Context context,
      final PubContentFacet storage,
      final String path) throws Exception
  {
    if (!PubCoordinatesHelper.isArchive(path)) {
      return badRequest("Pub packages must be uploaded to /packages/<name>/versions/<version>.tar.gz");
    }
    Payload payload = context.getRequest().getPayload();
    if (payload == null) {
      return badRequest("Missing package payload");
    }
    try {
      storage.putPackage(read(payload));
    }
    catch (IllegalArgumentException e) {
      return badRequest(e.getMessage());
    }
    return org.sonatype.nexus.repository.http.HttpResponses.created();
  }

  private static byte[] read(final Payload payload) throws IOException {
    try (InputStream input = payload.openInputStream()) {
      return ByteStreams.toByteArray(input);
    }
  }

  private static Response json(final String body) {
    return ok(payload(body.getBytes(StandardCharsets.UTF_8), PUB_JSON));
  }

  private static BytesPayload payload(final byte[] body, final String contentType) {
    return new BytesPayload(body, contentType);
  }

  private static String repositoryUrl(final Context context) {
    String url = context.getRepository().getUrl();
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }

  @Nonnull
  private String contentPath(final Context context) {
    TokenMatcher.State state = context.getAttributes().require(TokenMatcher.State.class);
    String path = state.getTokens().get(PubRecipeSupport.PATH_NAME);
    if (path == null || path.isEmpty()) {
      return "/";
    }
    return path.startsWith("/") ? path : "/" + path;
  }

}
