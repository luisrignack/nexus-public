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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * REST model of the Docker specific repository attributes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DockerAttributes
{
  public static final String NAME = "docker";

  private final Integer httpPort;

  private final Integer httpsPort;

  private final Boolean forceBasicAuth;

  private final Boolean v1Enabled;

  private final String subdomain;

  @JsonCreator
  public DockerAttributes(
      @JsonProperty("httpPort") final Integer httpPort,
      @JsonProperty("httpsPort") final Integer httpsPort,
      @JsonProperty("forceBasicAuth") final Boolean forceBasicAuth,
      @JsonProperty("v1Enabled") final Boolean v1Enabled,
      @JsonProperty("subdomain") final String subdomain)
  {
    this.httpPort = httpPort;
    this.httpsPort = httpsPort;
    this.forceBasicAuth = forceBasicAuth;
    this.v1Enabled = v1Enabled;
    this.subdomain = subdomain;
  }

  public Integer getHttpPort() {
    return httpPort;
  }

  public Integer getHttpsPort() {
    return httpsPort;
  }

  public Boolean getForceBasicAuth() {
    return forceBasicAuth;
  }

  public Boolean getV1Enabled() {
    return v1Enabled;
  }

  public String getSubdomain() {
    return subdomain;
  }
}
