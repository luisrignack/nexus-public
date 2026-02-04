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
 * Test {@link ComponentDAO} with no entity versioning
 */
class UnversionedComponentDAOTest
    extends ComponentDAOTestSupport
{
  @BeforeEach
  void setup() {
    setupContent(false);
  }

  @Override
  @Test
  protected void testCrudOperations() throws InterruptedException {
    super.testCrudOperations();
  }

  @Override
  @Test
  protected void testBrowseComponentCoordinates() {
    super.testBrowseComponentCoordinates();
  }

  @Override
  @Test
  protected void testContinuationBrowsing() {
    super.testContinuationBrowsing();
  }

  @Override
  @Test
  protected void testDeleteAllComponents() {
    super.testDeleteAllComponents();
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
  protected void testBrowseComponentsInRepositories() {
    super.testBrowseComponentsInRepositories();
  }

  @Override
  @Test
  protected void testFilterClauseIsolation() {
    super.testFilterClauseIsolation();
  }

  @Override
  @Test
  protected void testContinuationSetBrowsing() {
    super.testContinuationSetBrowsing();
  }

  @Override
  @Test
  protected void testNormalizationMethods() {
    super.testNormalizationMethods();
  }

  @Override
  @Test
  protected void testBrowseComponentsEager() {
    super.testBrowseComponentsEager();
  }

  @Override
  @Test
  protected void testBrowseVersionsByRepoIds() {
    super.testBrowseVersionsByRepoIds();
  }

  @Override
  @Test
  protected void testReadCoordinateInRepoIds() {
    super.testReadCoordinateInRepoIds();
  }
}
