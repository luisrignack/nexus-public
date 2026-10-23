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
package org.sonatype.nexus.repository.yum.internal.metadata;

/**
 * One generated file of a Yum repository (repomd.xml or one of the compressed metadata files).
 */
public final class YumMetadataFile
{
  private final String path;

  private final byte[] content;

  private final long size;

  private final long openSize;

  private final String checksum;

  private final String openChecksum;

  private final String contentType;

  public YumMetadataFile(
      final String path,
      final byte[] content,
      final long size,
      final long openSize,
      final String checksum,
      final String openChecksum,
      final String contentType)
  {
    this.path = path;
    this.content = content;
    this.size = size;
    this.openSize = openSize;
    this.checksum = checksum;
    this.openChecksum = openChecksum;
    this.contentType = contentType;
  }

  public String path() {
    return path;
  }

  public byte[] content() {
    return content;
  }

  public long size() {
    return size;
  }

  public long openSize() {
    return openSize;
  }

  public String checksum() {
    return checksum;
  }

  public String openChecksum() {
    return openChecksum;
  }

  public String contentType() {
    return contentType;
  }
}
