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
package org.sonatype.nexus.blobstore.s3;

import org.sonatype.nexus.blobstore.MockBlobStoreConfiguration;
import org.sonatype.nexus.blobstore.api.BlobStoreConfiguration;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.sonatype.nexus.blobstore.s3.S3BlobStoreConfigurationHelper.CONFIG_KEY;
import static org.sonatype.nexus.blobstore.s3.internal.S3BlobStore.PRE_SIGNED_URL_ENABLED;

public class S3BlobStoreConfigurationBuilderTest
{
  @Test
  public void preSignedUrlShouldBeTrue() {
    final MockBlobStoreConfiguration configuration = new MockBlobStoreConfiguration("test-blobstore", "s3");
    final BlobStoreConfiguration config =
        S3BlobStoreConfigurationBuilder.builder(configuration, "test")
            .bucket("test-bucket")
            .region("test-region")
            .preSignedUrlEnabled(true)
            .build();

    assertThat(config.attributes(CONFIG_KEY).get(PRE_SIGNED_URL_ENABLED), is(true));
  }

  @Test
  public void preSignedUrlShouldBeFalse() {
    final MockBlobStoreConfiguration configuration = new MockBlobStoreConfiguration("test-blobstore", "s3");
    final BlobStoreConfiguration config =
        S3BlobStoreConfigurationBuilder.builder(configuration, "test")
            .bucket("test-bucket")
            .region("test-region")
            .preSignedUrlEnabled(false)
            .build();

    assertThat(config.attributes(CONFIG_KEY).get(PRE_SIGNED_URL_ENABLED), is(false));
  }

  @Test
  public void preSignedUrlShouldBeNull() {
    final MockBlobStoreConfiguration configuration = new MockBlobStoreConfiguration("test-blobstore", "s3");
    final BlobStoreConfiguration config =
        S3BlobStoreConfigurationBuilder.builder(configuration, "test")
            .bucket("test-bucket")
            .region("test-region")
            .build();

    assertThat(config.attributes(CONFIG_KEY).get(PRE_SIGNED_URL_ENABLED), is(nullValue()));
  }
}
