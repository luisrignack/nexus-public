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
package org.sonatype.nexus.sbom.rest;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javax.ws.rs.Encoded;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.common.entity.Continuation;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.content.facet.ContentFacet;
import org.sonatype.nexus.repository.content.fluent.FluentAsset;
import org.sonatype.nexus.repository.content.fluent.FluentComponent;
import org.sonatype.nexus.repository.rest.api.RepositoryManagerRESTAdapter;
import org.sonatype.nexus.rest.Resource;
import org.sonatype.nexus.sbom.SbomFormat;
import org.sonatype.nexus.sbom.SbomGenerator;
import org.sonatype.nexus.sbom.SbomGenerator.ComponentData;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.shiro.authz.annotation.RequiresPermissions;

import static com.google.common.base.Preconditions.checkNotNull;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.sonatype.nexus.rest.APIConstants.V1_API_PREFIX;
import org.springframework.stereotype.Component;

/**
 * Exports the component inventory of a repository as a standard SBOM (CycloneDX 1.5 or SPDX 2.3).
 *
 * @since 3.89
 */
@Component
@Singleton
@Path(SbomResource.RESOURCE_URI)
@Produces(APPLICATION_JSON)
public class SbomResource
    extends ComponentSupport
    implements Resource
{
  public static final String RESOURCE_URI = V1_API_PREFIX + "/sbom";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final int PAGE_SIZE = 100;

  private static final int MAX_COMPONENTS = 100_000;

  private static final long MAX_MANIFEST_BYTES = 5_000_000L;

  private final RepositoryManagerRESTAdapter repositoryAdapter;

  @Inject
  public SbomResource(final RepositoryManagerRESTAdapter repositoryAdapter) {
    this.repositoryAdapter = checkNotNull(repositoryAdapter);
  }

  @GET
  @Path("/{repository}")
  @RequiresPermissions("nexus:sbom:read")
  public Response getSbom(
      @PathParam("repository") @Encoded final String repositoryName,
      @QueryParam("format") @Nullable final String format) throws IOException
  {
    SbomFormat sbomFormat;
    try {
      sbomFormat = SbomFormat.from(format);
    }
    catch (IllegalArgumentException e) {
      throw new WebApplicationException(e.getMessage(), Response.Status.BAD_REQUEST);
    }

    Repository repository = repositoryAdapter.getRepository(repositoryName);
    if (repository == null) {
      throw new WebApplicationException("Repository not found: " + repositoryName, Response.Status.NOT_FOUND);
    }

    List<ComponentData> components = collect(repository);
    ObjectNode document = new SbomGenerator().generate(sbomFormat, repositoryName, components);
    byte[] bytes = MAPPER.writeValueAsBytes(document);

    return Response.ok(bytes, APPLICATION_JSON)
        .header("Content-Disposition", "attachment; filename=\"sbom-" + repositoryName + "-"
            + sbomFormat.getValue() + ".json\"")
        .build();
  }

  private List<ComponentData> collect(final Repository repository) {
    ContentFacet contentFacet = repository.optionalFacet(ContentFacet.class)
        .orElseThrow(() -> new WebApplicationException(
            "Repository '" + repository.getName() + "' has no content facet", Response.Status.BAD_REQUEST));

    String format = repository.getFormat().getValue();
    List<ComponentData> components = new ArrayList<>();

    String token = null;
    do {
      Continuation<FluentComponent> page = contentFacet.components().browse(PAGE_SIZE, token);
      for (FluentComponent component : page) {
        components.add(toComponentData(format, component));
        if (components.size() >= MAX_COMPONENTS) {
          log.warn("SBOM export truncated to {} components for repository '{}'", MAX_COMPONENTS, repository.getName());
          return components;
        }
      }
      token = nextToken(page);
    }
    while (token != null);

    return components;
  }

  private ComponentData toComponentData(final String format, final FluentComponent component) {
    String namespace = component.namespace();
    String license = readLicense(format, component);
    Map<String, String> checksums = new LinkedHashMap<>();
    List<String> dependencyNames = new ArrayList<>();

    for (FluentAsset asset : component.assets()) {
      asset.blob().ifPresent(blob -> {
        blob.checksums().forEach((algorithm, value) -> checksums.putIfAbsent(algorithm, value));
      });

      if ("npm".equals(format) && dependencyNames.isEmpty()) {
        dependencyNames.addAll(readNpmDependencies(asset, manifestPath(namespace, component.name(), component.version())));
      }
    }

    return new ComponentData(format, namespace, component.name(), component.version(), license, checksums,
        dependencyNames);
  }

  @Nullable
  private static String readLicense(final String format, final FluentComponent component) {
    try {
      Object value = component.attributes().child(format).get("license");
      return value instanceof String license ? license : null;
    }
    catch (Exception e) {
      return null;
    }
  }

  private static String manifestPath(@Nullable final String namespace, final String name, @Nullable final String version) {
    String packagePath = namespace == null || namespace.isEmpty() ? name : namespace + "/" + name;
    return "/" + packagePath + (version == null || version.isEmpty() ? "" : "/" + version);
  }

  /**
   * Reads the {@code dependencies} map of an npm version manifest asset when the given asset is that manifest.
   */
  private static List<String> readNpmDependencies(final FluentAsset asset, final String expectedManifestPath) {
    if (!expectedManifestPath.equals(asset.path()) || !asset.hasBlob()) {
      return List.of();
    }
    return asset.blob()
        .filter(blob -> blob.blobSize() <= MAX_MANIFEST_BYTES)
        .map(blob -> {
          try (InputStream input = asset.download().openInputStream()) {
            JsonNode manifest = MAPPER.readTree(input);
            JsonNode dependencies = manifest == null ? null : manifest.get("dependencies");
            if (dependencies != null && dependencies.isObject()) {
              List<String> names = new ArrayList<>();
              dependencies.fieldNames().forEachRemaining(names::add);
              return names;
            }
          }
          catch (Exception e) {
            // not a JSON manifest; ignore
          }
          return List.<String>of();
        })
        .orElse(List.of());
  }

  private static String nextToken(final Continuation<FluentComponent> page) {
    try {
      return page.nextContinuationToken();
    }
    catch (IllegalStateException e) {
      return null;
    }
  }
}
