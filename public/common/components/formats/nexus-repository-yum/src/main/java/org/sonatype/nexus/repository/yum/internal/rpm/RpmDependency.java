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
package org.sonatype.nexus.repository.yum.internal.rpm;

/**
 * A dependency entry from an RPM header (provides/requires/conflicts/obsoletes).
 */
public final class RpmDependency
{
  private final String name;

  private final int flags;

  private final long epoch;

  private final String version;

  private final String release;

  public RpmDependency(
      final String name,
      final int flags,
      final long epoch,
      final String version,
      final String release)
  {
    this.name = name;
    this.flags = flags;
    this.epoch = epoch;
    this.version = version;
    this.release = release;
  }

  public String name() {
    return name;
  }

  public int flags() {
    return flags;
  }

  public long epoch() {
    return epoch;
  }

  public String version() {
    return version;
  }

  public String release() {
    return release;
  }

  public boolean hasVersion() {
    return version != null && !version.isEmpty();
  }

  /**
   * Converts RPM sense flags (2=less, 4=greater, 8=equal) into the flags used by Yum metadata.
   */
  public String flagsString() {
    int sense = flags & 0x0f;
    return switch (sense) {
      case 2 -> "LT";
      case 4 -> "GT";
      case 8 -> "EQ";
      case 10 -> "LE";
      case 12 -> "GE";
      default -> null;
    };
  }
}
