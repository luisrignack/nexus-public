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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import jakarta.inject.Singleton;

import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.go.GoCoordinatesHelper;
import org.sonatype.nexus.repository.group.GroupFacet;
import org.sonatype.nexus.repository.group.GroupHandler;
import org.sonatype.nexus.repository.view.Context;
import org.sonatype.nexus.repository.view.Response;
import org.sonatype.nexus.repository.view.payloads.BytesPayload;

import com.google.common.io.ByteStreams;
import org.springframework.stereotype.Component;

import static org.sonatype.nexus.repository.http.HttpResponses.notFound;
import static org.sonatype.nexus.repository.http.HttpResponses.ok;
import static org.sonatype.nexus.repository.view.ContentTypes.APPLICATION_JSON;
import static org.sonatype.nexus.repository.view.ContentTypes.TEXT_PLAIN;

/**
 * Go group handler.
 */
@Component
@Singleton
public class GoGroupHandler
    extends GroupHandler
{
  @Nonnull
  @Override
  protected Response doGet(
      @Nonnull final Context context,
      @Nonnull final DispatchedRepositories dispatched) throws Exception
  {
    String path = GoCoordinatesHelper.decode(contentPath(context));
    if (path == null) {
      path = "";
    }

    GoCoordinatesHelper.Coordinates coordinates = GoCoordinatesHelper.parse(path).orElse(null);
    if (coordinates != null && coordinates.isList()) {
      List<String> bodies = new ArrayList<>();
      GroupFacet groupFacet = context.getRepository().facet(GroupFacet.class);
      for (Map.Entry<Repository, Response> entry : getAll(context, groupFacet.members(), dispatched).entrySet()) {
        Response response = entry.getValue();
        if (response.getStatus().isSuccessful() && response.getPayload() != null) {
          try (InputStream input = response.getPayload().openInputStream()) {
            bodies.add(new String(ByteStreams.toByteArray(input), StandardCharsets.UTF_8));
          }
        }
      }
      if (bodies.isEmpty()) {
        return notFoundResponse(context);
      }
      return ok(new BytesPayload(GoIndexBuilder.mergeVersionLists(bodies), TEXT_PLAIN));
    }

    if (coordinates != null && coordinates.isLatest()) {
      List<String> bodies = new ArrayList<>();
      GroupFacet groupFacet = context.getRepository().facet(GroupFacet.class);
      for (Map.Entry<Repository, Response> entry : getAll(context, groupFacet.members(), dispatched).entrySet()) {
        Response response = entry.getValue();
        if (!response.getStatus().isSuccessful() || response.getPayload() == null) {
          continue;
        }
        // try to get list from this member by dispatching logic not directly available; skip
      }
      String latest = GoIndexBuilder.latestVersion(bodies);
      if (latest == null) {
        // fallback to first member
        return getFirst(context, groupFacet.members(), dispatched);
      }
      return ok(new BytesPayload(GoIndexBuilder.buildInfo(coordinates.getModule(), latest), APPLICATION_JSON));
    }

    GroupFacet groupFacet = context.getRepository().facet(GroupFacet.class);
    return getFirst(context, groupFacet.members(), dispatched);
  }

  private static String contentPath(final Context context) {
    org.sonatype.nexus.repository.view.matchers.token.TokenMatcher.State state =
        context.getAttributes().get(org.sonatype.nexus.repository.view.matchers.token.TokenMatcher.State.class);
    String path = state == null ? null : state.getTokens().get(GoRecipeSupport.PATH_NAME);
    if (path == null) {
      path = context.getRequest().getPath();
    }
    return path;
  }
}
