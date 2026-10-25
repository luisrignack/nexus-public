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
package org.sonatype.nexus.repository.cargo.rest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * REST API model of cargo attributes for repositories API.
 *
 * <p>The JSON property name of this model is {@value #NAME}; it is applied with
 * {@code @JsonProperty("cargo")} at every use site (Jackson does not allow {@code @JsonProperty} on a type).
 * Cargo has no format specific configuration, so the block is always sent empty.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CargoAttributes
{
  public static final String NAME = "cargo";

  @Override
  public String toString() {
    return "CargoAttributes{}";
  }
}
