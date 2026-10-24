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
package org.sonatype.nexus.content.helm.internal.recipe;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.sonatype.nexus.common.entity.Continuation;
import org.sonatype.nexus.repository.content.Asset;
import org.sonatype.nexus.repository.content.facet.ContentFacet;
import org.sonatype.nexus.repository.content.fluent.FluentAsset;
import org.sonatype.nexus.repository.content.fluent.FluentComponent;
import org.sonatype.nexus.repository.helm.HelmCoordinatesHelper;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * Builds the Helm repository index.yaml.
 */
public final class HelmIndexBuilder
{
  private static final int PAGE_SIZE = 100;

  private HelmIndexBuilder() {
  }

  /**
   * Builds the index from all chart assets in the repository.
   */
  public static byte[] buildIndex(final ContentFacet content) {
    Map<String, List<ChartEntry>> entries = new TreeMap<>();

    browseComponents(content, component -> {
      String name = component.name();
      if (name == null || name.isEmpty() || "_metadata".equals(name)) {
        return;
      }
      String version = component.version();
      if (version == null || version.isEmpty()) {
        return;
      }
      List<ChartEntry> list = entries.computeIfAbsent(name, k -> new ArrayList<>());
      for (FluentAsset asset : component.assets()) {
        if (asset.path().toLowerCase().endsWith(".tgz")) {
          list.add(new ChartEntry(name, version, asset.path()));
          break;
        }
      }
      if (list.isEmpty()) {
        for (FluentAsset asset : component.assets()) {
          list.add(new ChartEntry(name, version, asset.path()));
          break;
        }
      }
    });

    StringBuilder yaml = new StringBuilder();
    yaml.append("apiVersion: v1\n");
    yaml.append("generated: \"2024-01-01T00:00:00Z\"\n");
    yaml.append("entries:\n");
    for (Map.Entry<String, List<ChartEntry>> entry : entries.entrySet()) {
      yaml.append("  ").append(entry.getKey()).append(":\n");
      for (ChartEntry chart : entry.getValue()) {
        yaml.append("  - name: ").append(entry.getKey()).append("\n");
        yaml.append("    version: ").append(chart.version).append("\n");
        yaml.append("    urls:\n");
        yaml.append("      - ").append(chart.url).append("\n");
        yaml.append("    created: \"2024-01-01T00:00:00Z\"\n");
        yaml.append("    digest: \"sha256:0000000000000000000000000000000000000000000000000000000000000000\"\n");
      }
    }
    if (entries.isEmpty()) {
      yaml.append("{}\n");
    }
    return yaml.toString().getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Merges index.yaml documents from member repositories.
   */
  public static byte[] mergeIndexes(final List<String> indexes) {
    Map<String, Map<String, ChartEntry>> entries = new TreeMap<>();
    for (String index : indexes) {
      if (index == null) {
        continue;
      }
      parseIndex(index, entries);
    }

    StringBuilder yaml = new StringBuilder();
    yaml.append("apiVersion: v1\n");
    yaml.append("generated: \"2024-01-01T00:00:00Z\"\n");
    yaml.append("entries:\n");
    for (Map.Entry<String, Map<String, ChartEntry>> chartEntry : entries.entrySet()) {
      yaml.append("  ").append(chartEntry.getKey()).append(":\n");
      for (ChartEntry chart : chartEntry.getValue().values()) {
        yaml.append("  - name: ").append(chart.name).append("\n");
        yaml.append("    version: ").append(chart.version).append("\n");
        yaml.append("    urls:\n");
        yaml.append("      - ").append(chart.url).append("\n");
        yaml.append("    created: \"2024-01-01T00:00:00Z\"\n");
        yaml.append("    digest: \"sha256:0000000000000000000000000000000000000000000000000000000000000000\"\n");
      }
    }
    if (entries.isEmpty()) {
      yaml.append("{}\n");
    }
    return yaml.toString().getBytes(StandardCharsets.UTF_8);
  }

  private static void parseIndex(final String index, final Map<String, Map<String, ChartEntry>> entries) {
    Map<String, Object> root;
    try {
      // real-world registries ship large indexes (e.g. Bitnami's is ~27 MB), so the
      // SnakeYAML default code point limit (3 MB) must be lifted
      LoaderOptions loaderOptions = new LoaderOptions();
      loaderOptions.setCodePointLimit(Integer.MAX_VALUE);
      root = new Yaml(loaderOptions).load(index);
    }
    catch (Exception e) {
      // unparseable index contributes nothing to the merge
      return;
    }
    if (!(root instanceof Map)) {
      return;
    }
    Object entriesNode = ((Map<?, ?>) root).get("entries");
    if (!(entriesNode instanceof Map)) {
      return;
    }
    for (Map.Entry<?, ?> chartEntry : ((Map<?, ?>) entriesNode).entrySet()) {
      if (!(chartEntry.getValue() instanceof List)) {
        continue;
      }
      String chart = String.valueOf(chartEntry.getKey());
      for (Object versionNode : (List<?>) chartEntry.getValue()) {
        if (!(versionNode instanceof Map)) {
          continue;
        }
        Object version = ((Map<?, ?>) versionNode).get("version");
        if (version == null) {
          continue;
        }
        String v = String.valueOf(version);
        String url = "";
        if (((Map<?, ?>) versionNode).get("urls") instanceof List<?> urls && !urls.isEmpty()) {
          url = String.valueOf(urls.get(0));
        }
        entries.computeIfAbsent(chart, k -> new LinkedHashMap<>()).putIfAbsent(v, new ChartEntry(chart, v, url));
      }
    }
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

  private static class ChartEntry
  {
    final String name;

    final String version;

    final String url;

    ChartEntry(final String name, final String version, final String url) {
      this.name = name;
      this.version = version;
      this.url = url;
    }
  }
}
