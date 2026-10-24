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
package org.sonatype.nexus.content.rubygems.internal.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import jakarta.inject.Singleton;

import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.group.GroupFacet;
import org.sonatype.nexus.repository.group.GroupHandler;
import org.sonatype.nexus.repository.rubygems.RubyGemsCoordinatesHelper;
import org.sonatype.nexus.repository.view.Context;
import org.sonatype.nexus.repository.view.Response;
import org.sonatype.nexus.repository.view.payloads.BytesPayload;

import org.springframework.stereotype.Component;

import static org.sonatype.nexus.repository.http.HttpResponses.ok;

/**
 * RubyGems group handler.
 */
@Component
@Singleton
public class RubyGemsGroupHandler
    extends GroupHandler
{
  @Nonnull
  @Override
  protected Response doGet(
      @Nonnull final Context context,
      @Nonnull final DispatchedRepositories dispatched) throws Exception
  {
    String path = contentPath(context);

    if (isSpecsIndex(path)) {
      List<byte[]> indexes = new ArrayList<>();
      GroupFacet groupFacet = context.getRepository().facet(GroupFacet.class);
      for (Map.Entry<Repository, Response> entry : getAll(context, groupFacet.members(), dispatched).entrySet()) {
        Response response = entry.getValue();
        if (response.getStatus().isSuccessful() && response.getPayload() != null) {
          try (java.io.InputStream input = response.getPayload().openInputStream()) {
            byte[] bytes = com.google.common.io.ByteStreams.toByteArray(input);
            indexes.add(bytes);
          }
        }
      }
      if (indexes.isEmpty()) {
        return notFoundResponse(context);
      }
      byte[] merged = RubyGemsIndexBuilder.mergeSpecsIndexes(indexes);
      return ok(new BytesPayload(merged, "application/x-gzip"));
    }

    GroupFacet groupFacet = context.getRepository().facet(GroupFacet.class);
    return getFirst(context, groupFacet.members(), dispatched);
  }

  private static boolean isSpecsIndex(final String path) {
    return "/specs.4.8.gz".equals(path)
        || "/latest_specs.4.8.gz".equals(path)
        || "/prerelease_specs.4.8.gz".equals(path);
  }

  private static String contentPath(final Context context) {
    org.sonatype.nexus.repository.view.matchers.token.TokenMatcher.State state = context.getAttributes().get(org.sonatype.nexus.repository.view.matchers.token.TokenMatcher.State.class);
    String path = state == null ? null : state.getTokens().get(RubyGemsRecipeSupport.PATH_NAME);
    if (path == null) {
      path = context.getRequest().getPath();
    }
    String decoded = RubyGemsCoordinatesHelper.decode(path);
    if (decoded == null || decoded.isEmpty()) {
      return "/";
    }
    return decoded.startsWith("/") ? decoded : "/" + decoded;
  }
}
