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
package org.sonatype.nexus.repository.pub.rest;

import org.sonatype.nexus.repository.rest.api.model.HostedStorageAttributes;

import jakarta.inject.Inject;

import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.pub.internal.PubFormat;
import org.sonatype.nexus.repository.rest.api.SimpleApiRepositoryAdapter;
import org.sonatype.nexus.repository.rest.api.model.AbstractApiRepository;
import org.sonatype.nexus.repository.routing.RoutingRuleStore;
import org.sonatype.nexus.repository.types.GroupType;
import org.sonatype.nexus.repository.types.HostedType;
import org.sonatype.nexus.repository.types.ProxyType;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@Qualifier(PubFormat.NAME)
public class PubRepositoryAdapter
    extends SimpleApiRepositoryAdapter
{
  @Inject
  public PubRepositoryAdapter(final RoutingRuleStore routingRuleStore) {
    super(routingRuleStore);
  }

  @Override
  public AbstractApiRepository adapt(final Repository repository) {
    if (HostedType.NAME.equals(repository.getType().toString())) {
      return new PubHostedApiRepository(
          repository.getName(),
          repository.getUrl(),
          repository.getConfiguration().isOnline(),
          getHostedStorageAttributes(repository),
          getCleanupPolicyAttributes(repository),
          getComponentAttributes(repository),
          new PubAttributes());
    }
    if (ProxyType.NAME.equals(repository.getType().toString())) {
      return new PubProxyApiRepository(
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
          new PubAttributes());
    }
    if (GroupType.NAME.equals(repository.getType().toString())) {
      return new PubGroupApiRepository(
          repository.getName(),
          repository.getUrl(),
          repository.getConfiguration().isOnline(),
          getHostedStorageAttributes(repository),
          getGroupAttributes(repository),
          new PubAttributes());
    }
    throw new IllegalArgumentException("Unsupported repository type: " + repository.getType());
  }
}
