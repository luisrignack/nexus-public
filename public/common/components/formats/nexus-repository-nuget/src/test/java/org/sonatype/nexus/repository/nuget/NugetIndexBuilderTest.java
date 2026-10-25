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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.sonatype.goodies.testsupport.TestSupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Generation of the flat container version index, the registration index and the search results.
 */
public class NugetIndexBuilderTest
    extends TestSupport
{
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String BASE = "http://localhost:8081/repository/nuget-hosted";

  @Test
  public void versionsIndexIsSortedWithSemVerAndStripsBuildMetadata() throws Exception {
    JsonNode root = MAPPER.readTree(NugetIndexBuilder.versionsIndex(
        Arrays.asList("1.0.0+build2", "2.0.0", "1.0.0-beta", "1.0.9")));

    JsonNode versions = root.get("versions");
    assertEquals(4, versions.size());
    assertEquals("1.0.0-beta", versions.get(0).asText());
    assertEquals("1.0.0", versions.get(1).asText());
    assertEquals("1.0.9", versions.get(2).asText());
    assertEquals("2.0.0", versions.get(3).asText());
  }

  @Test
  public void registrationIndexListsEveryVersionWithItsCatalogEntry() throws Exception {
    List<NugetPackage> packages = Arrays.asList(
        packageOf("Sample.Package", "2.0.0", "second"),
        packageOf("Sample.Package", "1.0.0", "first"));

    JsonNode root = MAPPER.readTree(NugetIndexBuilder.registrationIndex(BASE, "Sample.Package", packages));

    assertEquals(BASE + "/v3/registration/sample.package/index.json", root.get("@id").asText());
    assertEquals(2, root.get("count").asInt());

    JsonNode page = root.get("items").get(0);
    assertEquals("1.0.0", page.get("lower").asText());
    assertEquals("2.0.0", page.get("upper").asText());
    assertEquals(2, page.get("count").asInt());

    JsonNode first = page.get("items").get(0);
    assertEquals("1.0.0", first.get("catalogEntry").get("version").asText());
    assertEquals("first", first.get("catalogEntry").get("description").asText());
    assertEquals(BASE + "/v3-flatcontainer/sample.package/1.0.0/sample.package.1.0.0.nupkg",
        first.get("packageContent").asText());

    assertEquals(2, root.get("versions").size());
    assertEquals("1.0.0", root.get("versions").get(0).get("catalogEntry").get("version").asText());
  }

  @Test
  public void registrationIndexForUnknownPackageIsEmpty() throws Exception {
    JsonNode root = MAPPER.readTree(NugetIndexBuilder.registrationIndex(BASE, "Missing.Package", Collections.emptyList()));

    assertEquals(0, root.get("count").asInt());
    assertTrue(root.get("items").isEmpty());
    assertTrue(root.get("versions").isEmpty());
  }

  @Test
  public void searchMatchesIdDescriptionAndTagsIgnoringCase() throws Exception {
    List<NugetPackage> packages = Arrays.asList(
        packageOf("Alpha.Lib", "1.0.0", "Library doing alpha things", "search tooling"),
        packageOf("Beta.Lib", "1.0.0", "unrelated description", "searchengine"));

    assertEquals(1, totalHits(packages, "ALPHA"));
    assertEquals("Alpha.Lib", firstHit(packages, "alpha").get("id").asText());
    assertEquals(2, totalHits(packages, "search"));
    assertEquals(1, totalHits(packages, "searchengine"));
    assertEquals(0, totalHits(packages, "does-not-exist"));
  }

  @Test
  public void searchPagesWithoutChangingTotalHits() throws Exception {
    List<NugetPackage> packages = Arrays.asList(
        packageOf("p1", "1.0.0", "search hit"),
        packageOf("p2", "1.0.0", "search hit"),
        packageOf("p3", "1.0.0", "search hit"),
        packageOf("p4", "1.0.0", "search hit"),
        packageOf("p5", "1.0.0", "search hit"));

    JsonNode root = MAPPER.readTree(NugetIndexBuilder.searchResults(BASE, packages, "search", 2, 2));

    assertEquals(5, root.get("totalHits").asInt());
    assertEquals(2, root.get("data").size());
    assertEquals("p3", root.get("data").get(0).get("id").asText());
    assertEquals("p4", root.get("data").get(1).get("id").asText());
  }

  @Test
  public void emptyQueryReturnsEverything() throws Exception {
    List<NugetPackage> packages = Arrays.asList(
        packageOf("p1", "1.0.0"),
        packageOf("p2", "1.0.0"));

    JsonNode root = MAPPER.readTree(NugetIndexBuilder.searchResults(BASE, packages, "  ", 0, -1));

    assertEquals(2, root.get("totalHits").asInt());
    assertEquals(2, root.get("data").size());
    assertTrue("a negative take falls back to the default page size",
        NugetIndexBuilder.DEFAULT_TAKE >= 2);
  }

  @Test
  public void groupMergesSearchResultsKeepingFirstEntryPerId() throws Exception {
    byte[] first = bytes("{\"totalHits\":2,\"data\":["
        + "{\"id\":\"Alpha.Lib\",\"version\":\"1.0.0\"},"
        + "{\"id\":\"Beta.Lib\",\"version\":\"1.0.0\"}]}");
    byte[] second = bytes("{\"totalHits\":2,\"data\":["
        + "{\"id\":\"alpha.lib\",\"version\":\"2.0.0\"},"
        + "{\"id\":\"Gamma.Lib\",\"version\":\"1.0.0\"}]}");

    JsonNode merged = MAPPER.readTree(NugetIndexBuilder.mergeSearchResults(Arrays.asList(first, second)));

    assertEquals(3, merged.get("data").size());
    JsonNode data = merged.get("data");
    assertEquals("Alpha.Lib", data.get(0).get("id").asText());
    assertEquals("1.0.0", data.get(0).get("version").asText());
    assertEquals("Beta.Lib", data.get(1).get("id").asText());
    assertEquals("Gamma.Lib", data.get(2).get("id").asText());
    assertEquals(4, merged.get("totalHits").asInt());
    assertFalse(merged.toString().contains("2.0.0"));
  }

  private static int totalHits(final List<NugetPackage> packages, final String query) throws Exception {
    return MAPPER.readTree(NugetIndexBuilder.searchResults(BASE, packages, query, 0, 20)).get("totalHits").asInt();
  }

  private static JsonNode firstHit(final List<NugetPackage> packages, final String query) throws Exception {
    return MAPPER.readTree(NugetIndexBuilder.searchResults(BASE, packages, query, 0, 20)).get("data").get(0);
  }

  private static NugetPackage packageOf(
      final String id,
      final String version,
      final String... descriptionAndTags)
  {
    String description = descriptionAndTags != null && descriptionAndTags.length > 0 ? descriptionAndTags[0] : null;
    String tags = descriptionAndTags != null && descriptionAndTags.length > 1 ? descriptionAndTags[1] : null;
    return new NugetPackage(id, version, null, description, null, null, null, null, tags, null, null, true);
  }

  private static byte[] bytes(final String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }
}
