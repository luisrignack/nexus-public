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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import jakarta.inject.Singleton;

import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.group.GroupFacet;
import org.sonatype.nexus.repository.group.GroupHandler;
import org.sonatype.nexus.repository.nuget.NugetCoordinatesHelper;
import org.sonatype.nexus.repository.nuget.NugetIndexBuilder;
import org.sonatype.nexus.repository.nuget.NugetServiceIndex;
import org.sonatype.nexus.repository.view.Context;
import org.sonatype.nexus.repository.view.Response;
import org.sonatype.nexus.repository.view.matchers.token.TokenMatcher;
import org.sonatype.nexus.repository.view.payloads.BytesPayload;

import com.google.common.io.ByteStreams;

import org.springframework.stereotype.Component;

import static org.sonatype.nexus.repository.http.HttpResponses.ok;
import static org.sonatype.nexus.repository.view.ContentTypes.APPLICATION_JSON;

/**
 * NuGet group handler.
 *
 * <p>The service index is generated for the group itself so that clients keep resolving packages through it.
 * Search results of all members are merged (first entry per package id wins) and every other read is answered by
 * the first member that has the requested package.</p>
 */
@Component
@Singleton
public class NugetGroupHandler
    extends GroupHandler
{
  @Nonnull
  @Override
  protected Response doGet(
      @Nonnull final Context context,
      @Nonnull final DispatchedRepositories dispatched) throws Exception
  {
    String path = contentPath(context);

    if (path.isEmpty() || "index.json".equals(path)) {
      return ok(new BytesPayload(NugetServiceIndex.build(context.getRepository().getUrl()), APPLICATION_JSON));
    }

    if (isQuery(path)) {
      return mergeSearchResults(context, dispatched);
    }

    GroupFacet groupFacet = context.getRepository().facet(GroupFacet.class);
    return getFirst(context, groupFacet.members(), dispatched);
  }

  private Response mergeSearchResults(final Context context, final DispatchedRepositories dispatched)
      throws Exception
  {
    GroupFacet groupFacet = context.getRepository().facet(GroupFacet.class);
    String localBase = context.getRepository().getUrl();
    List<byte[]> bodies = new ArrayList<>();

    for (Map.Entry<Repository, Response> entry : getAll(context, groupFacet.members(), dispatched).entrySet()) {
      Response response = entry.getValue();
      if (!response.getStatus().isSuccessful() || response.getPayload() == null) {
        continue;
      }
      byte[] body;
      try (InputStream input = response.getPayload().openInputStream()) {
        body = ByteStreams.toByteArray(input);
      }
      String json = new String(body, StandardCharsets.UTF_8);
      String rewritten = NugetServiceIndex.rewriteUrls(json, entry.getKey().getUrl(), localBase);
      bodies.add(rewritten.getBytes(StandardCharsets.UTF_8));
    }

    return ok(new BytesPayload(NugetIndexBuilder.mergeSearchResults(bodies), APPLICATION_JSON));
  }

  private static boolean isQuery(final String path) {
    String normalized = path.startsWith("/") ? path.substring(1) : path;
    return normalized.startsWith(NugetCoordinatesHelper.QUERY);
  }

  private static String contentPath(final Context context) {
    TokenMatcher.State state = context.getAttributes().get(TokenMatcher.State.class);
    String path = state != null ? state.getTokens().get(NugetRecipeSupport.PATH_NAME) : null;
    if (path == null) {
      path = context.getRequest().getPath();
    }
    String normalized = path == null ? "" : path;
    return normalized.startsWith("/") ? normalized.substring(1) : normalized;
  }
}
