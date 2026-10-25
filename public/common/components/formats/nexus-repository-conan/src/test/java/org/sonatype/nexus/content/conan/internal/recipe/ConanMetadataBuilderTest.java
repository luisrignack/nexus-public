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
package org.sonatype.nexus.content.conan.internal.recipe;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.sonatype.goodies.testsupport.TestSupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

/**
 * Tests for the JSON documents served by the Conan API: revision listings, {@code files} listings (with the MD5 of
 * every file), {@code download_urls} listings and the merge of the documents produced by group members.
 */
public class ConanMetadataBuilderTest
    extends TestSupport
{
  private static final String REFERENCE = "zlib/1.2.13@_/_";

  private static final String GROUP_URL = "http://localhost:8081/repository/conan-group";

  private static final String MEMBER_URL = "http://localhost:8081/repository/conan-proxy";

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  public void revisionsJson_listsNewestRevisionFirst() throws Exception {
    Map<String, OffsetDateTime> revisions = new LinkedHashMap<>();
    revisions.put("older", OffsetDateTime.parse("2024-01-01T00:00:00Z"));
    revisions.put("newer", OffsetDateTime.parse("2024-06-01T00:00:00Z"));

    JsonNode root = mapper.readTree(ConanMetadataBuilder.revisionsJson(REFERENCE, revisions, null));
    JsonNode entries = root.path("revisions");

    assertThat(entries.size(), is(2));
    assertThat(entries.get(0).path("info").path("revision").asText(), is("newer"));
    assertThat(entries.get(0).path("info").path("reference").asText(), is(REFERENCE));
    assertThat(entries.get(0).path("info").path("time").asText(), is("2024-06-01T00:00:00Z"));
    assertThat(entries.get(1).path("info").path("revision").asText(), is("older"));
    // no content_url unless a prefix was given
    assertThat(entries.get(0).path("content_url").isMissingNode(), is(true));
  }

  @Test
  public void revisionsJson_addsContentUrlPerRevision() throws Exception {
    Map<String, OffsetDateTime> revisions = new LinkedHashMap<>();
    revisions.put("r1", OffsetDateTime.parse("2024-01-01T00:00:00Z"));
    revisions.put("r2", OffsetDateTime.parse("2024-02-01T00:00:00Z"));

    String prefix = GROUP_URL + "/v2/conans/zlib/1.2.13/_/_/revisions";
    JsonNode root = mapper.readTree(ConanMetadataBuilder.revisionsJson(REFERENCE, revisions, prefix));

    assertThat(root.path("revisions").get(1).path("content_url").asText(),
        is(prefix + "/r1"));
    assertThat(root.path("revisions").get(0).path("content_url").asText(),
        is(prefix + "/r2"));
  }

  @Test
  public void filesJson_slashesFileNamesAndReportsMd5() throws Exception {
    Map<String, Map<String, Object>> files = new LinkedHashMap<>();
    files.put("conanfile.py", md5Of("tiny"));
    files.put("conanexport.tgz", md5Of("bigger payload"));

    JsonNode root = mapper.readTree(ConanMetadataBuilder.filesJson(REFERENCE, "r1", files));
    JsonNode stored = root.path("conanfile").path("files");

    assertThat(root.path("conanfile").path("reference").asText(), is(REFERENCE));
    assertThat(root.path("conanfile").path("revision").asText(), is("r1"));
    assertThat(stored.has("/conanfile.py"), is(true));
    assertThat(stored.has("/conanexport.tgz"), is(true));
    assertThat(stored.get("/conanfile.py").path("md5").asText(), is(md5Of("tiny").get("md5")));
    assertThat(stored.get("/conanfile.py").path("size").asLong(), is(4L));
    assertThat(stored.get("/conanexport.tgz").path("md5").asText().length(), is(32));
  }

  @Test
  public void filesJson_alreadySlashedNamesAreNotSlashedTwice() throws Exception {
    Map<String, Map<String, Object>> files = new LinkedHashMap<>();
    files.put("/conanfile.py", md5Of("tiny"));

    JsonNode root = mapper.readTree(ConanMetadataBuilder.filesJson(REFERENCE, "r1", files));

    assertThat(root.path("conanfile").path("files").has("//conanfile.py"), is(false));
    assertThat(root.path("conanfile").path("files").has("/conanfile.py"), is(true));
  }

  @Test
  public void downloadUrlsJson_sortsByLastModifiedAndDropsTheSortKey() throws Exception {
    Map<String, Map<String, Object>> files = new LinkedHashMap<>();
    files.put("conanfile.py", url("http://old/conanfile.py", 100L));
    files.put("conanexport.tgz", url("http://new/conanexport.tgz", 200L));

    JsonNode root = mapper.readTree(ConanMetadataBuilder.downloadUrlsJson(files));
    JsonNode entries = root.path("conanfile").path("files");

    assertThat(entries.fieldNames().next(), is("/conanexport.tgz"));
    assertThat(entries.get("/conanexport.tgz").path("url").asText(), is("http://new/conanexport.tgz"));
    assertThat(entries.get("/conanexport.tgz").path("lastModified").isMissingNode(), is(true));
    assertThat(entries.get("/conanfile.py").path("url").asText(), is("http://old/conanfile.py"));
  }

  @Test
  public void searchResultsJson_sortsReferences() {
    String json = ConanMetadataBuilder.searchResultsJson(
        java.util.Arrays.asList("zlib/1.2.13@_/_", "libpng/1.6@_/_", "abc/1@_/_"));

    assertThat(ConanMetadataBuilder.parseSearchResults(json),
        contains("abc/1@_/_", "libpng/1.6@_/_", "zlib/1.2.13@_/_"));
  }

  @Test
  public void mergeRevisionLists_unionsAndKeepsTheNewestTime() {
    Map<String, OffsetDateTime> left = new LinkedHashMap<>();
    left.put("shared", OffsetDateTime.parse("2024-01-01T00:00:00Z"));
    left.put("onlyLeft", OffsetDateTime.parse("2024-03-01T00:00:00Z"));

    Map<String, OffsetDateTime> right = new LinkedHashMap<>();
    right.put("shared", OffsetDateTime.parse("2024-05-01T00:00:00Z"));
    right.put("onlyRight", OffsetDateTime.parse("2024-02-01T00:00:00Z"));

    Map<String, OffsetDateTime> merged = ConanMetadataBuilder.mergeRevisionLists(left, right);

    // newest first: shared (05-01), onlyLeft (03-01), onlyRight (02-01)
    assertThat(merged.keySet(), contains("shared", "onlyLeft", "onlyRight"));
    assertThat(merged.get("shared"), is(OffsetDateTime.parse("2024-05-01T00:00:00Z")));
  }

  @Test
  public void mergeSearchResults_unionsWithoutDuplicates() {
    List<String> merged = ConanMetadataBuilder.mergeSearchResults(
        java.util.Arrays.asList("a/1@_/_", "b/1@_/_"),
        java.util.Arrays.asList("b/1@_/_", "c/1@_/_"));

    assertThat(merged, contains("a/1@_/_", "b/1@_/_", "c/1@_/_"));
  }

  @Test
  public void mergeRevisionBodies_rewritesContentUrlsToTheGroup() throws Exception {
    Map<String, String> bodies = new LinkedHashMap<>();
    bodies.put(MEMBER_URL, ConanMetadataBuilder.revisionsJson(
        REFERENCE,
        at("r1", "2024-01-01T00:00:00Z"),
        MEMBER_URL + "/v2/conans/zlib/1.2.13/_/_/revisions"));
    bodies.put("http://localhost:8081/repository/other", ConanMetadataBuilder.revisionsJson(
        REFERENCE,
        at("r2", "2024-06-01T00:00:00Z"),
        "http://localhost:8081/repository/other/v2/conans/zlib/1.2.13/_/_/revisions"));

    JsonNode merged = mapper.readTree(ConanMetadataBuilder.mergeRevisionBodies(bodies, GROUP_URL));
    JsonNode entries = merged.path("revisions");

    assertThat(entries.size(), is(2));
    assertThat(entries.get(0).path("info").path("revision").asText(), is("r2"));
    assertThat(entries.get(1).path("info").path("revision").asText(), is("r1"));
    assertThat(entries.get(0).path("content_url").asText(),
        is(GROUP_URL + "/v2/conans/zlib/1.2.13/_/_/revisions/r2"));
    assertThat(entries.get(1).path("content_url").asText(),
        is(GROUP_URL + "/v2/conans/zlib/1.2.13/_/_/revisions/r1"));
    assertThat(entries.get(1).path("content_url").asText().startsWith(MEMBER_URL), is(false));
    assertThat(merged.path("revisions").get(0).path("info").path("reference").asText(), is(REFERENCE));
  }

  @Test
  public void mergeRevisionBodies_emptyInputYieldsEmptyDocument() throws Exception {
    JsonNode merged = mapper.readTree(ConanMetadataBuilder.mergeRevisionBodies(new LinkedHashMap<>(), GROUP_URL));

    assertThat(merged.path("revisions").size(), is(0));
  }

  @Test
  public void mergeSearchBodies_unionsResults() {
    String merged = ConanMetadataBuilder.mergeSearchBodies(java.util.Arrays.asList(
        ConanMetadataBuilder.searchResultsJson(java.util.List.of("a/1@_/_")),
        ConanMetadataBuilder.searchResultsJson(java.util.List.of("b/1@_/_", "a/1@_/_"))));

    assertThat(ConanMetadataBuilder.parseSearchResults(merged), contains("a/1@_/_", "b/1@_/_"));
  }

  @Test
  public void parseRevisions_readsBackWhatWasWritten() {
    Map<String, OffsetDateTime> revisions = new LinkedHashMap<>();
    revisions.put("r1", OffsetDateTime.parse("2024-01-01T00:00:00Z"));

    Map<String, OffsetDateTime> parsed = ConanMetadataBuilder.parseRevisions(
        ConanMetadataBuilder.revisionsJson(REFERENCE, revisions, null));

    assertThat(parsed.size(), is(1));
    assertThat(parsed.get("r1"), is(OffsetDateTime.parse("2024-01-01T00:00:00Z")));
    assertThat(ConanMetadataBuilder.parseRevisions("not json").size(), is(0));
    assertThat(ConanMetadataBuilder.parseSearchResults(null), hasSize(0));
    assertThat(ConanMetadataBuilder.parseSearchResults("{\"results\":null}"), hasSize(0));
    assertThat(ConanMetadataBuilder.parseSearchResults("{\"results\":[\"a/1@_/_\"]}"),
        contains("a/1@_/_"));
  }

  // ----------------------------------------------------------------- helpers

  private static Map<String, OffsetDateTime> at(final String revision, final String time) {
    Map<String, OffsetDateTime> revisions = new LinkedHashMap<>();
    revisions.put(revision, OffsetDateTime.parse(time));
    return revisions;
  }

  private static Map<String, Object> md5Of(final String content) {
    Map<String, Object> attributes = new LinkedHashMap<>();
    attributes.put("md5", com.google.common.hash.Hashing.md5()
        .hashBytes(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());
    attributes.put("size", (long) content.length());
    return attributes;
  }

  private static Map<String, Object> url(final String value, final Long lastModified) {
    Map<String, Object> attributes = new LinkedHashMap<>();
    attributes.put("url", value);
    attributes.put("lastModified", lastModified);
    return attributes;
  }
}
