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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import jakarta.inject.Singleton;

import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.conan.ConanApiPath;
import org.sonatype.nexus.repository.conan.ConanCoordinates;
import org.sonatype.nexus.repository.group.GroupFacet;
import org.sonatype.nexus.repository.group.GroupHandler;
import org.sonatype.nexus.repository.view.Context;
import org.sonatype.nexus.repository.view.Response;
import org.sonatype.nexus.repository.view.matchers.token.TokenMatcher;
import org.sonatype.nexus.repository.view.payloads.BytesPayload;

import com.google.common.io.ByteStreams;
import org.springframework.stereotype.Component;

import static org.sonatype.nexus.repository.http.HttpResponses.ok;
import static org.sonatype.nexus.repository.view.ContentTypes.APPLICATION_JSON;

/**
 * Conan group handler.
 *
 * <p>Revision listings and v1 search results are merged across every member, everything else is served by the
 * first member that answers successfully. {@code download_urls} documents are re-written so the returned URLs point
 * at the group instead of the individual member.</p>
 */
@Component
@Singleton
public class ConanGroupHandler
    extends GroupHandler
{
  @Nonnull
  @Override
  protected Response doGet(
      @Nonnull final Context context,
      @Nonnull final DispatchedRepositories dispatched) throws Exception
  {
    ConanApiPath apiPath = ConanApiPath.parse(contentPath(context));
    GroupFacet groupFacet = context.getRepository().facet(GroupFacet.class);
    List<Repository> members = groupFacet.members();

    switch (apiPath.kind()) {
      case SEARCH:
        return mergeSearch(context, members, dispatched);
      case RECIPE_REVISIONS:
      case PACKAGE_REVISIONS:
        return mergeRevisions(context, members, dispatched);
      case V1_DOWNLOAD_URLS:
      case V1_PACKAGE_DOWNLOAD_URLS:
        return downloadUrls(context, members, dispatched);
      default:
        return getFirst(context, members, dispatched);
    }
  }

  /**
   * Union of the search results of every member; an empty result is still a valid (200) answer.
   */
  private Response mergeSearch(
      final Context context,
      final List<Repository> members,
      final DispatchedRepositories dispatched) throws Exception
  {
    List<String> bodies = new ArrayList<>();
    for (final Map.Entry<Repository, Response> entry : getAll(context, members, dispatched).entrySet()) {
      if (isSuccessful(entry.getValue())) {
        bodies.add(read(entry.getValue()));
      }
    }
    return ok(payload(ConanMetadataBuilder.mergeSearchBodies(bodies)));
  }

  /**
   * Union of the revision listings of every member, newest first.
   */
  private Response mergeRevisions(
      final Context context,
      final List<Repository> members,
      final DispatchedRepositories dispatched) throws Exception
  {
    Map<String, String> bodies = new LinkedHashMap<>();
    for (final Map.Entry<Repository, Response> entry : getAll(context, members, dispatched).entrySet()) {
      if (isSuccessful(entry.getValue())) {
        bodies.put(entry.getKey().getUrl(), read(entry.getValue()));
      }
    }

    if (bodies.isEmpty()) {
      return notFoundResponse(context);
    }

    String groupUrl = context.getRepository().getUrl();
    return ok(payload(ConanMetadataBuilder.mergeRevisionBodies(bodies, groupUrl)));
  }

  /**
   * First member able to list the download urls, with every member URL rewritten to the group URL.
   */
  private Response downloadUrls(
      final Context context,
      final List<Repository> members,
      final DispatchedRepositories dispatched) throws Exception
  {
    Response response = getFirst(context, members, dispatched);
    if (!isSuccessful(response) || response.getPayload() == null) {
      return response;
    }

    String body = read(response);
    String groupUrl = context.getRepository().getUrl();
    String rewritten = body;
    for (final Repository member : members) {
      rewritten = ConanDownloadUrlsRewriter.rewritePrefix(rewritten, member.getUrl(), groupUrl);
    }

    if (rewritten.equals(body)) {
      return response;
    }
    return new Response.Builder()
        .copy(response)
        .payload(payload(rewritten))
        .build();
  }

  private static boolean isSuccessful(final Response response) {
    return response.getStatus().isSuccessful() && response.getPayload() != null;
  }

  private static String read(final Response response) throws IOException {
    try (InputStream input = response.getPayload().openInputStream()) {
      return new String(ByteStreams.toByteArray(input), StandardCharsets.UTF_8);
    }
  }

  private static BytesPayload payload(final String body) {
    return new BytesPayload(body.getBytes(StandardCharsets.UTF_8), APPLICATION_JSON);
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
