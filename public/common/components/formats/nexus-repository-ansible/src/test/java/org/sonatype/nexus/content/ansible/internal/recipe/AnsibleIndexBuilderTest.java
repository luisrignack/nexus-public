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
package org.sonatype.nexus.content.ansible.internal.recipe;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.sonatype.goodies.testsupport.Test5Support;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

class AnsibleIndexBuilderTest
    extends Test5Support
{
  private static final String LIST_A =
      "{\"data\":[{\"name\":\"general\",\"namespace\":\"community\",\"latest_version\":\"1.0.0\","
          + "\"versions_url\":\"http://member-a/repository/a/api/v3/plugin/ansible/content/published"
          + "/collections/index/community/general/versions\"}],\"meta\":{\"count\":1}}";

  private static final String LIST_B =
      "{\"data\":[{\"name\":\"general\",\"namespace\":\"community\",\"latest_version\":\"2.0.0\"},"
          + "{\"name\":\"posix\",\"namespace\":\"ansible\",\"latest_version\":\"1.5.0\"}],\"meta\":{\"count\":2}}";

  private static final String VERSIONS_A =
      "{\"data\":[{\"version\":\"1.0.0\",\"artifact\":{\"filename\":\"community.general-1.0.0.tar.gz\"}}]}";

  private static final String VERSIONS_B =
      "{\"data\":[{\"version\":\"2.0.0\",\"artifact\":{\"filename\":\"community.general-2.0.0.tar.gz\"}},"
          + "{\"version\":\"1.1.0\",\"artifact\":{\"filename\":\"community.general-1.1.0.tar.gz\"}}]}";

  @Test
  void comparesVersionsNumericallySegmentBySegment() {
    assertThat(AnsibleIndexBuilder.compareVersions("1.0.0", "1.0.0"), is(0));
    assertThat(AnsibleIndexBuilder.compareVersions("1.10.0", "1.9.0") > 0, is(true));
    assertThat(AnsibleIndexBuilder.compareVersions("2.0.0", "10.0.0") < 0, is(true));
    assertThat(AnsibleIndexBuilder.compareVersions("1.0.1", "1.0.0") > 0, is(true));
    assertThat(AnsibleIndexBuilder.compareVersions("1.0", "1.0.1") < 0, is(true));
    assertThat(AnsibleIndexBuilder.compareVersions("1.0.0", "1.0.0-rc1") > 0, is(false));
  }

  @Test
  void mergesCollectionListsFromMembers() {
    byte[] merged = AnsibleIndexBuilder.mergeCollectionLists(List.of(LIST_A, LIST_B));
    String document = new String(merged, StandardCharsets.UTF_8);

    // both members know community.general; the first wins, ansible.posix is added.
    // the document carries the list twice: `data` (Galaxy v3) and `results` (Galaxy v2).
    assertThat(count(document, "\"namespace\":\"community\""), is(2));
    assertThat(count(document, "\"namespace\":\"ansible\""), is(2));
    assertThat(document, containsString("\"count\":2"));
  }

  @Test
  void mergesVersionListsOrderingNewestFirst() {
    byte[] merged = AnsibleIndexBuilder.mergeVersionLists(List.of(VERSIONS_A, VERSIONS_B));
    String document = new String(merged, StandardCharsets.UTF_8);

    assertThat(document.indexOf("\"version\":\"2.0.0\"") < document.indexOf("\"version\":\"1.1.0\""), is(true));
    assertThat(document.indexOf("\"version\":\"1.1.0\"") < document.indexOf("\"version\":\"1.0.0\""), is(true));
    assertThat(document, containsString("\"count\":3"));
  }

  @Test
  void mergeIgnoresEmptyAndInvalidDocuments() {
    byte[] merged = AnsibleIndexBuilder.mergeCollectionLists(
        java.util.Arrays.asList("", null, "not json"));
    assertThat(new String(merged, StandardCharsets.UTF_8), containsString("\"count\":0"));
  }

  @Test
  void rewritesMemberUrlsToTheGroup() {
    String rewritten = AnsibleIndexBuilder.rewrite(
        LIST_A, "http://member-a/repository/a", "http://localhost:8081/repository/g");

    assertThat(rewritten, not(containsString("member-a")));
    assertThat(rewritten, containsString("http://localhost:8081/repository/g/api/v3/"));
  }

  @Test
  void rewriteIsANoOpForSameOrMissingBase() {
    assertThat(AnsibleIndexBuilder.rewrite(LIST_A, "x", "x"), is(LIST_A));
    assertThat(AnsibleIndexBuilder.rewrite(LIST_A, null, "y"), is(LIST_A));
    assertThat(AnsibleIndexBuilder.rewrite(LIST_A, "", "y"), is(LIST_A));
    assertThat(AnsibleIndexBuilder.rewrite(null, "x", "y"), is(nullValue()));
  }

  @Test
  void rootDocumentPointsAtThisRepository() {
    String root = new String(AnsibleIndexBuilder.root("http://localhost:8081/repository/g"),
        StandardCharsets.UTF_8);

    assertThat(root, containsString("http://localhost:8081/repository/g/api/v3/plugin/ansible/content/published"));
    assertThat(root, containsString("\"galaxy_api_version\":\"v3\""));
    assertThat(root, containsString("\"available_versions\":{\"v3\":\"v3/\"}"));
  }

  @Test
  void apiDiscoveryDocumentListsV3AndV1() {
    String discovery = new String(AnsibleIndexBuilder.apiDiscovery(), StandardCharsets.UTF_8);

    assertThat(discovery, containsString("\"available_versions\":{\"v3\":\"v3/\",\"v1\":\"v1/\"}"));
  }

  @Test
  void mergeCollectionDetailKeepsFirstMemberDocument() {
    String detail = "{\"name\":\"general\",\"namespace\":\"community\",\"version_count\":1,"
        + "\"download_url\":\"http://member-a/repository/a/api/v3/collections/community/general/1.0.0/download\"}";
    String merged = AnsibleIndexBuilder.mergeCollectionDetail(
        List.of(detail), "http://localhost:8081/repository/g");

    assertThat(merged, containsString("\"name\":\"general\""));
    assertThat(merged, not(containsString("member-a")));
  }

  @Test
  void mergeVersionDetailRequiresArtifact() {
    assertThat(AnsibleIndexBuilder.mergeVersionDetail(List.of("{\"name\":\"x\"}"), "base"), is(nullValue()));
    String version = "{\"version\":\"1.0.0\",\"artifact\":{\"filename\":\"a.tar.gz\"}}";
    assertThat(AnsibleIndexBuilder.mergeVersionDetail(List.of(version), "base"), is(not(nullValue())));
  }

  private static int count(final String haystack, final String needle) {
    int found = 0;
    int index = haystack.indexOf(needle);
    while (index >= 0) {
      found++;
      index = haystack.indexOf(needle, index + 1);
    }
    return found;
  }
}
