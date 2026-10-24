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
package org.sonatype.nexus.sbom;

import java.util.Locale;

import javax.annotation.Nullable;

/**
 * Supported SBOM output standards.
 */
public enum SbomFormat
{
  CYCLONEDX("cyclonedx"),

  SPDX("spdx");

  private final String value;

  SbomFormat(final String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  /**
   * Parses a format value, defaulting to CycloneDX when {@code null} or blank.
   *
   * @throws IllegalArgumentException for unknown formats
   */
  public static SbomFormat from(@Nullable final String value) {
    if (value == null || value.isBlank()) {
      return CYCLONEDX;
    }
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    for (SbomFormat format : values()) {
      if (format.value.equals(normalized)) {
        return format;
      }
    }
    throw new IllegalArgumentException("Unknown SBOM format: " + value + " (expected cyclonedx or spdx)");
  }
}
