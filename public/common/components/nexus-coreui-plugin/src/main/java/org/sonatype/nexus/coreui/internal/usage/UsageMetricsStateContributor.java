/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 */
package org.sonatype.nexus.coreui.internal.usage;

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.sonatype.nexus.rapture.StateContributor;
import org.sonatype.nexus.repository.search.SearchRequest;
import org.sonatype.nexus.repository.search.SearchService;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Publishes the {@code contentUsageEvaluationResult} state consumed by the Nexus One dashboard.
 *
 * <p>That state is normally produced by a Pro-only content usage evaluator. This fork has no such
 * capability, so without a contributor the "Components" card waits 15 seconds and then shows
 * "Couldn't load metrics". Here it is backed by the real component count from the search index.</p>
 */
@Component
@Singleton
public class UsageMetricsStateContributor
    implements StateContributor
{
  private static final Logger log = LoggerFactory.getLogger(UsageMetricsStateContributor.class);

  private static final String STATE_KEY = "contentUsageEvaluationResult";

  private static final long CACHE_TTL_MS = 30_000;

  private final SearchService searchService;

  private volatile CacheEntry cache = new CacheEntry(0, -1);

  @Inject
  public UsageMetricsStateContributor(final SearchService searchService) {
    this.searchService = checkNotNull(searchService);
  }

  @Override
  public Map<String, Object> getState() {
    return ImmutableMap.of(STATE_KEY, metrics());
  }

  private List<Map<String, Object>> metrics() {
    long count = componentCount();
    return ImmutableList.of(
        metric("component_total_count", count),
        metric("peak_requests_per_day", 0),
        metric("peak_requests_per_day_30d", 0));
  }

  private long componentCount() {
    long now = System.currentTimeMillis();
    CacheEntry current = cache;
    if (current.timestamp > 0 && now - current.timestamp < CACHE_TTL_MS) {
      return current.count;
    }
    try {
      long count = searchService.count(SearchRequest.builder().disableAuthorization().build());
      cache = new CacheEntry(count, now);
      return count;
    }
    catch (Exception e) {
      log.debug("Unable to count components for dashboard metrics", e);
      return current.count > 0 ? current.count : 0;
    }
  }

  private static Map<String, Object> metric(final String name, final long value) {
    return ImmutableMap.of(
        "metricName", name,
        "metricValue", value,
        "thresholds", ImmutableList.of());
  }

  private static final class CacheEntry
  {
    private final long count;

    private final long timestamp;

    private CacheEntry(final long count, final long timestamp) {
      this.count = count;
      this.timestamp = timestamp;
    }
  }
}
