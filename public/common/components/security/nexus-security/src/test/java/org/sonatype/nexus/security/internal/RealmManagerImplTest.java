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
package org.sonatype.nexus.security.internal;

import java.util.List;

import jakarta.inject.Provider;

import org.sonatype.goodies.testsupport.TestSupport;
import org.sonatype.nexus.common.event.EventManager;
import org.sonatype.nexus.distributed.event.service.api.common.UserPasswordChangedDistributedEvent;
import org.sonatype.nexus.security.realm.RealmConfiguration;
import org.sonatype.nexus.security.realm.RealmConfigurationChangedEvent;
import org.sonatype.nexus.security.realm.RealmConfigurationEvent;
import org.sonatype.nexus.security.realm.RealmConfigurationStore;
import org.sonatype.nexus.security.realm.TestRealmConfiguration;

import com.google.common.collect.ImmutableList;
import org.apache.shiro.mgt.RealmSecurityManager;
import org.apache.shiro.realm.Realm;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class RealmManagerImplTest
    extends TestSupport
{
  @Mock
  private Provider<RealmConfiguration> initialRealmConfigurationProvider;

  @Mock
  private EventManager eventManager;

  @Mock
  private RealmConfigurationStore configStore;

  @Mock
  private RealmSecurityManager securityManager;

  @Mock
  private Realm realmA;

  @Mock
  private Realm realmB;

  @Mock
  private RealmConfigurationEvent configEvent;

  @Mock
  private AuthenticatingRealmImpl authenticatingRealm;

  @Mock
  private UserPasswordChangedDistributedEvent passwordChangedEvent;

  private RealmManagerImpl manager;

  @Before
  public void setUp() {
    List<Realm> realms = List.of(realmA, realmB);
    RealmConfiguration defaultConfig = new TestRealmConfiguration();
    defaultConfig.setRealmNames(List.of("A"));
    when(initialRealmConfigurationProvider.get()).thenReturn(defaultConfig);
    manager = new RealmManagerImpl(eventManager, configStore, initialRealmConfigurationProvider, securityManager,
        realms, false);
  }

  @Test
  public void testOnStoreChanged_LocalEvent() {
    when(configEvent.isLocal()).thenReturn(true);
    manager.on(configEvent);
    verifyNoInteractions(eventManager, configStore);
  }

  @Test
  public void testOnStoreChanged_RemoteEvent() {
    RealmConfiguration eventConfig = new TestRealmConfiguration();
    eventConfig.setRealmNames(ImmutableList.of("B"));
    when(configEvent.isLocal()).thenReturn(false);
    when(configEvent.getConfiguration()).thenReturn(eventConfig);

    manager.on(configEvent);

    ArgumentCaptor<RealmConfigurationChangedEvent> eventCaptor =
        ArgumentCaptor.forClass(RealmConfigurationChangedEvent.class);

    verify(eventManager).post(eventCaptor.capture());

    RealmConfiguration storeConfig = eventCaptor.getValue().getConfiguration();
    assertThat(storeConfig.getRealmNames(), is(eventConfig.getRealmNames()));
  }

  @Test
  public void testOnUserPasswordChanged() {
    when(passwordChangedEvent.isLocal()).thenReturn(false);
    when(passwordChangedEvent.isClearCache()).thenReturn(true);
    when(passwordChangedEvent.getUserId()).thenReturn("testuser");
    when(securityManager.getRealms()).thenReturn(List.of(authenticatingRealm));

    manager.on(passwordChangedEvent);

    verify(authenticatingRealm).clearCache("testuser");
  }

  @Test
  public void testOnUserPasswordChanged_LocalEvent() {
    when(passwordChangedEvent.isLocal()).thenReturn(true);

    manager.on(passwordChangedEvent);

    verifyNoInteractions(authenticatingRealm);
  }

  @Test
  public void testOnUserPasswordChanged_ClearCache() {
    when(passwordChangedEvent.isLocal()).thenReturn(false);
    when(passwordChangedEvent.isClearCache()).thenReturn(false);

    manager.on(passwordChangedEvent);

    verifyNoInteractions(authenticatingRealm);
  }
}
