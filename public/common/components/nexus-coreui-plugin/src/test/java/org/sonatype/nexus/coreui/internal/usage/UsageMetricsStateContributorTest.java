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

import org.sonatype.nexus.repository.search.SearchService;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class UsageMetricsStateContributorTest
{
  @Test
  @SuppressWarnings("unchecked")
  public void publishesComponentCountInState() {
    SearchService searchService = mock(SearchService.class);
    when(searchService.count(any())).thenReturn(42L);

    UsageMetricsStateContributor contributor = new UsageMetricsStateContributor(searchService);
    Map<String, Object> state = contributor.getState();
    List<Map<String, Object>> metrics = (List<Map<String, Object>>) state.get("contentUsageEvaluationResult");

    assertThat(metrics, hasSize(3));
    assertThat(metrics.get(0).get("metricName"), is("component_total_count"));
    assertThat(metrics.get(0).get("metricValue"), is(42L));
    verify(searchService).count(any());
  }
}
