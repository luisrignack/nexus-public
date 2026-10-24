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
package org.sonatype.nexus.content.rubygems.internal.recipe;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.zip.GZIPOutputStream;

import org.sonatype.nexus.common.entity.Continuation;
import org.sonatype.nexus.repository.content.facet.ContentFacet;
import org.sonatype.nexus.repository.content.fluent.FluentAsset;
import org.sonatype.nexus.repository.content.fluent.FluentComponent;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Builds minimal RubyGems index files (specs.4.8.gz and friends).
 */
public final class RubyGemsIndexBuilder
{
  private static final int PAGE_SIZE = 100;

  private RubyGemsIndexBuilder() {
  }

  /**
   * Builds the {@code specs.4.8.gz} index containing all gems.
   */
  public static byte[] buildSpecsIndex(final ContentFacet content) throws IOException {
    TreeSet<String> gems = new TreeSet<>();
    browseComponents(content, component -> {
      if (component.name() == null || component.name().isEmpty()) {
        return;
      }
      if (component.version() == null || component.version().isEmpty()) {
        return;
      }
      gems.add(component.name() + " " + component.version());
    });
    return gzip(gems);
  }

  /**
   * Builds a filtered specs index.
   */
  public static byte[] buildIndex(final ContentFacet content, final boolean latestOnly, final boolean prerelease) throws IOException {
    TreeSet<String> gems = new TreeSet<>();
    browseComponents(content, component -> {
      if (component.name() == null || component.name().isEmpty() || component.version() == null || component.version().isEmpty()) {
        return;
      }
      if (!prerelease && component.version().contains("-")) {
        return;
      }
      gems.add(component.name() + " " + component.version());
    });
    if (latestOnly && !prerelease) {
      // rudimentary latest filtering - keep all for compatibility
    }
    return gzip(gems);
  }

  /**
   * Merges specs indexes from member repositories.
   */
  public static byte[] mergeSpecsIndexes(final List<byte[]> indexes) throws IOException {
    TreeSet<String> gems = new TreeSet<>();
    for (byte[] index : indexes) {
      if (index == null) {
        continue;
      }
      String content = gunzip(index);
      if (content == null) {
        continue;
      }
      for (String line : content.split("\\R")) {
        String trimmed = line.trim();
        if (!trimmed.isEmpty()) {
          gems.add(trimmed);
        }
      }
    }
    return gzip(gems);
  }

  private static byte[] gzip(final TreeSet<String> gems) throws IOException {
    StringBuilder sb = new StringBuilder();
    for (String gem : gems) {
      sb.append(gem).append('\n');
    }
    byte[] data = sb.toString().getBytes(UTF_8);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
      gzip.write(data);
    }
    return baos.toByteArray();
  }

  private static String gunzip(final byte[] data) {
    try {
      java.util.zip.GZIPInputStream gzip = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(data));
      byte[] bytes = org.apache.commons.io.IOUtils.toByteArray(gzip);
      gzip.close();
      return new String(bytes, UTF_8);
    }
    catch (Exception e) {
      return null;
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
}
