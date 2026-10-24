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

import java.util.Collections;
import java.util.List;

import jakarta.inject.Inject;

import org.sonatype.nexus.common.collect.NestedAttributesMap;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.docker.internal.DockerFormat;
import org.sonatype.nexus.repository.rest.api.SimpleApiRepositoryAdapter;
import org.sonatype.nexus.repository.rest.api.model.AbstractApiRepository;
import org.sonatype.nexus.repository.routing.RoutingRuleStore;
import org.sonatype.nexus.repository.types.ProxyType;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Adapts Docker repositories to the REST API model.
 */
@Component
@Qualifier(DockerFormat.NAME)
public class DockerRepositoryAdapter
    extends SimpleApiRepositoryAdapter
{
  @Inject
  public DockerRepositoryAdapter(final RoutingRuleStore routingRuleStore) {
    super(routingRuleStore);
  }

  @Override
  public AbstractApiRepository adapt(final Repository repository) {
    if (ProxyType.NAME.equals(repository.getType().toString())) {
      return new DockerProxyApiRepository(
          repository.getName(),
          repository.getUrl(),
          repository.getConfiguration().isOnline(),
          getHostedStorageAttributes(repository),
          getCleanupPolicyAttributes(repository),
          getProxyAttributes(repository),
          getNegativeCacheAttributes(repository),
          getHttpClientAttributes(repository),
          getRoutingRuleName(repository),
          getReplicationAttributes(repository),
          dockerAttributes(repository),
          dockerProxyAttributes(repository));
    }
    throw new IllegalArgumentException("Unsupported repository type: " + repository.getType());
  }

  private static DockerAttributes dockerAttributes(final Repository repository) {
    NestedAttributesMap attributes = repository.getConfiguration().attributes(DockerAttributes.NAME);
    return new DockerAttributes(
        attributes.get("httpPort", Integer.class),
        attributes.get("httpsPort", Integer.class),
        attributes.get("forceBasicAuth", Boolean.class),
        attributes.get("v1Enabled", Boolean.class),
        attributes.get("subdomain", String.class));
  }

  @SuppressWarnings("unchecked")
  private static DockerProxyAttributes dockerProxyAttributes(final Repository repository) {
    NestedAttributesMap attributes = repository.getConfiguration().attributes(DockerProxyAttributes.NAME);
    List<String> whitelist = attributes.get("foreignLayerUrlWhitelist", List.class);
    return new DockerProxyAttributes(
        attributes.get("indexType", String.class),
        attributes.get("indexUrl", String.class),
        attributes.get("cacheForeignLayers", Boolean.class),
        whitelist != null ? whitelist : Collections.emptyList());
  }
}
