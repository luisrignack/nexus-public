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
package org.sonatype.nexus.blobstore.file;

import java.io.IOException;

import org.sonatype.nexus.blobstore.api.BlobId;
import org.sonatype.nexus.blobstore.api.softdeleted.SoftDeletedBlobIndex;
import org.sonatype.nexus.common.property.PropertiesFile;

/**
 * Storage holding {@link BlobId} of soft-deleted blobs for future hard-deletion
 */
public interface FileBlobDeletionIndex
    extends SoftDeletedBlobIndex
{
  /**
   * Initialisation of deletion index that should be done in the scope of {@link FileBlobStore} startup
   *
   * @param metadata blobstore-related properties
   * @param blobStore the {@link FileBlobStore} instance that this deletion index is related to
   */
  void initIndex(PropertiesFile metadata, FileBlobStore blobStore) throws IOException;

  /**
   * Tear down of deletion index that should be done in the scope of {@link FileBlobStore} tear down
   */
  void stopIndex() throws IOException;
}
