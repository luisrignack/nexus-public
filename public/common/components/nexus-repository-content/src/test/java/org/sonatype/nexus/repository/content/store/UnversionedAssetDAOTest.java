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
package org.sonatype.nexus.repository.content.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test {@link AssetDAO} without setting the component's entity version
 */
class UnversionedAssetDAOTest
    extends AssetDAOTestSupport
{
  @BeforeEach
  void setupContent() {
    initialiseContent(false);
  }

  @Override
  @Test
  protected void testCrudOperations() throws InterruptedException {
    super.testCrudOperations();
  }

  @Override
  @Test
  protected void testLastDownloaded() throws InterruptedException {
    super.testLastDownloaded();
  }

  @Override
  @Test
  protected void testAttachingBlobs() throws InterruptedException {
    super.testAttachingBlobs();
  }

  @Override
  @Test
  protected void testBrowseComponentAssets() {
    super.testBrowseComponentAssets();
  }

  @Override
  @Test
  protected void testContinuationBrowsing() {
    super.testContinuationBrowsing();
  }

  @Override
  @Test
  protected void testFlaggedBrowsing() {
    super.testFlaggedBrowsing();
  }

  @Override
  @Test
  protected void testReadPathTest() {
    super.testReadPathTest();
  }

  @Override
  @Test
  protected void testDeleteAllAssets() {
    super.testDeleteAllAssets();
  }

  @Override
  @Test
  protected void testReadPaths() {
    super.testReadPaths();
  }

  @Override
  @Test
  protected void testPurgeOperation() {
    super.testPurgeOperation();
  }

  @Override
  @Test
  protected void testRoundTrip() {
    super.testRoundTrip();
  }

  @Override
  @Test
  protected void testBrowseAssetsInRepositories() {
    super.testBrowseAssetsInRepositories();
  }

  @Override
  @Test
  protected void testBrowseEagerAssetsInRepository() {
    super.testBrowseEagerAssetsInRepository();
  }

  @Override
  @Test
  protected void testBrowseEagerAssetsInRepositoryOrderingByBlobCreated() throws InterruptedException {
    super.testBrowseEagerAssetsInRepositoryOrderingByBlobCreated();
  }

  @Override
  @Test
  protected void testBrowseEagerAssetsInRepositoryPaginationWithBlobCreated() throws InterruptedException {
    super.testBrowseEagerAssetsInRepositoryPaginationWithBlobCreated();
  }

  @Override
  @Test
  protected void testBrowseEagerAssetsInRepositorySameTimestampDifferentAssetId() throws InterruptedException {
    super.testBrowseEagerAssetsInRepositorySameTimestampDifferentAssetId();
  }

  @Override
  @Test
  protected void testSetLastDownloaded() {
    super.testSetLastDownloaded();
  }

  @Override
  @Test
  protected void testLastUpdated() {
    super.testLastUpdated();
  }

  @Override
  @Test
  protected void testFilterClauseIsolation() {
    super.testFilterClauseIsolation();
  }

  @Override
  @Test
  protected void testFindByBlobRef() throws InterruptedException {
    super.testFindByBlobRef();
  }

  @Override
  @Test
  protected void testFindByComponentIds() {
    super.testFindByComponentIds();
  }

  @Override
  @Test
  protected void testFindAddedToRepository() {
    super.testFindAddedToRepository();
  }

  @Override
  @Test
  protected void testFindAddedToRepositoryTruncatesToMilliseconds() {
    super.testFindAddedToRepositoryTruncatesToMilliseconds();
  }

  @Override
  @Test
  protected void testDeleteByPaths() {
    super.testDeleteByPaths();
  }

  @Override
  @Test
  protected void testAssetRecordsExist() {
    super.testAssetRecordsExist();
  }
}
