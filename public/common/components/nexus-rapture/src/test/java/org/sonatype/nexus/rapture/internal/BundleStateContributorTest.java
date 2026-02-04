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
package org.sonatype.nexus.rapture.internal;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.sonatype.nexus.bootstrap.entrypoint.edition.NexusEdition;
import org.sonatype.nexus.bootstrap.entrypoint.edition.NexusEditionSelector;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class BundleStateContributorTest
{
  @Mock
  private NexusEditionSelector nexusEditionSelector;

  @Mock
  private NexusEdition nexusEdition;

  private BundleStateContributor bundleStateContributor;

  @Before
  public void setUp() {
    bundleStateContributor = new BundleStateContributor(nexusEditionSelector);
    when(nexusEditionSelector.getCurrent()).thenReturn(nexusEdition);
  }

  @Test
  public void testGetState_filtersExternalDependencies() {
    // Mock modules list including both internal and external dependencies
    List<String> allModules = Arrays.asList(
        "nexus-core-1.0.jar",
        "nexus-rapture-1.0.jar",
        "sonatype-nexus-repository-1.0.jar",
        "nxrm-plugin-1.0.jar",
        "spring-boot-3.3.6.jar",
        "guava-32.1.3.jar",
        "jackson-databind-2.17.0.jar",
        "nexus-test-module-2.0.jar");
    when(nexusEdition.getModules()).thenReturn(allModules);

    Map<String, Object> state = bundleStateContributor.getState();
    @SuppressWarnings("unchecked")
    List<String> modules = (List<String>) state.get(BundleStateContributor.STATE_ID);

    // Only internal Nexus modules with "nexus-" prefix should be returned
    assertThat(modules)
        .hasSize(3)
        .containsExactlyInAnyOrder(
            "nexus-core-1.0.jar",
            "nexus-rapture-1.0.jar",
            "nexus-test-module-2.0.jar");
  }

  @Test
  public void testGetState_cachesModuleList() {
    List<String> modulesList = Arrays.asList("nexus-core-1.0.jar", "nexus-rapture-1.0.jar");
    when(nexusEdition.getModules()).thenReturn(modulesList);

    // Call getState twice
    bundleStateContributor.getState();
    Map<String, Object> state2 = bundleStateContributor.getState();

    @SuppressWarnings("unchecked")
    List<String> modules = (List<String>) state2.get(BundleStateContributor.STATE_ID);

    assertThat(modules).hasSize(2);
    // Verify that getModules() was only called once (cached)
    verify(nexusEdition, times(1)).getModules();
  }

  @Test
  public void testGetState_handlesEmptyModuleList() {
    when(nexusEdition.getModules()).thenReturn(List.of());

    Map<String, Object> state = bundleStateContributor.getState();
    @SuppressWarnings("unchecked")
    List<String> modules = (List<String>) state.get(BundleStateContributor.STATE_ID);

    assertThat(modules).isEmpty();
  }

  @Test
  public void testGetState_handlesOnlyExternalDependencies() {
    // All external dependencies, no internal modules
    List<String> allModules = Arrays.asList(
        "spring-boot-3.3.6.jar",
        "guava-32.1.3.jar",
        "jackson-databind-2.17.0.jar");
    when(nexusEdition.getModules()).thenReturn(allModules);

    Map<String, Object> state = bundleStateContributor.getState();
    @SuppressWarnings("unchecked")
    List<String> modules = (List<String>) state.get(BundleStateContributor.STATE_ID);

    assertThat(modules).isEmpty();
  }

  @Test
  public void testGetState_includesOnlyNexusPrefix() {
    // Test that only "nexus-" prefix modules are included
    List<String> allModules = Arrays.asList(
        "nexus-base-1.0.jar",
        "sonatype-nexus-pro-2.0.jar",
        "nxrm-cloud-3.0.jar",
        "other-library-1.0.jar");
    when(nexusEdition.getModules()).thenReturn(allModules);

    Map<String, Object> state = bundleStateContributor.getState();
    @SuppressWarnings("unchecked")
    List<String> modules = (List<String>) state.get(BundleStateContributor.STATE_ID);

    assertThat(modules)
        .hasSize(1)
        .containsExactly("nexus-base-1.0.jar");
  }

  @Test
  public void testGetState_returnsCorrectStateId() {
    when(nexusEdition.getModules()).thenReturn(List.of());

    Map<String, Object> state = bundleStateContributor.getState();

    assertThat(state)
        .containsKey(BundleStateContributor.STATE_ID)
        .containsKey("activeBundles");
  }
}
