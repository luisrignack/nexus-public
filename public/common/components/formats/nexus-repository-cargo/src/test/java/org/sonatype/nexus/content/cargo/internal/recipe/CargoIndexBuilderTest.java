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
package org.sonatype.nexus.content.cargo.internal.recipe;

import java.util.Arrays;
import java.util.List;

import org.sonatype.goodies.testsupport.TestSupport;
import org.sonatype.nexus.repository.cargo.CargoCrateSummary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link CargoIndexBuilder} tests: ndjson generation, semver ordering, checksums, config rewriting and search
 * filtering.
 */
public class CargoIndexBuilderTest
    extends TestSupport
{
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static String record(final String name, final String version, final String cksum) {
    return CargoIndexBuilder.recordFor(name, version, cksum, "2024-05-01T12:00:00Z");
  }

  //
  // semver ordering
  //

  @Test
  public void compareVersionsOrdersSemverNumerically() {
    assertTrue(CargoIndexBuilder.compareVersions("1.0.0", "1.0.1") < 0);
    assertTrue(CargoIndexBuilder.compareVersions("1.0.9", "1.0.10") < 0);
    assertTrue(CargoIndexBuilder.compareVersions("1.9.0", "1.10.0") < 0);
    assertTrue(CargoIndexBuilder.compareVersions("2.0.0", "10.0.0") < 0);
    assertTrue(CargoIndexBuilder.compareVersions("0.9.9", "1.0.0") < 0);
    assertEquals(0, CargoIndexBuilder.compareVersions("1.2.3", "1.2.3"));
    // build metadata is ignored
    assertEquals(0, CargoIndexBuilder.compareVersions("1.2.3+abc", "1.2.3"));
  }

  @Test
  public void compareVersionsOrdersPrereleasesBeforeRelease() {
    assertTrue(CargoIndexBuilder.compareVersions("1.0.0-alpha", "1.0.0") < 0);
    assertTrue(CargoIndexBuilder.compareVersions("1.0.0-alpha", "1.0.0-alpha.1") < 0);
    assertTrue(CargoIndexBuilder.compareVersions("1.0.0-alpha.1", "1.0.0-alpha.beta") < 0);
    assertTrue(CargoIndexBuilder.compareVersions("1.0.0-beta", "1.0.0-rc.1") < 0);
    assertTrue(CargoIndexBuilder.compareVersions("1.0.0-rc.1", "1.0.0") < 0);
    // numeric identifiers sort before alphanumeric ones
    assertTrue(CargoIndexBuilder.compareVersions("1.0.0-1", "1.0.0-alpha") < 0);
  }

  //
  // ndjson records
  //

  @Test
  public void mergeRecordSortsByVersionAscending() {
    String cksum = CargoIndexBuilder.sha256Hex("payload".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    String ndjson = "";
    ndjson = CargoIndexBuilder.mergeRecord(ndjson, record("serde", "10.0.0", cksum));
    ndjson = CargoIndexBuilder.mergeRecord(ndjson, record("serde", "1.0.0", cksum));
    ndjson = CargoIndexBuilder.mergeRecord(ndjson, record("serde", "2.0.0", cksum));
    ndjson = CargoIndexBuilder.mergeRecord(ndjson, record("serde", "1.0.0-rc.1", cksum));

    String[] lines = ndjson.split("\n");
    assertEquals(4, lines.length);
    assertTrue(ndjson.endsWith("\n"));

    List<com.fasterxml.jackson.databind.node.ObjectNode> records = CargoIndexBuilder.parseRecords(ndjson);
    assertEquals("1.0.0-rc.1", records.get(0).path("vers").asText());
    assertEquals("1.0.0", records.get(1).path("vers").asText());
    assertEquals("2.0.0", records.get(2).path("vers").asText());
    assertEquals("10.0.0", records.get(3).path("vers").asText());

    assertEquals("10.0.0", CargoIndexBuilder.maxVersion(ndjson));
    assertEquals("serde", records.get(0).path("name").asText());
    assertEquals(cksum, records.get(0).path("cksum").asText());
    assertTrue(records.get(0).path("deps").isArray());
    assertTrue(records.get(0).path("features").isObject());
    assertFalse(records.get(0).path("yanked").asBoolean());
  }

  @Test
  public void mergeRecordReplacesTheSameVersion() {
    String first = CargoIndexBuilder.sha256Hex("one".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    String second = CargoIndexBuilder.sha256Hex("two".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    String ndjson = CargoIndexBuilder.mergeRecord("", record("serde", "1.0.0", first));
    ndjson = CargoIndexBuilder.mergeRecord(ndjson, record("serde", "2.0.0", first));
    ndjson = CargoIndexBuilder.mergeRecord(ndjson, record("serde", "1.0.0", second));

    List<com.fasterxml.jackson.databind.node.ObjectNode> records = CargoIndexBuilder.parseRecords(ndjson);
    assertEquals(2, records.size());
    assertEquals("1.0.0", records.get(0).path("vers").asText());
    assertEquals(second, records.get(0).path("cksum").asText());
    assertEquals("2.0.0", records.get(1).path("vers").asText());

    String remaining = CargoIndexBuilder.removeRecord(ndjson, "1.0.0");
    assertEquals(1, CargoIndexBuilder.parseRecords(remaining).size());
    assertEquals("", CargoIndexBuilder.removeRecord(remaining, "2.0.0"));
  }

  @Test
  public void sha256ChecksumIsComputedFromTheCrateBytes() {
    // sha256("abc")
    assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
        CargoIndexBuilder.sha256Hex("abc".getBytes(java.nio.charset.StandardCharsets.UTF_8)));

    String cksum = CargoIndexBuilder.sha256Hex("abc".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    String record = CargoIndexBuilder.withCksum(record("serde", "1.0.0", ""), cksum);
    assertEquals(cksum, CargoIndexBuilder.jsonField(record, "cksum"));

    String synthesized = CargoIndexBuilder.recordFor("serde", "1.0.0", cksum, "2024-05-01T12:00:00Z");
    assertEquals("serde", CargoIndexBuilder.jsonField(synthesized, "name"));
    assertEquals("1.0.0", CargoIndexBuilder.jsonField(synthesized, "vers"));
    assertEquals(cksum, CargoIndexBuilder.jsonField(synthesized, "cksum"));
    assertEquals("2024-05-01T12:00:00Z", CargoIndexBuilder.jsonField(synthesized, "pubtime"));
  }

  @Test
  public void validateRecordChecksPathAgainstBody() {
    String body = record("serde", "1.0.0", "deadbeef");
    assertNull(CargoIndexBuilder.validateRecord(body, "serde", "1.0.0"));
    assertNull(CargoIndexBuilder.validateRecord(body, "SERDE", "1.0.0"));

    String nameMismatch = CargoIndexBuilder.validateRecord(body, "tokio", "1.0.0");
    assertNotNull(nameMismatch);
    assertTrue(nameMismatch, nameMismatch.contains("Crate name mismatch"));

    String versionMismatch = CargoIndexBuilder.validateRecord(body, "serde", "2.0.0");
    assertNotNull(versionMismatch);
    assertTrue(versionMismatch, versionMismatch.contains("Version mismatch"));

    String invalid = CargoIndexBuilder.validateRecord("not json", "serde", "1.0.0");
    assertNotNull(invalid);
    assertTrue(invalid, invalid.contains("Invalid index record"));

    String missing = CargoIndexBuilder.validateRecord("{\"vers\":\"1.0.0\"}", "serde", "1.0.0");
    assertNotNull(missing);
    assertTrue(missing, missing.contains("'name'"));
  }

  //
  // config.json
  //

  @Test
  public void configJsonPointsAtTheRepository() throws Exception {
    JsonNode config = MAPPER.readTree(
        CargoIndexBuilder.configJson("http://localhost:8081/repository/cargo-hosted"));
    assertEquals("http://localhost:8081/repository/cargo-hosted/api/v1/crates", config.path("dl").asText());
    assertEquals("http://localhost:8081/repository/cargo-hosted/api", config.path("api").asText());

    JsonNode withSlash = MAPPER.readTree(
        CargoIndexBuilder.configJson("http://localhost:8081/repository/cargo-hosted/"));
    assertEquals("http://localhost:8081/repository/cargo-hosted/api/v1/crates", withSlash.path("dl").asText());
  }

  @Test
  public void rewriteConfigRepointsDlAndApiAndKeepsUnknownFields() throws Exception {
    String upstream = "{\"dl\":\"https://crates.io/api/v1/crates\",\"api\":\"https://crates.io/api\","
        + "\"auth-required\":false}";

    JsonNode rewritten = MAPPER.readTree(
        CargoIndexBuilder.rewriteConfig(upstream, "http://localhost:8081/repository/cargo-proxy"));

    assertEquals("http://localhost:8081/repository/cargo-proxy/api/v1/crates", rewritten.path("dl").asText());
    assertEquals("http://localhost:8081/repository/cargo-proxy/api", rewritten.path("api").asText());
    assertFalse(rewritten.path("auth-required").asBoolean(true));

    // an unparsable upstream document falls back to a freshly generated configuration
    JsonNode fallback = MAPPER.readTree(CargoIndexBuilder.rewriteConfig("<html>404</html>",
        "http://localhost:8081/repository/cargo-proxy"));
    assertEquals("http://localhost:8081/repository/cargo-proxy/api/v1/crates", fallback.path("dl").asText());
  }

  @Test
  public void jsonFieldReadsUpstreamBases() {
    String upstream = "{\"dl\":\"https://crates.io/api/v1/crates\",\"api\":\"https://crates.io/api\"}";
    assertEquals("https://crates.io/api/v1/crates", CargoIndexBuilder.jsonField(upstream, "dl"));
    assertEquals("https://crates.io/api", CargoIndexBuilder.jsonField(upstream, "api"));
    assertEquals("", CargoIndexBuilder.jsonField(upstream, "missing"));
    assertEquals("", CargoIndexBuilder.jsonField("not json", "dl"));
  }

  //
  // search
  //

  private static List<CargoCrateSummary> crates() {
    return Arrays.asList(
        new CargoCrateSummary("serde", "A serialization framework", "1.0.200", 1000, "2024-05-01T00:00:00Z"),
        new CargoCrateSummary("serde_json", "JSON for serde", "1.0.100", 900, "2024-05-02T00:00:00Z"),
        new CargoCrateSummary("tokio", "An asynchronous runtime", "1.35.0", 5000, "2024-05-03T00:00:00Z"),
        new CargoCrateSummary("axum", "Ergonomic web framework", "0.7.0", 700, "2024-05-04T00:00:00Z"),
        new CargoCrateSummary("hyper", "An HTTP implementation", "1.1.0", 4000, "2024-05-05T00:00:00Z"));
  }

  @Test
  public void searchFiltersByName() {
    List<CargoCrateSummary> matched = CargoIndexBuilder.filter(crates(), "serde");
    assertEquals(2, matched.size());
    assertEquals("serde", matched.get(0).getName());
    assertEquals("serde_json", matched.get(1).getName());

    // case insensitive
    assertEquals(2, CargoIndexBuilder.filter(crates(), "SERDE").size());
    // blank query matches everything
    assertEquals(5, CargoIndexBuilder.filter(crates(), "").size());
    assertEquals(5, CargoIndexBuilder.filter(crates(), null).size());
    // no match
    assertTrue(CargoIndexBuilder.filter(crates(), "nosuchcrate").isEmpty());
  }

  @Test
  public void searchFiltersByDescriptionAndRequiresEveryTerm() {
    List<CargoCrateSummary> byDescription = CargoIndexBuilder.filter(crates(), "runtime");
    assertEquals(1, byDescription.size());
    assertEquals("tokio", byDescription.get(0).getName());

    // every term has to appear in name or description
    assertEquals(1, CargoIndexBuilder.filter(crates(), "async runtime").size());
    assertTrue(CargoIndexBuilder.filter(crates(), "runtime serialization").isEmpty());
  }

  @Test
  public void searchResponsePaginatesAndReportsTotal() throws Exception {
    JsonNode page2 = MAPPER.readTree(CargoIndexBuilder.searchResponse(crates(), "", 2, 2));
    assertEquals(5, page2.path("meta").path("total").asInt());
    assertEquals(2, page2.path("crates").size());
    assertEquals("tokio", page2.path("crates").get(0).path("id").asText());
    assertEquals("axum", page2.path("crates").get(1).path("id").asText());

    JsonNode first = MAPPER.readTree(CargoIndexBuilder.searchResponse(crates(), "serde", 1, 10));
    assertEquals(2, first.path("meta").path("total").asInt());
    JsonNode entry = first.path("crates").get(0);
    assertEquals("serde", entry.path("id").asText());
    assertEquals("serde", entry.path("name").asText());
    assertEquals("1.0.200", entry.path("max_version").asText());
    assertEquals("1.0.200", entry.path("newest_version").asText());
    assertEquals("A serialization framework", entry.path("description").asText());
    assertEquals(1000, entry.path("downloads").asLong());
    assertEquals("2024-05-01T00:00:00Z", entry.path("updated_at").asText());

    // out of range pages are empty but keep the total
    JsonNode beyond = MAPPER.readTree(CargoIndexBuilder.searchResponse(crates(), "", 99, 10));
    assertEquals(0, beyond.path("crates").size());
    assertEquals(5, beyond.path("meta").path("total").asInt());
  }

  @Test
  public void detailResponseListsEveryRecord() throws Exception {
    String cksum = CargoIndexBuilder.sha256Hex("abc".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    String ndjson = CargoIndexBuilder.mergeRecord("", record("serde", "1.0.0", cksum));
    ndjson = CargoIndexBuilder.mergeRecord(ndjson, record("serde", "1.0.1", cksum));

    CargoCrateSummary summary = new CargoCrateSummary("serde", "A serialization framework", "1.0.1", 1000,
        "2024-05-01T00:00:00Z");
    JsonNode response = MAPPER.readTree(CargoIndexBuilder.detailResponse(summary, ndjson));

    assertEquals("serde", response.path("crate").path("id").asText());
    assertEquals("1.0.1", response.path("crate").path("max_version").asText());
    assertEquals(2, response.path("versions").size());
    assertEquals("1.0.0", response.path("versions").get(0).path("num").asText());
    assertEquals("1.0.1", response.path("versions").get(1).path("num").asText());
    assertEquals(cksum, response.path("versions").get(1).path("cksum").asText());
    assertEquals("serde", response.path("versions").get(1).path("crate").asText());
  }

  @Test
  public void mergeSearchKeepsFirstMemberOnDuplicates() throws Exception {
    String hosted = "{\"crates\":[{\"id\":\"serde\",\"max_version\":\"1.0.0\"},"
        + "{\"id\":\"tokio\",\"max_version\":\"1.0.0\"}],\"meta\":{\"total\":2}}";
    String proxied = "{\"crates\":[{\"id\":\"serde\",\"max_version\":\"2.0.0\"},"
        + "{\"id\":\"hyper\",\"max_version\":\"1.0.0\"}],\"meta\":{\"total\":3}}";

    JsonNode merged = MAPPER.readTree(CargoIndexBuilder.mergeSearch(Arrays.asList(hosted, proxied)));
    assertEquals(3, merged.path("crates").size());
    assertEquals(3, merged.path("meta").path("total").asInt());
    assertEquals("serde", merged.path("crates").get(0).path("id").asText());
    assertEquals("1.0.0", merged.path("crates").get(0).path("max_version").asText());
    assertEquals("tokio", merged.path("crates").get(1).path("id").asText());
    assertEquals("hyper", merged.path("crates").get(2).path("id").asText());

    // broken member responses are skipped rather than failing the group
    JsonNode withBroken = MAPPER.readTree(CargoIndexBuilder.mergeSearch(Arrays.asList("garbage", hosted)));
    assertEquals(2, withBroken.path("crates").size());
  }
}
