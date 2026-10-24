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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import jakarta.inject.Singleton;

import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.common.entity.Continuation;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.content.facet.ContentFacet;
import org.sonatype.nexus.repository.content.fluent.FluentAsset;
import org.sonatype.nexus.repository.content.fluent.FluentComponent;
import org.sonatype.nexus.repository.content.fluent.FluentQuery;
import org.sonatype.nexus.repository.group.GroupFacet;
import org.sonatype.nexus.repository.npm.internal.NpmCoordinatesHelper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.io.ByteStreams;
import org.springframework.stereotype.Component;

/**
 * Implements the npm registry search endpoint ({@code GET /-/v1/search}) on top of the components and manifests
 * stored in a repository.
 *
 * <p>Search metadata comes from the component attributes written by {@link NpmComponentAttributes}; for content
 * published before those attributes existed the version manifest is parsed as a fallback. Group repositories query
 * their members as well as their own content.</p>
 */
@Component
@Singleton
public class NpmSearchHandler
    extends ComponentSupport
{
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final int PAGE_SIZE = 500;

  private static final int MAX_COMPONENTS = 10_000;

  private static final int DEFAULT_SIZE = 20;

  private static final int MAX_SIZE = 250;

  public ObjectNode search(final Repository repository, final String text, final int requestedSize, final int from) {
    int size = requestedSize <= 0 ? DEFAULT_SIZE : Math.min(requestedSize, MAX_SIZE);
    int offset = Math.max(from, 0);

    List<PackageInfo> packages = new ArrayList<>();
    Map<String, FluentComponent> newest = new LinkedHashMap<>();

    ContentFacet contentFacet = repository.facet(ContentFacet.class);
    FluentQuery<FluentComponent> query = repository.optionalFacet(GroupFacet.class).isPresent()
        ? contentFacet.components().withGroupMemberContent()
        : contentFacet.components();

    String token = null;
    int scanned = 0;
    do {
      Continuation<FluentComponent> page = query.browse(PAGE_SIZE, token);
      for (FluentComponent component : page) {
        if (scanned++ >= MAX_COMPONENTS) {
          break;
        }
        if (component.version() == null || component.version().isEmpty()) {
          continue;
        }
        if (isLegacyManifestComponent(component)) {
          continue;
        }
        String packagePath = packagePath(component);
        FluentComponent current = newest.get(packagePath);
        if (current == null || NpmCoordinatesHelper.compareVersions(component.version(), current.version()) > 0) {
          newest.put(packagePath, component);
        }
      }
      token = nextToken(page);
    }
    while (token != null && scanned < MAX_COMPONENTS);

    List<String> words = words(text);
    for (Map.Entry<String, FluentComponent> entry : newest.entrySet()) {
      PackageInfo info = describe(repository, entry.getKey(), entry.getValue());
      info.score = score(info, words);
      if (info.score > 0) {
        packages.add(info);
      }
    }

    packages.sort(Comparator.comparingDouble((PackageInfo info) -> info.score).reversed()
        .thenComparing(info -> info.name));

    ArrayNode objects = MAPPER.createArrayNode();
    int end = Math.min(offset + size, packages.size());
    for (int i = offset; i < end; i++) {
      objects.add(toObject(packages.get(i)));
    }

    ObjectNode response = MAPPER.createObjectNode();
    response.set("objects", objects);
    response.put("total", packages.size());
    response.put("time", DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC)));
    return response;
  }

  private PackageInfo describe(
      final Repository repository,
      final String packagePath,
      final FluentComponent component)
  {
    PackageInfo info = new PackageInfo();
    info.name = packagePath;
    info.version = component.version();
    info.date = component.created();
    info.scope = packagePath.startsWith("@") && packagePath.contains("/")
        ? packagePath.substring(0, packagePath.indexOf('/'))
        : "unscoped";

    ContentFacet contentFacet = repository.facet(ContentFacet.class);
    Optional<FluentAsset> manifestAsset = contentFacet.assets()
        .path("/" + packagePath + "/" + component.version())
        .find();

    manifestAsset.ifPresent(asset -> {
      readAttribute(asset, "npm.description", value -> info.description = value);
      readAttribute(asset, "npm.author", value -> info.author = value);
      readAttribute(asset, "npm.homepage", value -> info.homepage = value);
      readAttribute(asset, "npm.repository", value -> info.repository = value);
      Object keywords = asset.attributes().get("npm.keywords");
      if (keywords instanceof Iterable) {
        for (Object keyword : (Iterable<?>) keywords) {
          if (keyword != null) {
            info.keywords.add(keyword.toString());
          }
        }
      }
    });

    JsonNode manifest = readManifest(manifestAsset);
    if (manifest != null) {
      if (info.description == null) {
        info.description = text(manifest, "description");
      }
      if (info.author == null) {
        JsonNode author = manifest.get("author");
        if (author != null && author.isObject()) {
          info.author = text(author, "name");
        }
        else if (author != null && author.isTextual()) {
          info.author = author.asText();
        }
      }
      if (info.homepage == null) {
        info.homepage = text(manifest, "homepage");
      }
      if (info.repository == null) {
        JsonNode repositoryNode = manifest.get("repository");
        if (repositoryNode != null && repositoryNode.isObject()) {
          info.repository = text(repositoryNode, "url");
        }
        else if (repositoryNode != null && repositoryNode.isTextual()) {
          info.repository = repositoryNode.asText();
        }
      }
      if (info.keywords.isEmpty()) {
        JsonNode keywords = manifest.get("keywords");
        if (keywords != null && keywords.isArray()) {
          keywords.forEach(keyword -> {
            if (keyword.isTextual()) {
              info.keywords.add(keyword.asText());
            }
          });
        }
      }
      JsonNode maintainers = manifest.get("maintainers");
      if (maintainers != null && maintainers.isArray()) {
        for (JsonNode maintainer : maintainers) {
          if (maintainer.isObject()) {
            info.maintainers.add(text(maintainer, "name"));
          }
          else if (maintainer.isTextual()) {
            info.maintainers.add(maintainer.asText());
          }
        }
      }
    }

    info.packageUrl = repository.getUrl() + "/" + packagePath;
    return info;
  }

  private static JsonNode readManifest(final Optional<FluentAsset> manifestAsset) {
    if (!manifestAsset.isPresent()) {
      return null;
    }
    try {
      byte[] bytes = ByteStreams.toByteArray(manifestAsset.get().download().openInputStream());
      JsonNode document = MAPPER.readTree(bytes);
      return document != null && document.isObject() ? document : null;
    }
    catch (Exception e) {
      return null;
    }
  }

  private static double score(final PackageInfo info, final List<String> words) {
    if (words.isEmpty()) {
      return 0;
    }

    String name = info.name.toLowerCase(Locale.ROOT);
    String shortName = info.name.startsWith("@") && info.name.contains("/")
        ? info.name.substring(info.name.indexOf('/') + 1).toLowerCase(Locale.ROOT)
        : name;
    String haystack = (info.name + ' ' + nullToEmpty(info.description) + ' ' + String.join(" ", info.keywords)
        + ' ' + nullToEmpty(info.author)).toLowerCase(Locale.ROOT);

    boolean nameMatches = true;
    boolean anyMatches = true;
    for (String word : words) {
      if (!name.contains(word) && !shortName.contains(word)) {
        nameMatches = false;
      }
      if (!haystack.contains(word)) {
        anyMatches = false;
      }
    }

    if (nameMatches) {
      String joined = String.join(" ", words);
      if (name.equals(joined) || shortName.equals(joined)) {
        return 1.0;
      }
      if (name.startsWith(joined) || shortName.startsWith(joined)) {
        return 0.9;
      }
      return 0.8;
    }
    return anyMatches ? 0.5 : 0;
  }

  private static ObjectNode toObject(final PackageInfo info) {
    ObjectNode object = MAPPER.createObjectNode();

    ObjectNode pkg = object.putObject("package");
    pkg.put("name", info.name);
    pkg.put("scope", info.scope);
    pkg.put("version", info.version);
    if (info.description != null) {
      pkg.put("description", info.description);
    }
    if (!info.keywords.isEmpty()) {
      ArrayNode keywords = pkg.putArray("keywords");
      info.keywords.forEach(keywords::add);
    }
    if (info.date != null) {
      pkg.put("date", info.date.toString());
    }

    ObjectNode links = pkg.putObject("links");
    links.put("npm", info.packageUrl);
    if (info.homepage != null) {
      links.put("homepage", info.homepage);
    }
    if (info.repository != null) {
      links.put("repository", info.repository);
    }

    ObjectNode publisher = pkg.putObject("publisher");
    publisher.put("username", info.author != null ? info.author : "nexus");

    ArrayNode maintainers = pkg.putArray("maintainers");
    if (info.maintainers.isEmpty()) {
      maintainers.addObject().put("username", "nexus");
    }
    else {
      info.maintainers.forEach(name -> maintainers.addObject().put("username", name));
    }

    ObjectNode score = object.putObject("score");
    score.put("final", info.score);
    ObjectNode detail = score.putObject("detail");
    detail.put("quality", info.score);
    detail.put("popularity", info.score);
    detail.put("maintenance", info.score);

    object.put("searchScore", info.score);
    return object;
  }

  private static void readAttribute(
      final FluentAsset asset,
      final String key,
      final java.util.function.Consumer<String> consumer)
  {
    Object value = asset.attributes().get(key);
    if (value != null) {
      consumer.accept(value.toString());
    }
  }

  private static String packagePath(final FluentComponent component) {
    String name = component.name();
    String namespace = component.namespace();
    if (NpmCoordinatesHelper.isVersion(name) && namespace != null && !namespace.isEmpty()
        && !(namespace.startsWith("@") && namespace.indexOf('/') < 0)) {
      // Legacy data: a version manifest used to be stored as its own component (namespace=package, name=version).
      return namespace;
    }
    return namespace == null || namespace.isEmpty() ? name : namespace + "/" + name;
  }

  /**
   * Old versions of the format stored a version manifest as its own component, using the version as component name
   * and no namespace.
   */
  private static boolean isLegacyManifestComponent(final FluentComponent component) {
    String name = component.name();
    String namespace = component.namespace();
    return NpmCoordinatesHelper.isVersion(name) && (namespace == null || namespace.isEmpty());
  }

  /**
   * Continuation tokens are only available while there are more results; the implementation throws when the
   * current page was the last one.
   */
  private static String nextToken(final Continuation<FluentComponent> page) {
    try {
      return page.nextContinuationToken();
    }
    catch (IllegalStateException e) {
      return null;
    }
  }

  private static String text(final JsonNode node, final String field) {
    JsonNode value = node != null ? node.get(field) : null;
    return value != null && value.isTextual() ? value.asText() : null;
  }

  private static List<String> words(final String text) {
    List<String> words = new ArrayList<>();
    if (text != null) {
      for (String word : text.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
        if (!word.isEmpty() && !words.contains(word)) {
          words.add(word);
        }
      }
    }
    return words;
  }

  private static String nullToEmpty(final String value) {
    return value != null ? value : "";
  }

  private static final class PackageInfo
  {
    private String name;

    private String scope;

    private String version;

    private String description;

    private String author;

    private String homepage;

    private String repository;

    private String packageUrl;

    private OffsetDateTime date;

    private final List<String> keywords = new ArrayList<>();

    private final List<String> maintainers = new ArrayList<>();

    private double score;
  }
}
