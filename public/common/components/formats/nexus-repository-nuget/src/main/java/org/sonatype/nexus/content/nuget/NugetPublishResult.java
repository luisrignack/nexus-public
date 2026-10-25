/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype Nexus" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.content.nuget;

/**
 * Outcome of a successful {@code .nupkg} publish.
 */
public class NugetPublishResult
{
  private final String id;

  private final String version;

  private final String nupkgPath;

  private final String nuspecPath;

  private final byte[] nuspec;

  public NugetPublishResult(
      final String id,
      final String version,
      final String nupkgPath,
      final String nuspecPath,
      final byte[] nuspec)
  {
    this.id = id;
    this.version = version;
    this.nupkgPath = nupkgPath;
    this.nuspecPath = nuspecPath;
    this.nuspec = nuspec;
  }

  public String getId() {
    return id;
  }

  public String getVersion() {
    return version;
  }

  public String getNupkgPath() {
    return nupkgPath;
  }

  public String getNuspecPath() {
    return nuspecPath;
  }

  public byte[] getNuspec() {
    return nuspec;
  }
}
