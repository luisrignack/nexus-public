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
package org.sonatype.nexus.repository.cargo;

import javax.annotation.Nullable;

/**
 * Crate level metadata used to answer crates.io compatible search ({@code api/v1/crates?q=}) and crate detail
 * ({@code api/v1/crates/{crate}}) requests.
 *
 * @since 3.89
 */
public class CargoCrateSummary
{
  private final String name;

  private final String description;

  private final String maxVersion;

  private final long downloads;

  private final String updatedAt;

  public CargoCrateSummary(
      final String name,
      @Nullable final String description,
      @Nullable final String maxVersion,
      final long downloads,
      @Nullable final String updatedAt)
  {
    this.name = name == null ? "" : name;
    this.description = description == null ? "" : description;
    this.maxVersion = maxVersion == null ? "" : maxVersion;
    this.downloads = downloads;
    this.updatedAt = updatedAt == null ? "" : updatedAt;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public String getMaxVersion() {
    return maxVersion;
  }

  public long getDownloads() {
    return downloads;
  }

  public String getUpdatedAt() {
    return updatedAt;
  }

  @Override
  public String toString() {
    return "CargoCrateSummary{name='" + name + "', maxVersion='" + maxVersion + "', downloads=" + downloads + '}';
  }
}
