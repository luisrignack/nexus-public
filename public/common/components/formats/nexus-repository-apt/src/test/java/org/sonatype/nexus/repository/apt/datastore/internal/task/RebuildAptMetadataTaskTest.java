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
package org.sonatype.nexus.repository.apt.datastore.internal.task;

import java.util.Collections;
import java.util.UUID;

import org.sonatype.goodies.testsupport.TestSupport;
import org.sonatype.nexus.repository.Format;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.RepositoryTaskSupport;
import org.sonatype.nexus.repository.Type;
import org.sonatype.nexus.repository.apt.AptFormat;
import org.sonatype.nexus.repository.apt.datastore.AptContentFacet;
import org.sonatype.nexus.repository.apt.datastore.internal.data.AptKeyValueFacet;
import org.sonatype.nexus.repository.apt.datastore.internal.hosted.metadata.AptHostedMetadataFacet;
import org.sonatype.nexus.repository.content.fluent.FluentAsset;
import org.sonatype.nexus.repository.manager.RepositoryManager;
import org.sonatype.nexus.repository.types.GroupType;
import org.sonatype.nexus.repository.types.HostedType;
import org.sonatype.nexus.scheduling.TaskConfiguration;

import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.util.ThreadContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RebuildAptMetadataTaskTest
    extends TestSupport
{
  @Mock
  private RepositoryManager repositoryManager;

  @Mock
  private GroupType groupType;

  @Mock
  private SecurityManager securityManager;

  @Mock
  private Repository aptRepository;

  @Mock
  private AptContentFacet contentFacet;

  @Mock
  private AptKeyValueFacet keyValueFacet;

  @Mock
  private AptHostedMetadataFacet metadataFacet;

  private RebuildAptMetadataTask underTest;

  @Before
  public void setup() {
    ThreadContext.bind(securityManager);

    underTest = new RebuildAptMetadataTask();
    underTest.install(repositoryManager, groupType);

    // Setup repository mocks
    when(aptRepository.getName()).thenReturn("test-apt-repo");
    when(aptRepository.getFormat()).thenReturn(new AptFormat());
    when(aptRepository.getType()).thenReturn(new HostedType());
    when(aptRepository.facet(AptContentFacet.class)).thenReturn(contentFacet);
    when(aptRepository.facet(AptKeyValueFacet.class)).thenReturn(keyValueFacet);
    when(aptRepository.facet(AptHostedMetadataFacet.class)).thenReturn(metadataFacet);

    // Mock getAptPackageAssets to return empty iterable
    when(contentFacet.getAptPackageAssets()).thenReturn(Collections.emptyList());

    when(repositoryManager.get("test-apt-repo")).thenReturn(aptRepository);
  }

  @After
  public void tearDown() {
    ThreadContext.unbindSecurityManager();
  }

  @Test
  public void testDeltaRebuild_shouldSkipAptKeyValueRepopulation() throws Exception {
    // Configure task for delta rebuild (rebuildAptMetadataFullRebuild=false)
    TaskConfiguration configuration = createTaskConfiguration(false);
    underTest.configure(configuration);

    // Execute
    underTest.call();

    // Verify that apt_key_value is NOT repopulated
    verify(keyValueFacet, never()).removeAllPackageMetadata();
    verify(metadataFacet, never()).addPackageMetadata(any(FluentAsset.class));

    // Verify that metadata files are rebuilt
    verify(metadataFacet, times(1)).rebuildMetadata();
  }

  @Test
  public void testFullRebuild_shouldRepopulateAptKeyValue() throws Exception {
    // Mock some assets for full rebuild
    FluentAsset mockAsset1 = mock(FluentAsset.class);
    FluentAsset mockAsset2 = mock(FluentAsset.class);
    when(contentFacet.getAptPackageAssets()).thenReturn(java.util.Arrays.asList(mockAsset1, mockAsset2));

    // Configure task for full rebuild (rebuildAptMetadataFullRebuild=true)
    TaskConfiguration configuration = createTaskConfiguration(true);
    underTest.configure(configuration);

    // Execute
    underTest.call();

    // Verify that apt_key_value is cleared
    verify(keyValueFacet, times(1)).removeAllPackageMetadata();

    // Verify that apt_key_value is repopulated for each asset
    verify(metadataFacet, times(1)).addPackageMetadata(mockAsset1);
    verify(metadataFacet, times(1)).addPackageMetadata(mockAsset2);

    // Verify that metadata files are rebuilt
    verify(metadataFacet, times(1)).rebuildMetadata();
  }

  @Test
  public void testAppliesTo_aptHostedRepository() {
    Repository aptHosted = mock(Repository.class);
    Format aptFormat = new AptFormat();
    Type hostedType = new HostedType();

    when(aptHosted.getFormat()).thenReturn(aptFormat);
    when(aptHosted.getType()).thenReturn(hostedType);

    assertThat(underTest.appliesTo(aptHosted), is(true));
  }

  @Test
  public void testAppliesTo_nonAptRepository() {
    Repository mavenRepo = mock(Repository.class);
    Format mavenFormat = mock(Format.class);
    Type hostedType = new HostedType();

    when(mavenFormat.getValue()).thenReturn("maven");
    when(mavenRepo.getFormat()).thenReturn(mavenFormat);
    when(mavenRepo.getType()).thenReturn(hostedType);

    assertThat(underTest.appliesTo(mavenRepo), is(false));
  }

  @Test
  public void testAppliesTo_aptProxyRepository() {
    Repository aptProxy = mock(Repository.class);
    Format aptFormat = new AptFormat();
    Type proxyType = mock(Type.class);

    when(proxyType.getValue()).thenReturn("proxy");
    when(aptProxy.getFormat()).thenReturn(aptFormat);
    when(aptProxy.getType()).thenReturn(proxyType);

    assertThat(underTest.appliesTo(aptProxy), is(false));
  }

  private TaskConfiguration createTaskConfiguration(boolean fullRebuild) {
    TaskConfiguration configuration = new TaskConfiguration();
    configuration.setId(UUID.randomUUID().toString());
    configuration.setTypeId(RebuildAptMetadataTaskDescriptor.TYPE_ID);
    configuration.setString(RepositoryTaskSupport.REPOSITORY_NAME_FIELD_ID, "test-apt-repo");
    configuration.setBoolean(RebuildAptMetadataTaskDescriptor.APT_METADATA_FULL_REBUILD, fullRebuild);
    return configuration;
  }
}
