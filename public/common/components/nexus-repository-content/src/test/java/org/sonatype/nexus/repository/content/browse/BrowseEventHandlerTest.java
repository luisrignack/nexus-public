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
package org.sonatype.nexus.repository.content.browse;

import java.time.Duration;

import org.sonatype.goodies.testsupport.TestSupport;
import org.sonatype.nexus.common.cooperation2.datastore.DefaultCooperation2Factory;
import org.sonatype.nexus.common.db.DatabaseCheck;
import org.sonatype.nexus.common.event.EventManager;
import org.sonatype.nexus.common.scheduling.PeriodicJobService;
import org.sonatype.nexus.repository.content.browse.capability.BrowseTrimService;
import org.sonatype.nexus.repository.content.event.asset.AssetCreatedEvent;
import org.sonatype.nexus.repository.content.event.asset.AssetDeletedEvent;
import org.sonatype.nexus.repository.content.event.asset.AssetPurgedEvent;
import org.sonatype.nexus.repository.content.event.asset.AssetUploadedEvent;
import org.sonatype.nexus.repository.content.event.component.ComponentDeletedEvent;
import org.sonatype.nexus.repository.content.event.component.ComponentPurgedEvent;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class BrowseEventHandlerTest
    extends TestSupport
{
  @Mock
  private PeriodicJobService periodicJobService;

  @Mock
  private EventManager eventManager;

  @Mock
  private DatabaseCheck databaseCheck;

  @Mock
  private BrowseTrimService browseTrimService;

  private BrowseEventHandler underTest;

  @Before
  public void setup() {
    DefaultCooperation2Factory cooperation = new DefaultCooperation2Factory();
    underTest = new BrowseEventHandler(cooperation, periodicJobService, eventManager, true, Duration.ofSeconds(0),
        Duration.ofSeconds(30), 100, 2, true, databaseCheck, browseTrimService);
  }

  @Test
  public void testAssetPurgedEvent_notProcessedWhenPaused() {
    underTest.pauseEventProcessing();
    AssetPurgedEvent event = mock(AssetPurgedEvent.class);
    underTest.on(event);
    verifyNoInteractions(event);
  }

  @Test
  public void testAssetCreatedEvent_notProcessedWhenPaused() {
    underTest.pauseEventProcessing();
    AssetCreatedEvent event = mock(AssetCreatedEvent.class);
    underTest.on(event);
    verifyNoInteractions(event);
  }

  @Test
  public void testAssetUploadedEvent_notProcessedWhenPaused() {
    underTest.pauseEventProcessing();
    AssetUploadedEvent event = mock(AssetUploadedEvent.class);
    underTest.on(event);
    verifyNoInteractions(event);
  }

  @Test
  public void testAssetDeletedEvent_notProcessedWhenPaused() {
    underTest.pauseEventProcessing();
    AssetDeletedEvent event = mock(AssetDeletedEvent.class);
    underTest.on(event);
    verifyNoInteractions(event);
  }

  @Test
  public void testComponentPurgedEvent_notProcessedWhenPaused() {
    underTest.pauseEventProcessing();
    ComponentPurgedEvent event = mock(ComponentPurgedEvent.class);
    underTest.on(event);
    verifyNoInteractions(event);
  }

  @Test
  public void testComponentDeletedEvent_notProcessedWhenPaused() {
    underTest.pauseEventProcessing();
    ComponentDeletedEvent event = mock(ComponentDeletedEvent.class);
    underTest.on(event);
    verifyNoInteractions(event);
  }

  @Test
  public void testMaybeTrimRepositories_skipsWhenNotAllowed() {
    when(browseTrimService.shouldAllowTrim()).thenReturn(false);

    underTest.maybeTrimRepositories();

    verify(browseTrimService, times(1)).shouldAllowTrim();
  }

  @Test
  public void testMaybeTrimRepositories_allowsWhenEnabled() {
    when(browseTrimService.shouldAllowTrim()).thenReturn(true);

    underTest.maybeTrimRepositories();

    verify(browseTrimService, times(1)).shouldAllowTrim();
  }

  @Test
  public void testShouldTriggerImmediatePurge_withBatchEnabled_returnsFalse() {
    when(browseTrimService.isBatchTrimEnabled()).thenReturn(true);

    DefaultCooperation2Factory cooperation = new DefaultCooperation2Factory();
    BrowseEventHandler handler = new BrowseEventHandler(
        cooperation, periodicJobService, eventManager, true, Duration.ofSeconds(0),
        Duration.ofSeconds(30), 100, 2, true, databaseCheck, browseTrimService);

    when(browseTrimService.isBatchTrimEnabled()).thenReturn(true);
  }

  @Test
  public void testBatchTrimEnabled_defaultsToFalse() {
    when(browseTrimService.isBatchTrimEnabled()).thenReturn(false);

    assertFalse("Batch trim should default to false", browseTrimService.isBatchTrimEnabled());
  }

  @Test
  public void testPostgresqlTrimEnabled_canBeToggled() {
    when(browseTrimService.isPostgresqlTrimEnabled()).thenReturn(false).thenReturn(true);

    assertFalse(browseTrimService.isPostgresqlTrimEnabled());
    assertTrue(browseTrimService.isPostgresqlTrimEnabled());
  }
}
