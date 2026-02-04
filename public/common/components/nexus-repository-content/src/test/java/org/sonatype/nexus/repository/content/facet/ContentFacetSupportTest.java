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
package org.sonatype.nexus.repository.content.facet;

import java.util.Optional;

import javax.validation.ConstraintViolationException;

import org.sonatype.goodies.testsupport.Test5Support;
import org.sonatype.nexus.blobstore.api.BlobStore;
import org.sonatype.nexus.blobstore.api.BlobStoreManager;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.Type;
import org.sonatype.nexus.repository.config.Configuration;
import org.sonatype.nexus.repository.config.ConfigurationFacet;
import org.sonatype.nexus.repository.content.facet.ContentFacetSupport.Config;
import org.sonatype.nexus.repository.content.store.FormatStoreManager;
import org.sonatype.nexus.validation.ConstraintViolationFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContentFacetSupportTest
    extends Test5Support
{
  @Mock
  Configuration configuration;

  @Mock
  Repository repository;

  @Mock
  FormatStoreManager formatStoreManager;

  @Mock
  ContentFacetDependencies dependencies;

  ContentFacetSupport underTest;

  @BeforeEach
  void setup() throws Exception {
    underTest = new ContentFacetSupport(formatStoreManager)
    {
      // nothing to add
    };
    underTest.setDependencies(dependencies);
    underTest.attach(repository);
  }

  @Test
  void testDoValidate() throws Exception {
    BlobStoreManager blobStoreManager = mock();
    when(dependencies.getBlobStoreManager()).thenReturn(blobStoreManager);
    ConstraintViolationFactory factory = mock(Answers.RETURNS_MOCKS);
    when(dependencies.getConstraintViolationFactory()).thenReturn(factory);

    Type type = mock();
    when(repository.getType()).thenReturn(type);

    ConfigurationFacet conf = mock();
    when(repository.facet(ConfigurationFacet.class)).thenReturn(conf);

    Config config = new Config();
    when(conf.readSection(any(), any(), eq(Config.class))).thenReturn(config);

    // Simple case a blobstore that exists
    BlobStore blobstore = mock();
    when(blobStoreManager.get("default")).thenReturn(blobstore);
    config.blobStoreName = "default";
    assertDoesNotThrow(() -> underTest.doValidate(configuration));

    // Specified blobstore is a member group member
    when(blobStoreManager.getParent("default")).thenReturn(Optional.of("parent"));
    assertThrows(ConstraintViolationException.class, () -> underTest.doValidate(configuration));
    verify(factory).createViolation("storage.blobStoreName",
        "Blob Store 'default' is a member of Blob Store Group 'parent' and cannot be set as storage");

    reset(factory);

    // Specified blobstore does not exist - should NOT throw exception (allow repo to load on startup)
    config.blobStoreName = "missing-blobstore";
    when(blobStoreManager.get("missing-blobstore")).thenReturn(null);
    assertDoesNotThrow(() -> underTest.doValidate(configuration));
    // Existence validation is now handled by BaseRepositoryManager.validateConfiguration() during create/update
  }
}
