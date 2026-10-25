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
package org.sonatype.nexus.repository.nuget;

import java.util.Arrays;
import java.util.List;

import org.sonatype.goodies.testsupport.TestSupport;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Coordinate normalization, storage layout and SemVer comparison.
 */
public class NugetCoordinatesHelperTest
    extends TestSupport
{
  @Test
  public void normalizesIdAndVersion() {
    assertEquals("newtonsoft.json", NugetCoordinatesHelper.normalizeId("  Newtonsoft.Json "));
    assertEquals("", NugetCoordinatesHelper.normalizeId(null));
    assertEquals("1.2.3", NugetCoordinatesHelper.normalizeVersion("1.2.3+build.45"));
    assertEquals("1.2.3-beta", NugetCoordinatesHelper.normalizeVersion(" 1.2.3-beta "));
    assertEquals("", NugetCoordinatesHelper.normalizeVersion(null));
  }

  @Test
  public void validatesCoordinates() {
    assertTrue(NugetCoordinatesHelper.isValidId("Newtonsoft.Json"));
    assertTrue(NugetCoordinatesHelper.isValidId("some_package-1"));
    assertFalse(NugetCoordinatesHelper.isValidId("bad id"));
    assertFalse(NugetCoordinatesHelper.isValidId(""));
    assertFalse(NugetCoordinatesHelper.isValidId(null));

    assertTrue(NugetCoordinatesHelper.isValidVersion("1.0.0"));
    assertTrue(NugetCoordinatesHelper.isValidVersion("1.0.0-beta.1"));
    assertTrue(NugetCoordinatesHelper.isValidVersion("1.0.0+metadata"));
    assertFalse(NugetCoordinatesHelper.isValidVersion("not-a-version"));
    assertFalse(NugetCoordinatesHelper.isValidVersion(""));
  }

  @Test
  public void buildsV3StorageLayout() {
    assertEquals(
        "/v3-flatcontainer/newtonsoft.json/13.0.1/newtonsoft.json.13.0.1.nupkg",
        NugetCoordinatesHelper.nupkgPath("Newtonsoft.Json", "13.0.1"));
    assertEquals(
        "/v3-flatcontainer/newtonsoft.json/13.0.1/newtonsoft.json.13.0.1.nuspec",
        NugetCoordinatesHelper.nuspecPath("Newtonsoft.Json", "13.0.1"));
    assertEquals(
        "http://localhost:8081/repository/nuget-hosted/v3-flatcontainer/newtonsoft.json/13.0.1/newtonsoft.json.13.0.1.nupkg",
        NugetCoordinatesHelper.packageContentUrl("http://localhost:8081/repository/nuget-hosted/",
            "Newtonsoft.Json", "13.0.1"));
  }

  @Test
  public void normalizesAssetPaths() {
    assertEquals("/v3-flatcontainer/a/1.0.0/a.1.0.0.nupkg",
        NugetCoordinatesHelper.normalizePath("v3-flatcontainer/a/1.0.0/a.1.0.0.nupkg"));
    assertEquals("/v3-flatcontainer/a/1.0.0/a.1.0.0.nupkg",
        NugetCoordinatesHelper.normalizePath("/v3-flatcontainer/a/1.0.0/a.1.0.0.nupkg"));
    assertEquals("/", NugetCoordinatesHelper.normalizePath(""));
  }

  @Test
  public void comparesVersionsWithSemVerSemantics() {
    assertTrue(NugetCoordinatesHelper.compareVersions("1.0.0", "1.0.1") < 0);
    assertTrue(NugetCoordinatesHelper.compareVersions("1.0.9", "1.0.10") < 0);
    assertTrue(NugetCoordinatesHelper.compareVersions("1.0.0-beta", "1.0.0") < 0);
    assertTrue(NugetCoordinatesHelper.compareVersions("1.0.0-alpha", "1.0.0-beta") < 0);
    assertTrue(NugetCoordinatesHelper.compareVersions("1.0.0-beta", "1.0.0-beta.2") < 0);
    assertTrue(NugetCoordinatesHelper.compareVersions("2.0.0", "10.0.0") < 0);
    assertEquals(0, NugetCoordinatesHelper.compareVersions("1.0.0+build1", "1.0.0+build2"));
    assertEquals(0, NugetCoordinatesHelper.compareVersions("1.0", "1.0.0"));
  }

  @Test
  public void sortsAndDeduplicatesVersions() {
    List<String> sorted = NugetCoordinatesHelper.sortVersions(
        Arrays.asList("1.0.0", "1.0.0-beta", "0.9.0", "1.0.0+build7", "1.0.10", "1.0.9", "1.0.0"));

    assertEquals(Arrays.asList("0.9.0", "1.0.0-beta", "1.0.0", "1.0.9", "1.0.10"), sorted);
  }
}
