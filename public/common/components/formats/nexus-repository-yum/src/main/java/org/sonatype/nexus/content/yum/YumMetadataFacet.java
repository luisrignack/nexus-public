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
package org.sonatype.nexus.content.yum;

import java.io.IOException;

import org.sonatype.nexus.repository.Facet;

/**
 * Generates and maintains the Yum repository metadata for a hosted repository.
 */
@Facet.Exposed
public interface YumMetadataFacet
    extends Facet
{
  /**
   * Rebuilds {@code repodata/repomd.xml} and the compressed metadata files from the current RPM assets.
   */
  void rebuild() throws IOException;

  /**
   * Makes sure the metadata exists and is current with the RPM assets in the repository.
   */
  void ensureMetadata() throws IOException;
}
