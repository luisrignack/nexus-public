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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import jakarta.inject.Singleton;

import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.group.GroupFacet;
import org.sonatype.nexus.repository.group.GroupHandler;
import org.sonatype.nexus.repository.pypi.PyPiCoordinatesHelper;
import org.sonatype.nexus.repository.view.Context;
import org.sonatype.nexus.repository.view.Payload;
import org.sonatype.nexus.repository.view.Response;
import org.sonatype.nexus.repository.view.matchers.token.TokenMatcher;
import org.sonatype.nexus.repository.view.payloads.BytesPayload;

import com.google.common.io.ByteStreams;
import org.springframework.stereotype.Component;

import static org.sonatype.nexus.repository.http.HttpResponses.ok;
import static org.sonatype.nexus.repository.view.ContentTypes.TEXT_HTML;

/**
 * PyPI group handler.
 *
 * <p>The PEP 503 indexes are merged across all members, removing duplicate links; package files and any
 * other path are served by the first member that answers successfully.</p>
 */
@Component
@Singleton
public class PyPiGroupHandler
    extends GroupHandler
{
  @Nonnull
  @Override
  protected Response doGet(
      @Nonnull final Context context,
      @Nonnull final DispatchedRepositories dispatched) throws Exception
  {
    String path = PyPiCoordinatesHelper.stripTrailingSlash(
        PyPiCoordinatesHelper.decode(contentPath(context)));

    if (PyPiCoordinatesHelper.isSimpleIndex(path)) {
      return mergedIndex(context, dispatched);
    }

    if (PyPiCoordinatesHelper.projectFromIndexPath(path).isPresent()) {
      return mergedIndex(context, dispatched);
    }

    GroupFacet groupFacet = context.getRepository().facet(GroupFacet.class);
    return getFirst(context, groupFacet.members(), dispatched);
  }

  private Response mergedIndex(
      final Context context,
      final DispatchedRepositories dispatched) throws Exception
  {
    GroupFacet groupFacet = context.getRepository().facet(GroupFacet.class);

    Set<String> anchors = new LinkedHashSet<>();
    for (Map.Entry<Repository, Response> entry : getAll(context, groupFacet.members(), dispatched).entrySet()) {
      String html = readHtml(entry.getValue());
      if (html != null) {
        anchors.addAll(PyPiIndexBuilder.extractAnchors(html));
      }
    }

    if (anchors.isEmpty()) {
      return notFoundResponse(context);
    }

    return ok(new BytesPayload(PyPiIndexBuilder.mergeAnchors(new ArrayList<>(anchors)), TEXT_HTML));
  }

  private static String readHtml(final Response response) throws IOException {
    if (!response.getStatus().isSuccessful()) {
      return null;
    }
    Payload payload = response.getPayload();
    if (payload == null) {
      return null;
    }
    try (InputStream input = payload.openInputStream()) {
      return new String(ByteStreams.toByteArray(input), StandardCharsets.UTF_8);
    }
  }

  private static String contentPath(final Context context) {
    TokenMatcher.State state = context.getAttributes().get(TokenMatcher.State.class);
    String path = state == null ? null : state.getTokens().get(PyPiRecipeSupport.PATH_NAME);
    if (path == null) {
      path = context.getRequest().getPath();
    }
    String decoded = PyPiCoordinatesHelper.decode(path);
    return decoded.startsWith("/") ? decoded : "/" + decoded;
  }
}
