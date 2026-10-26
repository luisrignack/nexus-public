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
package org.sonatype.nexus.content.ansible.internal.recipe;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import jakarta.inject.Singleton;

import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.ansible.AnsibleCoordinatesHelper;
import org.sonatype.nexus.repository.ansible.AnsibleCoordinatesHelper.GalaxyRequest;
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
import static org.sonatype.nexus.repository.view.ContentTypes.APPLICATION_JSON;

/**
 * Merges the Galaxy documents of the members of a group.
 *
 * <p>Lists are merged collection by collection (the first member that knows a collection wins) and
 * every absolute URL a member produced is rewritten so it points at the group instead.</p>
 */
@Component
@Singleton
public class AnsibleGroupHandler
    extends GroupHandler
{
  @Nonnull
  @Override
  protected Response doGet(
      @Nonnull final Context context,
      @Nonnull final DispatchedRepositories dispatched) throws Exception
  {
    String path = contentPath(context);
    GalaxyRequest request = AnsibleCoordinatesHelper.parse(path);
    String groupUrl = context.getRepository().getUrl();

    switch (request.getKind()) {
      case API_DISCOVERY:
        return ok(new BytesPayload(AnsibleIndexBuilder.apiDiscovery(), APPLICATION_JSON));
      case ROOT:
      case INDEX_ALL:
        return merge(context, dispatched, groupUrl,
            documents -> AnsibleIndexBuilder.mergeCollectionLists(documents));
      case INDEX_NAMESPACE: {
        return merge(context, dispatched, groupUrl, documents -> {
          byte[] merged = AnsibleIndexBuilder.mergeCollectionLists(documents);
          return merged;
        });
      }
      case INDEX_VERSIONS:
        return merge(context, dispatched, groupUrl,
            documents -> AnsibleIndexBuilder.mergeVersionLists(documents));
      case DOWNLOAD:
        // the request addresses a tarball: answer with the bytes of the first member that has it.
        // NOTE: must not collect() first, because collecting marks every member as already
        // dispatched and getFirst() would then skip them all and answer 404.
        return firstSuccessful(context, dispatched, path);
      case INDEX_COLLECTION:
      case INDEX_VERSION: {
        List<String> documents = collect(context, dispatched, request);
        if (documents.isEmpty()) {
          return notFound(path);
        }
        byte[] merged = mergeDocument(request, documents, groupUrl);
        if (merged == null) {
          return notFound(path);
        }
        return ok(new BytesPayload(merged, APPLICATION_JSON));
      }
      default:
        break;
    }

    return firstSuccessful(context, dispatched, path);
  }

  private byte[] mergeDocument(
      final GalaxyRequest request,
      final List<String> documents,
      final String groupUrl)
  {
    String merged;
    switch (request.getKind()) {
      case INDEX_COLLECTION:
        merged = AnsibleIndexBuilder.mergeCollectionDetail(documents, groupUrl);
        break;
      case INDEX_VERSION:
        merged = AnsibleIndexBuilder.mergeVersionDetail(documents, groupUrl);
        break;
      default:
        merged = null;
        break;
    }
    return merged == null ? null : merged.getBytes(StandardCharsets.UTF_8);
  }

  private Response merge(
      final Context context,
      final DispatchedRepositories dispatched,
      final String groupUrl,
      final java.util.function.Function<List<String>, byte[]> mergeFunction) throws Exception
  {
    GroupFacet groupFacet = context.getRepository().facet(GroupFacet.class);
    List<String> documents = new ArrayList<>();
    for (Map.Entry<Repository, Response> entry : getAll(context, groupFacet.members(), dispatched).entrySet()) {
      String document = read(entry.getValue());
      if (document != null) {
        documents.add(rewriteUrls(document, entry.getKey().getUrl(), groupUrl));
      }
    }
    if (documents.isEmpty()) {
      return notFound(context.getRequest().getPath());
    }
    byte[] merged = mergeFunction.apply(documents);
    if (merged == null) {
      return notFound(context.getRequest().getPath());
    }
    return ok(new BytesPayload(merged, APPLICATION_JSON));
  }

  private List<String> collect(
      final Context context,
      final DispatchedRepositories dispatched,
      final GalaxyRequest request) throws Exception
  {
    GroupFacet groupFacet = context.getRepository().facet(GroupFacet.class);
    List<String> documents = new ArrayList<>();
    for (Map.Entry<Repository, Response> entry : getAll(context, groupFacet.members(), dispatched).entrySet()) {
      String document = read(entry.getValue());
      if (document != null) {
        documents.add(rewriteUrls(document, entry.getKey().getUrl(), context.getRepository().getUrl()));
      }
    }
    return documents;
  }

  private Response firstSuccessful(
      final Context context,
      final DispatchedRepositories dispatched,
      final String path) throws Exception
  {
    GroupFacet groupFacet = context.getRepository().facet(GroupFacet.class);
    return getFirst(context, groupFacet.members(), dispatched);
  }

  private static String read(final Response response) {
    if (response == null || !response.getStatus().isSuccessful() || response.getPayload() == null) {
      return null;
    }
    try (InputStream input = response.getPayload().openInputStream()) {
      return new String(ByteStreams.toByteArray(input), StandardCharsets.UTF_8);
    }
    catch (Exception e) {
      return null;
    }
  }

  private static String rewriteUrls(final String document, final String from, final String to) {
    return AnsibleIndexBuilder.rewrite(document, from, to);
  }

  private static String contentPath(final Context context) {
    TokenMatcher.State state = context.getAttributes().get(TokenMatcher.State.class);
    String path = state == null ? null : state.getTokens().get(AnsibleRecipeSupport.PATH_NAME);
    if (path == null) {
      path = context.getRequest().getPath();
    }
    String decoded = AnsibleCoordinatesHelper.decode(path);
    if (decoded == null || decoded.isEmpty()) {
      return "/";
    }
    return decoded.startsWith("/") ? decoded : "/" + decoded;
  }
}
