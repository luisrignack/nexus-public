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
package org.sonatype.nexus.repository.apt.datastore.internal.data;

import java.util.stream.Stream;

import org.sonatype.nexus.common.entity.Continuations;
import org.sonatype.nexus.repository.Facet.Exposed;
import org.sonatype.nexus.repository.apt.AptFormat;
import org.sonatype.nexus.repository.apt.datastore.data.AptKeyValueDAO;
import org.sonatype.nexus.repository.apt.datastore.data.AptKeyValueStore;
import org.sonatype.nexus.repository.content.kv.KeyValue;
import org.sonatype.nexus.repository.content.kv.KeyValueFacetSupport;
import org.sonatype.nexus.repository.content.store.FormatStoreManager;

import jakarta.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import static com.google.common.base.Preconditions.checkArgument;

@Component
@Qualifier(AptFormat.NAME)
@Exposed
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class AptKeyValueFacet
    extends KeyValueFacetSupport<AptKeyValueDAO, AptKeyValueStore>
{
  private final int limit;

  private final static String CATEGORY = StringUtils.EMPTY;

  @Inject
  public AptKeyValueFacet(
      @Qualifier(AptFormat.NAME) FormatStoreManager formatStoreManager,
      @Value("${nexus.apt.paging.size:100}") final int limit)
  {
    super(formatStoreManager, AptKeyValueDAO.class);
    checkArgument(limit > 0);
    this.limit = limit;
  }

  /**
   * Store AptDeb metadata
   *
   * @param assetId the assetId
   * @param componentId the componentId
   * @param metadata the json of an AptDeb metadata
   */
  public void addPackageMetadata(final int componentId, final int assetId, final String metadata) {
    set(CATEGORY, aptKey(componentId, assetId), metadata);
  }

  /**
   * Remove the AptDeb metadata.
   *
   * @param assetId the assetId
   * @param componentId the componentId
   */
  public void removePackageMetadata(final int componentId, final int assetId) {
    remove(CATEGORY, aptKey(componentId, assetId));
  }

  /**
   * Remove all AptDeb metadata.
   */
  public void removeAllPackageMetadata() {
    removeAll(CATEGORY);
  }

  /**
   * Brows AptDeb metadata backed by key-value object
   *
   * @return a stream of value objects representing AptDeb metadata as String
   */
  public Stream<String> browsePackagesMetadata() {
    return Continuations
        .streamOf((browseLimit, continuationToken) -> browseValues(CATEGORY, browseLimit, continuationToken), limit)
        .map(KeyValue::getValue);
  }

  /*
   * Creates a key for componentId. This should only be used for storing AptDeb JSON.
   * Other use cases should avoid overlapping this key structure.
   */
  private String aptKey(final int componentId, final int assetId) {
    return "apt-" + componentId + '-' + assetId;
  }
}
