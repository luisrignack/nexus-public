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
package org.sonatype.nexus.repository.content.maintenance.internal;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.sonatype.goodies.testsupport.TestSupport;
import org.sonatype.nexus.common.db.DatabaseCheck;
import org.sonatype.nexus.common.entity.EntityId;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.browse.node.BrowseNode;
import org.sonatype.nexus.repository.browse.node.BrowseNodeConfiguration;
import org.sonatype.nexus.repository.browse.node.BrowseNodeQueryService;
import org.sonatype.nexus.repository.content.browse.BrowseFacet;
import org.sonatype.nexus.repository.content.browse.store.BrowseNodeData;
import org.sonatype.nexus.repository.content.facet.ContentFacet;
import org.sonatype.nexus.repository.content.fluent.FluentAssets;
import org.sonatype.nexus.repository.content.fluent.FluentComponent;
import org.sonatype.nexus.repository.content.fluent.FluentComponents;
import org.sonatype.nexus.repository.content.maintenance.ContentMaintenanceFacet;
import org.sonatype.nexus.repository.security.ContentPermissionChecker;
import org.sonatype.nexus.repository.security.VariableResolverAdapterManager;
import org.sonatype.nexus.security.SecurityHelper;

import org.apache.shiro.util.ThreadContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DeleteFolderServiceTest
    extends TestSupport
{
  @Mock
  private BrowseNodeQueryService browseNodeQueryService;

  @Mock
  private BrowseNodeConfiguration configuration;

  @Mock
  private ContentPermissionChecker contentPermissionChecker;

  @Mock
  private VariableResolverAdapterManager variableResolverAdapterManager;

  @Mock
  private SecurityHelper securityHelper;

  @Mock
  private DatabaseCheck databaseCheck;

  @Mock
  private Repository repository;

  @Mock
  private ContentFacet contentFacet;

  @Mock
  private ContentMaintenanceFacet contentMaintenance;

  @Mock
  private BrowseFacet browseFacet;

  @Spy
  @InjectMocks
  private DeleteFolderServiceImpl deleteFolderService;

  @Before
  public void setUp() {
    BrowseNodeData node = mock(BrowseNodeData.class);
    when(node.getNodeId()).thenReturn(1L);

    when(repository.facet(ContentFacet.class)).thenReturn(contentFacet);
    when(repository.facet(ContentMaintenanceFacet.class)).thenReturn(contentMaintenance);
    when(repository.facet(BrowseFacet.class)).thenReturn(browseFacet);
    when(browseFacet.getByRequestPath(any())).thenReturn(Optional.of(node));
    when(configuration.getMaxNodes()).thenReturn(100);
  }

  @After
  public void tearDown() {
    // Unbind SecurityManager
    ThreadContext.unbindSecurityManager();
  }

  @Test
  public void testDeleteFolderForPostgreSql() {
    when(databaseCheck.isPostgresql()).thenReturn(true);
    doReturn(true).when(deleteFolderService).canDeleteComponent(repository);
    when(browseNodeQueryService.getByPath(any(), any(), anyInt())).thenReturn(Collections.emptyList());

    deleteFolderService.deleteFolder(repository, "path", OffsetDateTime.now());

    verify(browseNodeQueryService, times(1)).getByPath(any(), any(), anyInt());
    verify(browseFacet, times(1)).deleteByNodeId(any());
  }

  @Test
  public void testDeleteFolderForNonPostgreSQL() {
    when(databaseCheck.isPostgresql()).thenReturn(false);
    doReturn(true).when(deleteFolderService).canDeleteComponent(repository);
    when(browseNodeQueryService.getByPath(any(), any(), anyInt())).thenReturn(Collections.emptyList());

    deleteFolderService.deleteFolder(repository, "path", OffsetDateTime.now());

    verify(browseNodeQueryService, times(1)).getByPath(any(), any(), anyInt());
    verify(deleteFolderService, never()).deleteFoldersAndBrowseNode(any(), any(), any(), any(), any(), anyInt(),
        anyBoolean());
  }

  @Test
  public void testDeleteFolders() {
    when(browseNodeQueryService.getByPath(any(), any(), anyInt())).thenReturn(Collections.emptyList());

    deleteFolderService.deleteFolders(repository, "path", OffsetDateTime.now(), contentFacet, contentMaintenance, 100,
        true);

    verify(browseNodeQueryService, times(1)).getByPath(any(), any(), anyInt());
  }

  @Test
  public void testDeleteFoldersAndBrowseNode() {
    when(browseNodeQueryService.getByPath(any(), any(), anyInt())).thenReturn(Collections.emptyList());

    deleteFolderService.deleteFoldersAndBrowseNode(repository, "path", OffsetDateTime.now(), contentFacet,
        contentMaintenance, 100, true);

    verify(browseNodeQueryService, times(1)).getByPath(any(), any(), anyInt());
  }

  @Test
  public void testDeleteFolderForPostgreSqlWithChildren() {

    List<BrowseNode> childrenNodes = generateChildrenNodes();
    List<BrowseNode> childrenLeaves = generateChildrenLeaves();
    when(databaseCheck.isPostgresql()).thenReturn(true);
    doReturn(true).when(deleteFolderService).canDeleteComponent(repository);

    when(browseNodeQueryService.getByPath(any(), any(), anyInt()))
        .thenReturn(childrenNodes)
        .thenReturn(childrenLeaves)
        .thenReturn(Collections.emptyList())
        .thenReturn(childrenLeaves)
        .thenReturn(Collections.emptyList());

    deleteFolderService.deleteFolder(repository, "path", OffsetDateTime.now());

    verify(browseNodeQueryService, times(6)).getByPath(any(), any(), anyInt());
    verify(browseFacet, times(7)).deleteByNodeId(any());
    verify(deleteFolderService, never()).deleteFolders(any(), any(), any(), any(), any(), anyInt(), anyBoolean());
    verify(deleteFolderService, times(4)).checkDeleteComponent(any(), any(), any(), anyBoolean(), any());
    verify(deleteFolderService, times(4)).checkDeleteAsset(any(), any(), any(), any(), any());
  }

  private List<BrowseNode> generateChildrenNodes() {
    BrowseNodeData nodeOne = mock(BrowseNodeData.class);
    when(nodeOne.getNodeId()).thenReturn(2L);
    when(nodeOne.getName()).thenReturn("nodeOne");
    when(nodeOne.isLeaf()).thenReturn(false);
    when(nodeOne.getAssetCount()).thenReturn(0L);
    when(nodeOne.getPackageUrl()).thenReturn("/path/nodeOne/");

    BrowseNodeData nodeTwo = mock(BrowseNodeData.class);
    when(nodeTwo.getNodeId()).thenReturn(3L);
    when(nodeTwo.getName()).thenReturn("nodeTwo");
    when(nodeTwo.isLeaf()).thenReturn(false);
    when(nodeTwo.getAssetCount()).thenReturn(0L);
    when(nodeTwo.getPackageUrl()).thenReturn("/path/nodeTwo/");

    return Arrays.asList(nodeOne, nodeTwo);
  }

  private List<BrowseNode> generateChildrenLeaves() {
    BrowseNodeData childOne = mock(BrowseNodeData.class);
    when(childOne.getNodeId()).thenReturn(4L);
    when(childOne.getName()).thenReturn("childOne");
    when(childOne.isLeaf()).thenReturn(true);
    when(childOne.getAssetCount()).thenReturn(0L);
    when(childOne.getPackageUrl()).thenReturn("/path/childOne/");

    BrowseNodeData childTwo = mock(BrowseNodeData.class);
    when(childTwo.getNodeId()).thenReturn(5L);
    when(childTwo.getName()).thenReturn("childTwo");
    when(childTwo.isLeaf()).thenReturn(true);
    when(childTwo.getAssetCount()).thenReturn(0L);
    when(childTwo.getPackageUrl()).thenReturn("/path/childTwo/");

    return Arrays.asList(childOne, childTwo);
  }

  @Test
  public void testCheckDeleteComponent_ReturnsTrue_WhenComponentDeletedSuccessfully() {
    BrowseNodeData node = mock(BrowseNodeData.class);
    FluentComponent component = mock(FluentComponent.class);
    FluentComponents fluentComponents = mock(FluentComponents.class);
    EntityId componentId = mock(EntityId.class);
    OffsetDateTime timestamp = OffsetDateTime.now();
    OffsetDateTime lastUpdated = timestamp.minusDays(1);

    when(node.getComponentId()).thenReturn(componentId);
    when(node.getAssetId()).thenReturn(null);
    when(contentFacet.components()).thenReturn(fluentComponents);
    when(fluentComponents.find(componentId)).thenReturn(Optional.of(component));
    when(component.lastUpdated()).thenReturn(lastUpdated);

    boolean result = deleteFolderService.checkDeleteComponent(timestamp, contentFacet, contentMaintenance, true, node);

    assertTrue(result);
    verify(contentMaintenance).deleteComponent(component);
  }

  @Test
  public void testCheckDeleteComponent_ReturnsFalse_WhenComponentDeletionFails() {
    BrowseNodeData node = mock(BrowseNodeData.class);
    FluentComponent component = mock(FluentComponent.class);
    FluentComponents fluentComponents = mock(FluentComponents.class);
    EntityId componentId = mock(EntityId.class);
    OffsetDateTime timestamp = OffsetDateTime.now();
    OffsetDateTime lastUpdated = timestamp.minusDays(1);

    when(node.getComponentId()).thenReturn(componentId);
    when(node.getAssetId()).thenReturn(null);
    when(contentFacet.components()).thenReturn(fluentComponents);
    when(fluentComponents.find(componentId)).thenReturn(Optional.of(component));
    when(component.lastUpdated()).thenReturn(lastUpdated);
    doThrow(new RuntimeException("Delete failed")).when(contentMaintenance).deleteComponent(component);

    boolean result = deleteFolderService.checkDeleteComponent(timestamp, contentFacet, contentMaintenance, true, node);

    assertFalse(result);
    verify(contentMaintenance).deleteComponent(component);
  }

  @Test
  public void testCheckDeleteComponent_ReturnsTrue_WhenComponentNotFound() {
    BrowseNodeData node = mock(BrowseNodeData.class);
    FluentComponents fluentComponents = mock(FluentComponents.class);
    EntityId componentId = mock(EntityId.class);
    OffsetDateTime timestamp = OffsetDateTime.now();

    when(node.getComponentId()).thenReturn(componentId);
    when(node.getAssetId()).thenReturn(null);
    when(contentFacet.components()).thenReturn(fluentComponents);
    when(fluentComponents.find(componentId)).thenReturn(Optional.empty());

    boolean result = deleteFolderService.checkDeleteComponent(timestamp, contentFacet, contentMaintenance, true, node);

    assertTrue(result);
    verify(contentMaintenance, never()).deleteComponent(any());
  }

  @Test
  public void testCheckDeleteComponent_ReturnsTrue_WhenNoComponentId() {
    BrowseNodeData node = mock(BrowseNodeData.class);
    EntityId assetId = mock(EntityId.class);
    OffsetDateTime timestamp = OffsetDateTime.now();

    when(node.getComponentId()).thenReturn(null);
    when(node.getAssetId()).thenReturn(assetId);

    boolean result = deleteFolderService.checkDeleteComponent(timestamp, contentFacet, contentMaintenance, true, node);

    assertTrue(result);
    verify(contentMaintenance, never()).deleteComponent(any());
  }

  @Test
  public void testCheckDeleteComponent_ReturnsFalse_WhenTimestampBeforeLastUpdated() {
    BrowseNodeData node = mock(BrowseNodeData.class);
    FluentComponent component = mock(FluentComponent.class);
    FluentComponents fluentComponents = mock(FluentComponents.class);
    EntityId componentId = mock(EntityId.class);
    OffsetDateTime timestamp = OffsetDateTime.now();
    OffsetDateTime lastUpdated = timestamp.plusDays(1);

    when(node.getComponentId()).thenReturn(componentId);
    when(node.getAssetId()).thenReturn(null);
    when(contentFacet.components()).thenReturn(fluentComponents);
    when(fluentComponents.find(componentId)).thenReturn(Optional.of(component));
    when(component.lastUpdated()).thenReturn(lastUpdated);

    boolean result = deleteFolderService.checkDeleteComponent(timestamp, contentFacet, contentMaintenance, true, node);

    assertFalse(result);
    verify(contentMaintenance, never()).deleteComponent(any());
  }

  @Test
  public void testCheckDeleteAsset_ReturnsTrue_WhenAssetDeletedSuccessfully() {
    BrowseNodeData node = mock(BrowseNodeData.class);
    FluentAssets fluentAssets = mock(FluentAssets.class);
    EntityId assetId = mock(EntityId.class);
    OffsetDateTime timestamp = OffsetDateTime.now();

    when(node.getAssetId()).thenReturn(assetId);
    when(contentFacet.assets()).thenReturn(fluentAssets);
    doReturn(true).when(deleteFolderService).checkDeleteAsset(any(), any(), any(), any(), any());

    boolean result =
        deleteFolderService.checkDeleteAsset(repository, timestamp, contentFacet, contentMaintenance, node);

    assertTrue(result);
  }

  @Test
  public void testCheckDeleteAsset_ReturnsFalse_WhenAssetDeletionFails() {
    BrowseNodeData node = mock(BrowseNodeData.class);
    FluentAssets fluentAssets = mock(FluentAssets.class);
    EntityId assetId = mock(EntityId.class);
    OffsetDateTime timestamp = OffsetDateTime.now();

    when(node.getAssetId()).thenReturn(assetId);
    when(contentFacet.assets()).thenReturn(fluentAssets);
    doReturn(false).when(deleteFolderService).checkDeleteAsset(any(), any(), any(), any(), any());

    boolean result =
        deleteFolderService.checkDeleteAsset(repository, timestamp, contentFacet, contentMaintenance, node);

    assertFalse(result);
  }

  @Test
  public void testCheckDeleteAsset_ReturnsTrue_WhenNoAssetId() {
    BrowseNodeData node = mock(BrowseNodeData.class);
    OffsetDateTime timestamp = OffsetDateTime.now();

    when(node.getAssetId()).thenReturn(null);

    boolean result =
        deleteFolderService.checkDeleteAsset(repository, timestamp, contentFacet, contentMaintenance, node);

    assertTrue(result);
  }

  @Test
  public void testCheckDeleteComponent_OrderMatters_PreventsOrphanedComponents() {
    // This test documents WHY we check component deletion BEFORE asset deletion
    // If we checked asset first and it succeeded but component failed, we'd have orphaned components
    BrowseNodeData node = mock(BrowseNodeData.class);
    FluentComponent component = mock(FluentComponent.class);
    FluentComponents fluentComponents = mock(FluentComponents.class);
    EntityId componentId = mock(EntityId.class);
    OffsetDateTime timestamp = OffsetDateTime.now();
    OffsetDateTime lastUpdated = timestamp.minusDays(1);

    when(node.getComponentId()).thenReturn(componentId);
    when(node.getAssetId()).thenReturn(null);
    when(contentFacet.components()).thenReturn(fluentComponents);
    when(fluentComponents.find(componentId)).thenReturn(Optional.of(component));
    when(component.lastUpdated()).thenReturn(lastUpdated);
    doThrow(new RuntimeException("Component deletion failed")).when(contentMaintenance).deleteComponent(component);

    // Component deletion should be checked FIRST and fail
    boolean componentDeleted =
        deleteFolderService.checkDeleteComponent(timestamp, contentFacet, contentMaintenance, true, node);

    // Verify component deletion failed
    assertFalse(componentDeleted);

    // If asset deletion was checked first and succeeded, but component failed,
    // we would have an orphaned component (component with no assets)
    // By checking component FIRST, if it fails, we never delete the asset,
    // maintaining data consistency
  }
}
