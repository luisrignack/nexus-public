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
    String[] lines = index.split("\\R");
    String currentChart = null;
    String currentVersion = null;
    String currentUrl = null;

    for (String line : lines) {
      String trimmed = line.trim();
      if (trimmed.endsWith(":") && !line.startsWith(" ") && !line.startsWith("-")) {
        currentChart = trimmed.substring(0, trimmed.length() - 1);
        if (!"entries".equals(currentChart) && !currentChart.isEmpty()) {
          entries.putIfAbsent(currentChart, new LinkedHashMap<>());
        }
        continue;
      }
      if (trimmed.startsWith("- name:") && currentChart != null && entries.containsKey(currentChart)) {
        currentVersion = null;
        currentUrl = null;
        continue;
      }
      if (trimmed.startsWith("version:") && currentChart != null && entries.containsKey(currentChart)) {
        currentVersion = trimmed.substring("version:".length()).trim().replace("\"", "").replace("'", "");
        continue;
      }
      if (trimmed.startsWith("- ") && currentChart != null && entries.containsKey(currentChart) && trimmed.contains(".tgz")) {
        currentUrl = trimmed.substring(1).trim();
        if (currentVersion != null) {
          entries.get(currentChart).put(currentVersion, new ChartEntry(currentChart, currentVersion, currentUrl));
        }
        continue;
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
