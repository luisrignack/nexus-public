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
package org.sonatype.nexus.api.rest.common.blobstore.s3;

import java.util.List;

import org.sonatype.nexus.api.rest.common.blobstore.model.BlobStoreApiSoftQuota;
import org.sonatype.nexus.api.rest.common.blobstore.s3.model.S3BlobStoreApiAdvancedBucketConnection;
import org.sonatype.nexus.api.rest.common.blobstore.s3.model.S3BlobStoreApiBucket;
import org.sonatype.nexus.api.rest.common.blobstore.s3.model.S3BlobStoreApiBucketConfiguration;
import org.sonatype.nexus.api.rest.common.blobstore.s3.model.S3BlobStoreApiBucketSecurity;
import org.sonatype.nexus.api.rest.common.blobstore.s3.model.S3BlobStoreApiEncryption;
import org.sonatype.nexus.api.rest.common.blobstore.s3.model.S3BlobStoreApiFailoverBucket;
import org.sonatype.nexus.api.rest.common.blobstore.s3.model.S3BlobStoreApiModel;
import org.sonatype.nexus.blobstore.api.BlobStoreConfiguration;

import org.sonatype.nexus.blobstore.s3.S3BlobStoreConfigurationBuilder;
import org.sonatype.nexus.common.app.ApplicationVersion;

import com.google.common.base.Preconditions;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Transforms a {@link S3BlobStoreApiModel} to an {@link BlobStoreConfiguration}.
 *
 * @since 3.20
 */
public final class S3BlobStoreApiModelMapper
{
  public static final String PRO = "PRO";

  public static BlobStoreConfiguration map(
      final BlobStoreConfiguration blobStoreConfiguration,
      final S3BlobStoreApiModel request,
      final ApplicationVersion applicationVersion)
  {
    checkNotNull(blobStoreConfiguration);
    checkNotNull(request);
    final S3BlobStoreApiBucketConfiguration bucketConfiguration = checkNotNull(request.getBucketConfiguration());
    S3BlobStoreApiBucket bucket =
        Preconditions.checkNotNull(bucketConfiguration.getBucket(), "Missing bucket configuration");

    final Boolean preSignedUrlEnabled = bucketConfiguration.getPreSignedUrlEnabled();
    if (Boolean.TRUE.equals(preSignedUrlEnabled) && !PRO.equals(applicationVersion.getEdition())) {
      throw new PreSignedUrlNotAllowedException(
          "Pre-signed URL feature is only available in Nexus Repository Manager Pro");
    }

    S3BlobStoreConfigurationBuilder builder =
        S3BlobStoreConfigurationBuilder.builder(blobStoreConfiguration, request.getName())
            .bucket(bucket.getName())
            .region(bucket.getRegion())
            .prefix(bucket.getPrefix())
            .preSignedUrlEnabled(preSignedUrlEnabled);

    S3BlobStoreApiBucketSecurity bucketSecurity = bucketConfiguration.getBucketSecurity();
    if (bucketSecurity != null) {
      builder.accessKey(bucketSecurity.getAccessKeyId());
      builder.accessSecret(bucketSecurity.getSecretAccessKey());
      builder.assumeRole(bucketSecurity.getRole());
      builder.sessionTokenKey(bucketSecurity.getSessionToken());
    }

    S3BlobStoreApiEncryption encryption = bucketConfiguration.getEncryption();
    if (encryption != null) {
      builder.encryptionKey(encryption.getEncryptionKey());
      builder.encryptionType(encryption.getEncryptionType());
    }

    S3BlobStoreApiAdvancedBucketConnection advanced = bucketConfiguration.getAdvancedBucketConnection();
    if (advanced != null) {
      builder.endpoint(advanced.getEndpoint());
      builder.signerType(advanced.getSignerType());
      builder.maxConnectionPool(advanced.getMaxConnectionPoolSize());
      builder.forcePathStyle(advanced.getForcePathStyle());
    }

    List<S3BlobStoreApiFailoverBucket> failoverBuckets = bucketConfiguration.getFailoverBuckets();
    if (failoverBuckets != null) {
      failoverBuckets.forEach(failover -> builder.failover(failover.getRegion(), failover.getBucketName()));
    }

    BlobStoreApiSoftQuota softQuota = request.getSoftQuota();
    if (softQuota != null) {
      builder.quotaConfig(softQuota.getType(), checkNotNull(softQuota.getLimit(), "Missing quota limit"));
    }

    return builder.build();
  }
}
