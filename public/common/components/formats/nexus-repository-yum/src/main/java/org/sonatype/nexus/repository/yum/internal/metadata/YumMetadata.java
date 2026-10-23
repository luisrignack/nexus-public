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

import java.util.Collections;
import java.util.Map;

/**
 * Complete generated metadata for a Yum repository.
 */
public final class YumMetadata
{
  public static final String REPOMD_PATH = "repodata/repomd.xml";

  private final String revision;

  private final Map<String, YumMetadataFile> files;

  public YumMetadata(final String revision, final Map<String, YumMetadataFile> files) {
    this.revision = revision;
    this.files = Map.copyOf(files);
  }

  public String revision() {
    return revision;
  }

  public Map<String, YumMetadataFile> files() {
    return Collections.unmodifiableMap(files);
  }

  public YumMetadataFile file(final String path) {
    return files.get(path);
  }
}
