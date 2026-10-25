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
package org.sonatype.nexus.repository.nuget.rest;

import jakarta.inject.Inject;

import org.sonatype.nexus.common.collect.NestedAttributesMap;
import org.sonatype.nexus.repository.config.Configuration;
import org.sonatype.nexus.repository.rest.api.ProxyRepositoryApiRequestToConfigurationConverter;
import org.sonatype.nexus.repository.routing.RoutingRuleStore;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import static java.util.Objects.nonNull;

/**
 * Maps the NuGet REST request to the {@code nugetProxy} configuration block consumed by
 * {@link org.sonatype.nexus.content.nuget.internal.recipe.NugetProxyFacet}.
 *
 * <p>Field defaults follow the repository creation wizard: {@code nugetVersion} defaults to
 * {@code V3} and {@code queryCacheItemMaxAge} (minutes) to {@code 3600}.</p>
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class NugetProxyRepositoryApiRequestToConfigurationConverter
    extends ProxyRepositoryApiRequestToConfigurationConverter<NugetProxyRepositoryApiRequest>
{
  @Inject
  public NugetProxyRepositoryApiRequestToConfigurationConverter(final RoutingRuleStore routingRuleStore) {
    super(routingRuleStore);
  }

  @Override
  public Configuration convert(final NugetProxyRepositoryApiRequest request) {
    Configuration configuration = super.convert(request);
    convertNugetProxy(request.getNugetProxy(), configuration);
    return configuration;
  }

  private static void convertNugetProxy(final NugetAttributes nugetProxy, final Configuration configuration) {
    if (nonNull(nugetProxy)) {
      NestedAttributesMap attributes = configuration.attributes(NugetAttributes.PROXY_NAME);
      attributes.set(NugetAttributes.NUGET_VERSION,
          nonNull(nugetProxy.getNugetVersion()) ? nugetProxy.getNugetVersion() : NugetAttributes.V3);
      attributes.set(NugetAttributes.QUERY_CACHE_ITEM_MAX_AGE,
          nonNull(nugetProxy.getQueryCacheItemMaxAge())
              ? nugetProxy.getQueryCacheItemMaxAge()
              : NugetAttributes.DEFAULT_QUERY_CACHE_ITEM_MAX_AGE);
    }
  }
}
