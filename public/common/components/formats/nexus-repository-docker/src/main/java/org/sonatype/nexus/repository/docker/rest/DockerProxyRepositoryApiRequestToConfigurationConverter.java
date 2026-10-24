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
package org.sonatype.nexus.repository.docker.rest;

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
 * Maps the Docker REST request to the repository configuration attributes consumed by
 * {@link org.sonatype.nexus.repository.docker.internal.DockerProxyFacet}.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DockerProxyRepositoryApiRequestToConfigurationConverter
    extends ProxyRepositoryApiRequestToConfigurationConverter<DockerProxyRepositoryApiRequest>
{
  @Inject
  public DockerProxyRepositoryApiRequestToConfigurationConverter(final RoutingRuleStore routingRuleStore) {
    super(routingRuleStore);
  }

  @Override
  public Configuration convert(final DockerProxyRepositoryApiRequest request) {
    Configuration configuration = super.convert(request);
    convertDocker(request.getDocker(), configuration);
    convertDockerProxy(request.getDockerProxy(), configuration);
    return configuration;
  }

  private static void convertDocker(final DockerAttributes docker, final Configuration configuration) {
    if (nonNull(docker)) {
      NestedAttributesMap attributes = configuration.attributes(DockerAttributes.NAME);
      attributes.set("httpPort", docker.getHttpPort());
      attributes.set("httpsPort", docker.getHttpsPort());
      attributes.set("forceBasicAuth", docker.getForceBasicAuth());
      attributes.set("v1Enabled", docker.getV1Enabled());
      attributes.set("subdomain", docker.getSubdomain());
    }
  }

  private static void convertDockerProxy(final DockerProxyAttributes dockerProxy, final Configuration configuration) {
    if (nonNull(dockerProxy)) {
      NestedAttributesMap attributes = configuration.attributes(DockerProxyAttributes.NAME);
      attributes.set("indexType", dockerProxy.getIndexType());
      attributes.set("indexUrl", dockerProxy.getIndexUrl());
      attributes.set("cacheForeignLayers", dockerProxy.getCacheForeignLayers());
      attributes.set("foreignLayerUrlWhitelist", dockerProxy.getForeignLayerUrlWhitelist());
    }
  }
}
