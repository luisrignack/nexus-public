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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * REST API model of the NuGet format specific attributes.
 *
 * <p>For proxy repositories these values are stored under the {@code nugetProxy} configuration block
 * (as sent by the repository creation wizard), while {@link #NAME} is the format name used by the
 * repositories API.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class NugetAttributes
{
  public static final String NAME = "nuget";

  /**
   * Configuration attribute block holding the NuGet proxy specific settings.
   */
  public static final String PROXY_NAME = "nugetProxy";

  public static final String NUGET_VERSION = "nugetVersion";

  public static final String QUERY_CACHE_ITEM_MAX_AGE = "queryCacheItemMaxAge";

  /**
   * NuGet v3 protocol (default).
   */
  public static final String V3 = "V3";

  /**
   * NuGet v2 protocol.
   */
  public static final String V2 = "V2";

  /**
   * Default value of {@code queryCacheItemMaxAge} in minutes.
   */
  public static final int DEFAULT_QUERY_CACHE_ITEM_MAX_AGE = 3600;

  private final String nugetVersion;

  private final Integer queryCacheItemMaxAge;

  @JsonCreator
  public NugetAttributes(
      @JsonProperty(NUGET_VERSION) final String nugetVersion,
      @JsonProperty(QUERY_CACHE_ITEM_MAX_AGE) final Integer queryCacheItemMaxAge)
  {
    this.nugetVersion = nugetVersion;
    this.queryCacheItemMaxAge = queryCacheItemMaxAge;
  }

  public NugetAttributes() {
    this(null, null);
  }

  public String getNugetVersion() {
    return nugetVersion;
  }

  public Integer getQueryCacheItemMaxAge() {
    return queryCacheItemMaxAge;
  }
}
