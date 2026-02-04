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
package org.sonatype.nexus.bootstrap.blobstore.file;

import jakarta.inject.Provider;

import org.sonatype.nexus.blobstore.api.BlobStore;
import org.sonatype.nexus.blobstore.file.FileBlobStore;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FileBlobStoreConfiguration
{
  @Bean
  public Provider<BlobStore> fileBlobstoreProvider(final ObjectProvider<FileBlobStore> provider) {
    return new FileBlobStoreProvider(provider);
  }

  @Qualifier(FileBlobStore.TYPE)
  private static record FileBlobStoreProvider(ObjectProvider<FileBlobStore> provider)
      implements Provider<BlobStore>
  {
    @Override
    public BlobStore get() {
      return provider.getIfUnique();
    }
  }
}
