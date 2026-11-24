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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nonnull;
import jakarta.inject.Singleton;

import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.group.GroupFacet;
import org.sonatype.nexus.repository.group.GroupHandler;
import org.sonatype.nexus.repository.pub.internal.PubCoordinatesHelper;
import org.sonatype.nexus.repository.view.Context;
import org.sonatype.nexus.repository.view.Response;
import org.sonatype.nexus.repository.view.matchers.token.TokenMatcher;
import org.sonatype.nexus.repository.view.payloads.BytesPayload;

import com.google.common.io.ByteStreams;
import org.springframework.stereotype.Component;

import static org.sonatype.nexus.repository.http.HttpResponses.notFound;
import static org.sonatype.nexus.repository.http.HttpResponses.ok;

/**
 * Merges the package documents of the members of a Pub group repository.
 *
 * <p>Archives are served from the first member that has them; metadata is merged by package name
 * and version so that a group exposes the union of its members. Archive URLs in the merged document
 * point back at the group, which then resolves them against its members.</p>
 */
@Component
@Singleton
public class PubGroupHandler
    extends GroupHandler
{
  @Nonnull
  @Override
  protected Response doGet(
      @Nonnull final Context context,
      @Nonnull final DispatchedRepositories dispatched) throws Exception
  {
    String path = contentPath(context);
    GroupFacet groupFacet = context.getRepository().facet(GroupFacet.class);
    Optional<String> packageName = PubCoordinatesHelper.packageFromMetadata(path);

    if (!packageName.isPresent()) {
      return getFirst(context, groupFacet.members(), dispatched);
    }

    List<byte[]> documents = new ArrayList<>();
    for (Map.Entry<Repository, Response> entry : getAll(context, groupFacet.members(), dispatched).entrySet()) {
      byte[] body = read(entry.getValue());
      if (body != null) {
        documents.add(body);
      }
    }
    if (documents.isEmpty()) {
      return notFound(path);
    }

    String repositoryUrl = context.getRepository().getUrl();
    byte[] merged = PubMetadataBuilder.merge(packageName.get(), repositoryUrl, documents);
    return ok(new BytesPayload(merged, PubContentHandler.PUB_JSON));
  }

  private static byte[] read(final Response response) {
    if (response == null || !response.getStatus().isSuccessful() || response.getPayload() == null) {
      return null;
    }
    try (InputStream input = response.getPayload().openInputStream()) {
      return ByteStreams.toByteArray(input);
    }
    catch (Exception e) {
      return null;
    }
  }

  private static String contentPath(final Context context) {
    TokenMatcher.State state = context.getAttributes().get(TokenMatcher.State.class);
    String path = state == null ? null : state.getTokens().get(PubRecipeSupport.PATH_NAME);
    if (path == null || path.isEmpty()) {
      path = context.getRequest().getPath();
    }
    return path.startsWith("/") ? path : "/" + path;
  }
}
