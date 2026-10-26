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
package org.sonatype.nexus.content.alpine.internal.recipe;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import jakarta.inject.Singleton;

import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.group.GroupFacet;
import org.sonatype.nexus.repository.group.GroupHandler;
import org.sonatype.nexus.repository.view.Context;
import org.sonatype.nexus.repository.view.Response;
import org.sonatype.nexus.repository.view.matchers.token.TokenMatcher;
import org.sonatype.nexus.repository.view.payloads.BytesPayload;

import com.google.common.io.ByteStreams;
import org.springframework.stereotype.Component;

import static org.sonatype.nexus.repository.http.HttpResponses.notFound;
import static org.sonatype.nexus.repository.http.HttpResponses.ok;
import static org.sonatype.nexus.repository.view.ContentTypes.APPLICATION_OCTET_STREAM;

/**
 * Merges the {@code APKINDEX.tar.gz} documents of the members of an Alpine group repository.
 *
 * <p>Stanzas are de-duplicated by package name and version: the first member that provides a
 * package wins. Package files are served from the first member that has them.</p>
 */
@Component
@Singleton
public class AlpineGroupHandler
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

    if (!AlpineContentHandler.isIndex(path)) {
      return getFirst(context, groupFacet.members(), dispatched);
    }

    List<String> merged = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (Map.Entry<Repository, Response> entry : getAll(context, groupFacet.members(), dispatched).entrySet()) {
      byte[] body = read(entry.getValue());
      if (body == null) {
        continue;
      }
      for (String stanza : AlpineIndexBuilder.parse(body)) {
        if (seen.add(AlpineIndexBuilder.packageKey(stanza))) {
          merged.add(stanza);
        }
      }
    }
    if (merged.isEmpty()) {
      return notFound(path);
    }
    return ok(new BytesPayload(AlpineIndexBuilder.build(merged), APPLICATION_OCTET_STREAM));
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
    String path = state == null ? null : state.getTokens().get(AlpineRecipeSupport.PATH_NAME);
    if (path == null || path.isEmpty()) {
      path = context.getRequest().getPath();
    }
    return path.startsWith("/") ? path : "/" + path;
  }
}
