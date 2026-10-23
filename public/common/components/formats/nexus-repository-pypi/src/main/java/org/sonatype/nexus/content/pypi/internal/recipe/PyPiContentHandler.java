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
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nonnull;
import jakarta.inject.Singleton;

import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.content.pypi.PyPiContentFacet;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.http.HttpResponses;
import org.sonatype.nexus.repository.pypi.PyPiCoordinatesHelper;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.Context;
import org.sonatype.nexus.repository.view.Handler;
import org.sonatype.nexus.repository.view.PartPayload;
import org.sonatype.nexus.repository.view.Request;
import org.sonatype.nexus.repository.view.Response;
import org.sonatype.nexus.repository.view.Status;
import org.sonatype.nexus.repository.view.matchers.token.TokenMatcher;

import com.google.common.io.ByteStreams;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.springframework.stereotype.Component;

import static com.google.common.base.Preconditions.checkState;
import static org.sonatype.nexus.repository.http.HttpMethods.DELETE;
import static org.sonatype.nexus.repository.http.HttpMethods.GET;
import static org.sonatype.nexus.repository.http.HttpMethods.HEAD;
import static org.sonatype.nexus.repository.http.HttpMethods.POST;
import static org.sonatype.nexus.repository.view.ContentTypes.TEXT_HTML;

/**
 * Handles the PEP 503 index and the legacy upload API of a PyPI hosted repository.
 */
@Component
@Singleton
public class PyPiContentHandler
    extends ComponentSupport
    implements Handler
{
  private static final String[] UPLOAD_FILE_FIELDS = {"content", "file"};

  @Nonnull
  @Override
  public Response handle(@Nonnull final Context context) throws Exception {
    String path = contentPath(context);
    String method = context.getRequest().getAction();

    Repository repository = context.getRepository();
    log.debug("{} repository '{}' content-path: {}", method, repository.getName(), path);

    PyPiContentFacet storage = repository.facet(PyPiContentFacet.class);

    switch (method) {
      case HEAD:
      case GET:
        return get(storage, path);

      case POST:
        return post(context, storage, path);

      case DELETE:
        return delete(storage, path);

      default:
        return HttpResponses.methodNotAllowed(method, GET, HEAD, POST, DELETE);
    }
  }

  private Response get(final PyPiContentFacet storage, final String path) throws IOException {
    if (isRoot(path)) {
      return redirect("simple/");
    }

    if (PyPiCoordinatesHelper.isSimpleIndex(path)) {
      return html(PyPiIndexBuilder.buildRootIndex(storage));
    }

    Optional<String> project = PyPiCoordinatesHelper.projectFromIndexPath(path);
    if (project.isPresent()) {
      return html(PyPiIndexBuilder.buildProjectIndex(storage, project.get()));
    }

    if (path.startsWith(PyPiCoordinatesHelper.PACKAGES_PREFIX + "/")) {
      return storage.get(path)
          .map(HttpResponses::ok)
          .orElseGet(() -> HttpResponses.notFound(path));
    }

    return HttpResponses.notFound(path);
  }

  private Response post(
      final Context context,
      final PyPiContentFacet storage,
      final String path) throws Exception
  {
    if (!isRoot(path)) {
      return HttpResponses.notFound(path);
    }

    Request request = context.getRequest();
    if (!request.isMultipart()) {
      return HttpResponses.badRequest("Expected a multipart/form-data upload");
    }

    Map<String, String> fields = new LinkedHashMap<>();
    PartPayload file = null;

    Iterable<PartPayload> parts = request.getMultiparts();
    if (parts == null) {
      return HttpResponses.badRequest("Missing multipart body");
    }

    for (PartPayload part : parts) {
      try {
        if (part.isFormField()) {
          fields.put(part.getFieldName(), read(part));
        }
        else if (file == null && isFileField(part.getFieldName())) {
          // consume the remaining metadata before the file, then stop iterating:
          // the streaming multipart iterator skips the file if we ask for the next part
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
      return HttpResponses.badRequest("Missing distribution file");
    }

    try {
      String fileName = PyPiCoordinatesHelper.sanitizeFileName(file.getName());
      String project = fields.get("name");
      if (project == null || project.trim().isEmpty()) {
        project = PyPiCoordinatesHelper.projectFromFileName(fileName);
      }

      String version = fields.get("version");
      if (version == null || version.trim().isEmpty()) {
        version = PyPiCoordinatesHelper.versionFromFileName(fileName).orElse(null);
      }

      String requiresPython = fields.get("requires_python");
      if (requiresPython == null) {
        requiresPython = fields.get("requires-python");
      }

      String assetPath = PyPiCoordinatesHelper.PACKAGES_PREFIX + "/"
          + PyPiCoordinatesHelper.sanitizeFileName(fileName);
      storage.put(assetPath, project, version, file, requiresPython);
      log.debug("Uploaded {} for project {}", assetPath, project);
      return HttpResponses.created();
    }
    finally {
      file.close();
    }
  }

  private Response delete(final PyPiContentFacet storage, final String path) throws IOException {
    if (path.startsWith(PyPiCoordinatesHelper.PACKAGES_PREFIX + "/")) {
      return storage.delete(path) ? HttpResponses.noContent() : HttpResponses.notFound(path);
    }

    Optional<String> project = PyPiCoordinatesHelper.projectFromIndexPath(path);
    if (project.isPresent()) {
      return storage.deleteProject(project.get()) ? HttpResponses.noContent() : HttpResponses.notFound(path);
    }

    return HttpResponses.notFound(path);
  }

  private static boolean isRoot(final String path) {
    return path.isEmpty() || "/".equals(path);
  }

  private static boolean isFileField(final String fieldName) {
    for (String field : UPLOAD_FILE_FIELDS) {
      if (field.equals(fieldName)) {
        return true;
      }
    }
    return false;
  }

  private static String read(final PartPayload part) throws IOException {
    try (InputStream input = part.openInputStream()) {
      return new String(ByteStreams.toByteArray(input), StandardCharsets.UTF_8);
    }
  }

  private static Response html(final byte[] body) {
    return HttpResponses.ok(new org.sonatype.nexus.repository.view.payloads.BytesPayload(body, TEXT_HTML));
  }

  private static Response redirect(final String location) {
    return new Response.Builder()
        .status(new Status(true, HttpStatus.SC_MOVED_TEMPORARILY))
        .header(HttpHeaders.LOCATION, location)
        .build();
  }

  @Nonnull
  private String contentPath(final Context context) {
    TokenMatcher.State state = context.getAttributes().require(TokenMatcher.State.class);
    String path = state.getTokens().get("path");
    checkState(path != null, "Missing token: path");
    String decoded = PyPiCoordinatesHelper.decode(path);
    return decoded.startsWith("/") ? decoded : "/" + decoded;
  }
}
