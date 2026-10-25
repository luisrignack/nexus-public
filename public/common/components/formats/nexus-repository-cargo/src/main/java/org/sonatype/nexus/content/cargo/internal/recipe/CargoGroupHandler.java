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
package org.sonatype.nexus.content.cargo.internal.recipe;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import jakarta.inject.Singleton;

import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.cargo.CargoCoordinatesHelper;
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
 * Cargo group handler.
 *
 * <p>{@code index/config.json} is synthesized so it points at the group itself; index files, crate detail and
 * downloads are served by the first member that has the crate; search responses of all members are merged with the
 * first member winning on duplicates.</p>
 *
 * @since 3.89
 */
@Component
@Singleton
public class CargoGroupHandler
    extends GroupHandler
{
  @Nonnull
  @Override
  protected Response doGet(
      @Nonnull final Context context,
      @Nonnull final DispatchedRepositories dispatched) throws Exception
  {
    String path = contentPath(context);

    if (CargoCoordinatesHelper.isConfigPath(path)) {
      return json(CargoIndexBuilder.configJson(repositoryBase(context)));
    }

    if (CargoCoordinatesHelper.isSearchPath(path)) {
      GroupFacet groupFacet = context.getRepository().facet(GroupFacet.class);
      List<String> bodies = new ArrayList<>();
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
      return json(CargoIndexBuilder.mergeSearch(bodies));
    }

    GroupFacet groupFacet = context.getRepository().facet(GroupFacet.class);
    return getFirst(context, groupFacet.members(), dispatched);
  }

  private static Response json(final String body) {
    return ok(new BytesPayload(body.getBytes(StandardCharsets.UTF_8), APPLICATION_JSON));
  }

  private static String repositoryBase(final Context context) {
    String url = context.getRepository().getUrl();
    return url.endsWith("/") ? url : url + "/";
  }

  private static String contentPath(final Context context) {
    TokenMatcher.State state = context.getAttributes().get(TokenMatcher.State.class);
    String path = state == null ? null : state.getTokens().get(CargoRecipeSupport.PATH_NAME);
    if (path == null) {
      path = context.getRequest().getPath();
    }
    return CargoCoordinatesHelper.normalize(path);
  }
}
