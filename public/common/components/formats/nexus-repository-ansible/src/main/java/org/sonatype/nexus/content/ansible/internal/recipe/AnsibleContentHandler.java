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

import java.io.IOException;
import java.util.Optional;

import javax.annotation.Nonnull;
import jakarta.inject.Singleton;

import org.sonatype.nexus.content.ansible.AnsibleContentFacet;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.ansible.AnsibleCoordinatesHelper;
import org.sonatype.nexus.repository.ansible.AnsibleCoordinatesHelper.GalaxyRequest;
import org.sonatype.nexus.repository.ansible.AnsibleCoordinatesHelper.Kind;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.Context;
import org.sonatype.nexus.repository.view.Handler;
import org.sonatype.nexus.repository.view.Request;
import org.sonatype.nexus.repository.view.Response;
import org.sonatype.nexus.repository.view.matchers.token.TokenMatcher;
import org.sonatype.nexus.repository.view.payloads.BytesPayload;

import org.springframework.stereotype.Component;

import static org.sonatype.nexus.repository.http.HttpMethods.DELETE;
import static org.sonatype.nexus.repository.http.HttpMethods.GET;
import static org.sonatype.nexus.repository.http.HttpMethods.HEAD;
import static org.sonatype.nexus.repository.http.HttpMethods.POST;
import static org.sonatype.nexus.repository.http.HttpMethods.PUT;
import static org.sonatype.nexus.repository.http.HttpResponses.badRequest;
import static org.sonatype.nexus.repository.http.HttpResponses.created;
import static org.sonatype.nexus.repository.http.HttpResponses.methodNotAllowed;
import static org.sonatype.nexus.repository.http.HttpResponses.noContent;
import static org.sonatype.nexus.repository.http.HttpResponses.notFound;
import static org.sonatype.nexus.repository.http.HttpResponses.ok;
import static org.sonatype.nexus.repository.view.ContentTypes.APPLICATION_JSON;

/**
 * Serves the Galaxy (v3/v2) API and the collection uploads of hosted Ansible repositories.
 */
