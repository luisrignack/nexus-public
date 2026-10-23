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
package org.sonatype.nexus.content.pypi.internal.recipe;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sonatype.nexus.common.entity.Continuation;
import org.sonatype.nexus.common.hash.HashAlgorithm;
import org.sonatype.nexus.repository.content.AssetBlob;
import org.sonatype.nexus.repository.content.facet.ContentFacet;
import org.sonatype.nexus.repository.content.fluent.FluentAsset;
import org.sonatype.nexus.repository.content.fluent.FluentComponent;
import org.sonatype.nexus.repository.pypi.PyPiCoordinatesHelper;

import static org.sonatype.nexus.content.pypi.PyPiContentFacet.REQUIRES_PYTHON_ATTRIBUTE;
import static org.sonatype.nexus.repository.pypi.PyPiCoordinatesHelper.escapeHtml;

/**
 * Builds and rewrites the HTML "simple" indexes defined by PEP 503.
 */
public final class PyPiIndexBuilder
{
  private static final int PAGE_SIZE = 100;

  private static final Pattern ANCHOR = Pattern.compile(
      "<a\\s+[^>]*href\\s*=\\s*[\"']([^\"']*)[\"'][^>]*>.*?</a>",
      Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

  private static final Pattern HREF = Pattern.compile("(?i)href\\s*=\\s*([\"'])(.*?)\\1");

  private PyPiIndexBuilder() {
  }

  /**
   * Builds the root {@code /simple/} index from the components of a repository.
   */
  public static byte[] buildRootIndex(final ContentFacet content) {
    Set<String> projects = new TreeSet<>();
    browseComponents(content, component -> projects.add(component.name()));

    StringBuilder html = new StringBuilder();
    html.append("<!DOCTYPE html>\n<html><head><title>Simple index</title></head><body>\n");
    for (String project : projects) {
      String normalized = PyPiCoordinatesHelper.normalizeProjectName(project);
      if (normalized.isEmpty()) {
        continue;
      }
      html.append("<a href=\"").append(escapeHtml(normalized)).append("/\">")
          .append(escapeHtml(normalized)).append("</a><br/>\n");
    }
    html.append("</body></html>\n");
    return html.toString().getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Builds the {@code /simple/{project}/} index from the assets of a repository.
   */
  public static byte[] buildProjectIndex(final ContentFacet content, final String project) {
    String normalized = PyPiCoordinatesHelper.normalizeProjectName(project);
    Map<String, String> links = new LinkedHashMap<>();

    browseComponents(content, component -> {
      if (!normalized.equals(component.name())) {
        return;
      }
      for (FluentAsset asset : component.assets()) {
        String path = asset.path();
        if (!path.startsWith(PyPiCoordinatesHelper.PACKAGES_PREFIX + "/")) {
          continue;
        }
        String fileName = PyPiCoordinatesHelper.fileName(path);
        links.putIfAbsent(fileName, projectLink(asset, fileName));
      }
    });

    StringBuilder html = new StringBuilder();
    html.append("<!DOCTYPE html>\n<html><head><title>Links for ").append(escapeHtml(normalized))
        .append("</title></head><body>\n<h1>Links for ").append(escapeHtml(normalized)).append("</h1>\n");
    links.values().forEach(html::append);
    html.append("</body></html>\n");
    return html.toString().getBytes(StandardCharsets.UTF_8);
  }

  private static String projectLink(final FluentAsset asset, final String fileName) {
    StringBuilder link = new StringBuilder();
    link.append("<a href=\"../../packages/").append(escapeHtml(fileName));
    Optional<String> sha256 = asset.blob()
        .map(AssetBlob::checksums)
        .map(checksums -> checksums.get(HashAlgorithm.SHA256.name()))
        .filter(value -> value != null && !value.isEmpty());
    sha256.ifPresent(value -> link.append("#sha256=").append(value));
    link.append('"');

    Object requiresPython = asset.attributes().get(REQUIRES_PYTHON_ATTRIBUTE);
    if (requiresPython != null && !String.valueOf(requiresPython).isEmpty()) {
      link.append(" data-requires-python=\"").append(escapeHtml(String.valueOf(requiresPython))).append('"');
    }
    link.append('>').append(escapeHtml(fileName)).append("</a><br/>\n");
    return link.toString();
  }

  /**
   * Rewrites the links of an upstream root index so they point at local, normalized project paths.
   */
  public static byte[] rewriteRootIndex(final String html) {
    Set<String> projects = new TreeSet<>();
    Matcher matcher = ANCHOR.matcher(html);
    while (matcher.find()) {
      String project = projectFromHref(matcher.group(1));
      if (!project.isEmpty()) {
        projects.add(PyPiCoordinatesHelper.normalizeProjectName(project));
      }
    }

    StringBuilder result = new StringBuilder();
    result.append("<!DOCTYPE html>\n<html><head><title>Simple index</title></head><body>\n");
    for (String project : projects) {
      result.append("<a href=\"").append(escapeHtml(project)).append("/\">")
          .append(escapeHtml(project)).append("</a><br/>\n");
    }
    result.append("</body></html>\n");
    return result.toString().getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Rewrites the links of an upstream project index so the files are downloaded through this repository.
   *
   * @param baseUri absolute URL of the upstream index used to resolve relative links
   */
  public static byte[] rewriteProjectIndex(final String html, final URI baseUri) {
    List<String> links = new ArrayList<>();
    Matcher matcher = ANCHOR.matcher(html);
    while (matcher.find()) {
      String anchor = matcher.group();
      String href = matcher.group(1);
      String resolved = resolve(baseUri, href);
      if (resolved == null) {
        continue;
      }
      String fileName = PyPiCoordinatesHelper.fileName(resolved);
      if (fileName.isEmpty()) {
        continue;
      }
      String fragment = fragment(href);
      String token = PyPiCoordinatesHelper.encodeUpstreamUrl(stripFragment(resolved));
      String replacement = "../../packages/" + escapeHtml(token) + "/" + escapeHtml(fileName)
          + (fragment.isEmpty() ? "" : escapeHtml(fragment));
      links.add(replaceHref(anchor, replacement));
    }

    StringBuilder result = new StringBuilder();
    result.append("<!DOCTYPE html>\n<html><head><title>Links</title></head><body>\n");
    links.forEach(result::append);
    result.append("</body></html>\n");
    return result.toString().getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Extracts the anchor tags from an index page, preserving their order and removing duplicates.
   */
  public static List<String> extractAnchors(final String html) {
    List<String> anchors = new ArrayList<>();
    Set<String> seen = new TreeSet<>();
    Matcher matcher = ANCHOR.matcher(html);
    while (matcher.find()) {
      String anchor = matcher.group();
      String href = matcher.group(1);
      if (seen.add(href)) {
        anchors.add(anchor);
      }
    }
    return anchors;
  }

  /**
   * Builds a merged index from the anchors of several member repositories.
   */
  public static byte[] mergeAnchors(final List<String> anchors) {
    StringBuilder result = new StringBuilder();
    result.append("<!DOCTYPE html>\n<html><head><title>Simple index</title></head><body>\n");
    anchors.forEach(anchor -> result.append(anchor).append('\n'));
    result.append("</body></html>\n");
    return result.toString().getBytes(StandardCharsets.UTF_8);
  }

  private static String projectFromHref(final String href) {
    String value = stripFragment(href.trim());
    int query = value.indexOf('?');
    if (query >= 0) {
      value = value.substring(0, query);
    }
    value = PyPiCoordinatesHelper.stripTrailingSlash(value);
    int slash = value.lastIndexOf('/');
    return PyPiCoordinatesHelper.decode(slash >= 0 ? value.substring(slash + 1) : value);
  }

  private static String resolve(final URI baseUri, final String href) {
    try {
      URI uri = baseUri.resolve(href.trim());
      return uri.isAbsolute() ? uri.toString() : null;
    }
    catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static String replaceHref(final String anchor, final String newHref) {
    Matcher matcher = HREF.matcher(anchor);
    if (!matcher.find()) {
      return anchor;
    }
    return anchor.substring(0, matcher.start(2)) + newHref + anchor.substring(matcher.end(2));
  }

  private static String stripFragment(final String value) {
    int index = value.indexOf('#');
    return index >= 0 ? value.substring(0, index) : value;
  }

  private static String fragment(final String value) {
    int index = value.indexOf('#');
    return index >= 0 ? value.substring(index) : "";
  }

  private static void browseComponents(final ContentFacet content, final ComponentConsumer consumer) {
    String continuationToken = null;
    do {
      Continuation<FluentComponent> page = content.components().browseWithAssets(PAGE_SIZE, continuationToken);
      page.forEach(consumer::accept);
      if (page.isEmpty()) {
        break;
      }
      continuationToken = page.nextContinuationToken();
    }
    while (continuationToken != null);
  }

  private interface ComponentConsumer
  {
    void accept(FluentComponent component);
  }
}
