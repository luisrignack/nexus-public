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
package org.sonatype.nexus.content.pub.internal.recipe;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import org.sonatype.nexus.repository.pub.PubPackageMetadata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class PubMetadataBuilderTest
{
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String REPOSITORY_URL = "http://nexus:8081/repository/pub-group";

  @Test
  void buildsMetadataWithRewrittenArchiveUrlsAndChecksums() throws Exception {
    PubPackageMetadata first = metadata("http", "1.0.0");
    PubPackageMetadata second = metadata("http", "1.2.0");

    JsonNode document = MAPPER.readTree(PubMetadataBuilder.build(
        "http", REPOSITORY_URL, Arrays.asList(first, second)));

    assertThat(document.path("name").asText(), equalTo("http"));
    assertThat(document.path("latest").path("version").asText(), equalTo("1.2.0"));
    assertThat(document.path("versions").size(), equalTo(2));
    assertThat(document.path("versions").get(0).path("archive_url").asText(),
        equalTo(REPOSITORY_URL + "/packages/http/versions/1.2.0.tar.gz"));
    assertThat(document.path("latest").path("archive_sha256").asText(), equalTo("sha-1.2.0"));
    assertThat(document.path("latest").path("pubspec").path("name").asText(), equalTo("http"));
  }

  @Test
  void rewritesUpstreamArchiveUrlsBeforeCaching() throws Exception {
    byte[] upstream = """
        {
          "name": "http",
          "latest": {
            "version": "1.2.0",
            "archive_url": "https://pub.dev/packages/http/versions/1.2.0.tar.gz",
            "archive_sha256": "abc"
          },
          "versions": [
            {
              "version": "1.0.0",
              "archive_url": "https://pub.dev/packages/http/versions/1.0.0.tar.gz"
            }
          ]
        }
        """.getBytes(StandardCharsets.UTF_8);

    JsonNode rewritten = MAPPER.readTree(PubMetadataBuilder.rewrite(REPOSITORY_URL, upstream));

    assertThat(rewritten.path("latest").path("archive_url").asText(),
        equalTo(REPOSITORY_URL + "/packages/http/versions/1.2.0.tar.gz"));
    assertThat(rewritten.path("versions").get(0).path("archive_url").asText(),
        equalTo(REPOSITORY_URL + "/packages/http/versions/1.0.0.tar.gz"));
    assertThat(rewritten.path("latest").path("archive_sha256").asText(), equalTo("abc"));
  }

  @Test
  void mergesVersionsOfGroupMembers() throws Exception {
    byte[] first = PubMetadataBuilder.build("http", "http://nexus/repository/pub-a",
        Collections.singletonList(metadata("http", "1.0.0")));
    byte[] second = PubMetadataBuilder.build("http", "http://nexus/repository/pub-b",
        Collections.singletonList(metadata("http", "1.2.0")));

    JsonNode merged = MAPPER.readTree(PubMetadataBuilder.merge(
        "http", REPOSITORY_URL, Arrays.asList(first, second)));

    assertThat(merged.path("versions").size(), equalTo(2));
    assertThat(merged.path("versions").get(0).path("archive_url").asText(),
        equalTo(REPOSITORY_URL + "/packages/http/versions/1.0.0.tar.gz"));
  }

  private static PubPackageMetadata metadata(final String name, final String version) {
    Map<String, Object> pubspec = Map.of("name", name, "version", version);
    return new PubPackageMetadata(name, version, pubspec, "sha-" + version, null);
  }
}
