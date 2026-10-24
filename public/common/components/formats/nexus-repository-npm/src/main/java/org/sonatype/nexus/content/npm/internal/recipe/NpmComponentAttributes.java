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
package org.sonatype.nexus.content.npm.internal.recipe;

import java.util.ArrayList;
import java.util.List;

import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.content.facet.ContentFacet;
import org.sonatype.nexus.repository.content.fluent.FluentAttributes;
import org.sonatype.nexus.repository.npm.internal.NpmCoordinatesHelper;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Copies the metadata of an npm version manifest into component and asset attributes.
 *
 * <p>Attributes are stored under the {@code npm.} prefix ({@code npm.description}, {@code npm.keywords},
 * {@code npm.author}, {@code npm.license}, {@code npm.homepage} and {@code npm.repository}). They are written to
 * both the component and its manifest asset: the component attributes feed {@code /-/v1/search}, while the asset
 * attributes are the ones exposed by the browse/search UI (for example
 * {@code assets.attributes.npm.description}).</p>
 */
public final class NpmComponentAttributes
{
  public static final String DESCRIPTION = "npm.description";

  public static final String KEYWORDS = "npm.keywords";

  public static final String AUTHOR = "npm.author";

  public static final String LICENSE = "npm.license";

  public static final String HOMEPAGE = "npm.homepage";

  public static final String REPOSITORY = "npm.repository";

  private NpmComponentAttributes() {
  }

  /**
   * Applies the manifest metadata to the component and manifest asset for the given version.
   */
  public static void apply(
      final Repository repository,
      final String packagePath,
      final String version,
      final JsonNode manifest)
  {
    if (manifest == null || !manifest.isObject()) {
      return;
    }

    String namespace = NpmCoordinatesHelper.getComponentGroup(packagePath);
    String name = NpmCoordinatesHelper.getComponentName(packagePath);
    ContentFacet contentFacet = repository.facet(ContentFacet.class);

    contentFacet.components()
        .name(name)
        .namespace(namespace)
        .version(version)
        .find()
        .ifPresent(component -> apply(component, manifest));

    contentFacet.assets()
        .path("/" + packagePath + "/" + version)
        .find()
        .ifPresent(asset -> apply(asset, manifest));
  }

  private static void apply(final FluentAttributes<?> target, final JsonNode manifest) {
    String description = text(manifest, "description");
    if (description != null) {
      target.withAttribute(DESCRIPTION, description);
    }

    String author = author(manifest);
    if (author != null) {
      target.withAttribute(AUTHOR, author);
    }

    String license = license(manifest);
    if (license != null) {
      target.withAttribute(LICENSE, license);
    }

    String homepage = text(manifest, "homepage");
    if (homepage != null) {
      target.withAttribute(HOMEPAGE, homepage);
    }

    String repository = repository(manifest);
    if (repository != null) {
      target.withAttribute(REPOSITORY, repository);
    }

    List<String> keywords = keywords(manifest);
    if (!keywords.isEmpty()) {
      target.withAttribute(KEYWORDS, keywords);
    }
  }

  private static String author(final JsonNode manifest) {
    JsonNode author = manifest.get("author");
    if (author == null) {
      return null;
    }
    if (author.isTextual()) {
      return author.asText();
    }
    if (author.isObject()) {
      return text(author, "name");
    }
    return null;
  }

  private static String license(final JsonNode manifest) {
    JsonNode license = manifest.get("license");
    if (license == null) {
      return null;
    }
    if (license.isTextual()) {
      return license.asText();
    }
    if (license.isObject()) {
      return text(license, "type");
    }
    return null;
  }

  private static String repository(final JsonNode manifest) {
    JsonNode repository = manifest.get("repository");
    if (repository == null) {
      return null;
    }
    if (repository.isTextual()) {
      return repository.asText();
    }
    if (repository.isObject()) {
      return text(repository, "url");
    }
    return null;
  }

  private static List<String> keywords(final JsonNode manifest) {
    List<String> keywords = new ArrayList<>();
    JsonNode node = manifest.get("keywords");
    if (node != null && node.isArray()) {
      for (JsonNode keyword : node) {
        if (keyword.isTextual() && !keyword.asText().isEmpty()) {
          keywords.add(keyword.asText());
        }
      }
    }
    return keywords;
  }

  private static String text(final JsonNode node, final String field) {
    JsonNode value = node.get(field);
    return value != null && value.isTextual() && !value.asText().isEmpty() ? value.asText() : null;
  }
}