@Component
@Singleton
public class AnsibleContentHandler
    implements Handler
{
  @Nonnull
  @Override
  public Response handle(@Nonnull final Context context) throws Exception {
    String path = contentPath(context);
    String method = context.getRequest().getAction();

    Repository repository = context.getRepository();
    AnsibleContentFacet storage = repository.facet(AnsibleContentFacet.class);
    String baseUrl = repository.getUrl();
    GalaxyRequest request = AnsibleCoordinatesHelper.parse(path);

    switch (method) {
      case HEAD:
      case GET:
        return get(storage, request, path, baseUrl);
      case POST:
      case PUT:
        return put(context, storage, request, path);
      case DELETE:
        return delete(storage, request, path);
      default:
        return methodNotAllowed(method, GET, HEAD, POST, PUT, DELETE);
    }
  }

  private Response get(
      final AnsibleContentFacet storage,
      final GalaxyRequest request,
      final String path,
      final String baseUrl) throws IOException
  {
    switch (request.getKind()) {
      case ROOT:
        return ok(new BytesPayload(AnsibleIndexBuilder.root(baseUrl), APPLICATION_JSON));
      case API_DISCOVERY:
        return ok(new BytesPayload(AnsibleIndexBuilder.apiDiscovery(), APPLICATION_JSON));
      case INDEX_ALL:
        return ok(new BytesPayload(AnsibleIndexBuilder.collectionList(storage, baseUrl), APPLICATION_JSON));
      case INDEX_NAMESPACE:
        return ok(new BytesPayload(
            AnsibleIndexBuilder.namespaceList(storage, request.getNamespace(), baseUrl), APPLICATION_JSON));
      case INDEX_COLLECTION:
        return document(AnsibleIndexBuilder.collection(
            storage, request.getNamespace(), request.getName(), baseUrl), path);
      case INDEX_VERSIONS:
        return ok(new BytesPayload(
            AnsibleIndexBuilder.versions(storage, request.getNamespace(), request.getName(), baseUrl),
            APPLICATION_JSON));
      case INDEX_VERSION:
        return document(AnsibleIndexBuilder.version(
            storage, request.getNamespace(), request.getName(), request.getVersion(), baseUrl), path);
      case DOWNLOAD:
        // only the repository's own download path carries coordinates; upstream Galaxy artifact
        // paths are proxied, never served by a hosted repository
        if (request.getNamespace() == null) {
          return notFound(path);
        }
        return download(storage, request);
      default:
        break;
    }

    if (request.getKind() == Kind.ASSET) {
      Optional<Content> content = storage.get(path);
      if (content.isPresent()) {
        return ok(content.get());
      }
      // a bare tarball path may address the canonical location of its coordinates
      String[] coordinates = AnsibleCoordinatesHelper.pathCoordinates(path);
      if (coordinates != null) {
        String canonical = AnsibleCoordinatesHelper.canonicalPath(coordinates[0], coordinates[1], coordinates[2]);
        Optional<Content> canonicalContent = storage.get(canonical);
        if (canonicalContent.isPresent()) {
          return ok(canonicalContent.get());
        }
      }
    }
    return notFound(path);
  }

  private Response download(final AnsibleContentFacet storage, final GalaxyRequest request) throws IOException {
    String canonical = AnsibleCoordinatesHelper.canonicalPath(
        request.getNamespace(), request.getName(), request.getVersion());
    Optional<Content> content = storage.get(canonical);
    if (content.isEmpty()) {
      return notFound(canonical);
    }
    return ok(content.get());
  }

  private Response document(final byte[] body, final String path) {
    if (body == null) {
      return notFound(path);
    }
    return ok(new BytesPayload(body, APPLICATION_JSON));
  }

  private Response put(
      final Context context,
      final AnsibleContentFacet storage,
      final GalaxyRequest request,
      final String path) throws IOException
  {
    Request request1 = context.getRequest();
    if (request1.getPayload() == null) {
      return badRequest("Missing collection archive payload");
    }

    String namespace;
    String name;
    String version;
    switch (request.getKind()) {
      case INDEX_VERSION:
        namespace = request.getNamespace();
        name = request.getName();
        version = request.getVersion();
        break;
      case ASSET:
        String[] coordinates = AnsibleCoordinatesHelper.pathCoordinates(path);
        if (coordinates == null) {
          return badRequest("Uploads must target /collections/{namespace}/{name}/{version}/<file>.tar.gz"
              + " or /api/v3/.../collections/index/{namespace}/{name}/versions/{version}/");
        }
        namespace = coordinates[0];
        name = coordinates[1];
        version = coordinates[2];
        break;
      default:
        return badRequest("Cannot upload a collection to " + path);
    }

    String canonical = AnsibleCoordinatesHelper.canonicalPath(namespace, name, version);
    try {
      storage.putCollection(canonical, namespace, name, version, request1.getPayload());
    }
    catch (IllegalArgumentException e) {
      return badRequest(e.getMessage());
    }
    catch (IOException e) {
      return badRequest("Not a valid Ansible collection archive");
    }
    return created();
  }

  private Response delete(
      final AnsibleContentFacet storage,
      final GalaxyRequest request,
      final String path) throws IOException
  {
    if (request.getKind() == Kind.INDEX_COLLECTION
        || request.getKind() == Kind.INDEX_VERSIONS
        || request.getKind() == Kind.INDEX_VERSION)
    {
      boolean deleted = storage.deleteCollection(request.getNamespace(), request.getName());
      return deleted ? noContent() : notFound(path);
    }
    return storage.delete(path) ? noContent() : notFound(path);
  }

  @Nonnull
  private String contentPath(final Context context) {
    TokenMatcher.State state = context.getAttributes().require(TokenMatcher.State.class);
    String path = state.getTokens().get(AnsibleRecipeSupport.PATH_NAME);
    String decoded = AnsibleCoordinatesHelper.decode(path);
    if (decoded == null || decoded.isEmpty()) {
      return "/";
    }
    return decoded.startsWith("/") ? decoded : "/" + decoded;
  }
}
