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
package org.sonatype.nexus.repository.npm.internal;

import java.util.Arrays;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link NpmCoordinatesHelper} tests.
 */
public class NpmCoordinatesHelperTest
{
  @Test
  public void testDecodeScopedName() {
    assertEquals("@scope/name", NpmCoordinatesHelper.decode("%40scope%2Fname"));
    assertEquals("@scope/name", NpmCoordinatesHelper.decode("@scope%2Fname"));
  }

  @Test
  public void testComponentCoordinates() {
    assertEquals("demo", NpmCoordinatesHelper.getComponentName("demo"));
    assertEquals("", NpmCoordinatesHelper.getComponentGroup("demo"));
    assertEquals("name", NpmCoordinatesHelper.getComponentName("@scope/name/-/name-1.2.3.tgz"));
    assertEquals("@scope", NpmCoordinatesHelper.getComponentGroup("@scope/name/-/name-1.2.3.tgz"));
    assertEquals("@scope/name", NpmCoordinatesHelper.getComponentPath("@scope/name/-/name-1.2.3.tgz"));
  }

  @Test
  public void testComponentVersion() {
    assertEquals("1.2.3", NpmCoordinatesHelper.getComponentVersion("demo/-/demo-1.2.3.tgz"));
    assertEquals("1.2.3-beta.1", NpmCoordinatesHelper.getComponentVersion("demo/-/demo-1.2.3-beta.1.tgz"));
    assertEquals("2.0.0", NpmCoordinatesHelper.getComponentVersion("@scope/name/-/name-2.0.0.tgz"));
    assertNull(NpmCoordinatesHelper.getComponentVersion("demo"));
    assertNull(NpmCoordinatesHelper.getComponentVersion("demo/1.2.3"));
  }

  @Test
  public void testVersionFromTarballName() {
    assertEquals("1.0.0", NpmCoordinatesHelper.getVersionFromTarballName("pkg-1.0.0.tgz"));
    assertEquals("1.0.0", NpmCoordinatesHelper.getVersionFromTarballName("@scope/pkg-1.0.0.tgz"));
    assertEquals("1.0.0", NpmCoordinatesHelper.getVersionFromTarballName("pkg-1.0.0.tar.gz"));
    assertNull(NpmCoordinatesHelper.getVersionFromTarballName("pkg.tgz"));
  }

  @Test
  public void testManifestPaths() {
    assertEquals("demo", NpmCoordinatesHelper.getPackagePathForManifest("demo/1.2.3"));
    assertEquals("@scope/name", NpmCoordinatesHelper.getPackagePathForManifest("@scope/name/1.2.3"));
    assertNull(NpmCoordinatesHelper.getPackagePathForManifest("demo"));
    assertNull(NpmCoordinatesHelper.getPackagePathForManifest("demo/-/demo-1.2.3.tgz"));
    assertNull(NpmCoordinatesHelper.getPackagePathForManifest("-/package/demo/dist-tags"));
  }

  @Test
  public void testVersionOrdering() {
    assertTrue(NpmCoordinatesHelper.compareVersions("1.0.0", "1.0.0") == 0);
    assertTrue(NpmCoordinatesHelper.compareVersions("1.0.1", "1.0.0") > 0);
    assertTrue(NpmCoordinatesHelper.compareVersions("1.10.0", "1.9.9") > 0);
    assertTrue(NpmCoordinatesHelper.compareVersions("2.0.0", "10.0.0") < 0);
    assertTrue(NpmCoordinatesHelper.compareVersions("1.0.0-beta.2", "1.0.0-beta.10") < 0);
    assertTrue(NpmCoordinatesHelper.compareVersions("1.0.0-beta", "1.0.0") < 0);
    assertTrue(NpmCoordinatesHelper.compareVersions("1.0.0-alpha", "1.0.0-beta") < 0);
    assertTrue(NpmCoordinatesHelper.compareVersions("1.0.0+build.1", "1.0.0+build.2") == 0);
  }

  @Test
  public void testHighestVersion() {
    assertEquals("2.0.0", NpmCoordinatesHelper.highestVersion(
        Arrays.asList("1.0.0", "2.0.0", "1.10.0", "2.0.0-rc.1")));
    assertNull(NpmCoordinatesHelper.highestVersion(Arrays.asList()));
  }

  @Test
  public void testPrerelease() {
    assertTrue(NpmCoordinatesHelper.isPrerelease("1.1.0-beta.1"));
    assertTrue(NpmCoordinatesHelper.isPrerelease("1.0.0-rc.1+build.5"));
    assertFalse(NpmCoordinatesHelper.isPrerelease("1.0.0"));
    assertFalse(NpmCoordinatesHelper.isPrerelease("1.0.0+build.5"));
  }

  @Test
  public void testHighestStableVersion() {
    assertEquals("1.0.2", NpmCoordinatesHelper.highestStableVersion(
        Arrays.asList("1.0.1", "1.0.2", "1.1.0-beta.1", "2.0.0-rc.1")));
    assertNull(NpmCoordinatesHelper.highestStableVersion(Arrays.asList("1.0.0-beta.1")));
  }

  @Test
  public void testIsVersion() {
    assertTrue(NpmCoordinatesHelper.isVersion("1.0.0"));
    assertTrue(NpmCoordinatesHelper.isVersion("1.0.0-rc.1"));
    assertFalse(NpmCoordinatesHelper.isVersion("latest"));
  }
}
