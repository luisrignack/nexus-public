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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * REST model of the Docker proxy specific attributes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DockerProxyAttributes
{
  public static final String NAME = "dockerProxy";

  private final String indexType;

  private final String indexUrl;

  private final Boolean cacheForeignLayers;

  private final List<String> foreignLayerUrlWhitelist;

  @JsonCreator
  public DockerProxyAttributes(
      @JsonProperty("indexType") final String indexType,
      @JsonProperty("indexUrl") final String indexUrl,
      @JsonProperty("cacheForeignLayers") final Boolean cacheForeignLayers,
      @JsonProperty("foreignLayerUrlWhitelist") final List<String> foreignLayerUrlWhitelist)
  {
    this.indexType = indexType;
    this.indexUrl = indexUrl;
    this.cacheForeignLayers = cacheForeignLayers;
    this.foreignLayerUrlWhitelist = foreignLayerUrlWhitelist;
  }

  public String getIndexType() {
    return indexType;
  }

  public String getIndexUrl() {
    return indexUrl;
  }

  public Boolean getCacheForeignLayers() {
    return cacheForeignLayers;
  }

  public List<String> getForeignLayerUrlWhitelist() {
    return foreignLayerUrlWhitelist;
  }
}
