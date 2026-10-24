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
package org.sonatype.nexus.sbom;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SbomGeneratorTest
{
  private final SbomGenerator underTest = new SbomGenerator();

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void cycloneDxDocumentContainsMavenComponentWithPurlAndHashes() {
    Map<String, String> checksums = new LinkedHashMap<>();
    checksums.put("sha256", "ab12");
    checksums.put("sha1", "cd34");
    SbomGenerator.ComponentData component = new SbomGenerator.ComponentData(
        "maven", "com.example", "foo", "1.0.0", "Apache-2.0", checksums, List.of());

    JsonNode doc = underTest.generate(SbomFormat.CYCLONEDX, "maven-hosted", List.of(component));

    assertThat(doc.get("bomFormat").asText()).isEqualTo("CycloneDX");
    assertThat(doc.get("specVersion").asText()).isEqualTo("1.5");
    assertThat(doc.get("serialNumber").asText()).startsWith("urn:uuid:");

    JsonNode metadata = doc.get("metadata");
    assertThat(metadata.get("component").get("name").asText()).isEqualTo("nexus:maven-hosted");
    assertThat(metadata.get("component").get("bom-ref").asText()).isEqualTo("nexus-repo:maven-hosted");
    assertThat(metadata.get("tools").get(0).get("name").asText()).isEqualTo(SbomGenerator.TOOL_NAME);

    JsonNode entry = doc.get("components").get(0);
    assertThat(entry.get("purl").asText()).isEqualTo("pkg:maven/com.example/foo@1.0.0");
    assertThat(entry.get("bom-ref").asText()).isEqualTo("pkg:maven/com.example/foo@1.0.0");
    assertThat(entry.get("group").asText()).isEqualTo("com.example");
    assertThat(entry.get("name").asText()).isEqualTo("foo");
    assertThat(entry.get("version").asText()).isEqualTo("1.0.0");

    JsonNode hashes = entry.get("hashes");
    assertThat(hashes).hasSize(2);
    assertThat(hashes.get(0).get("alg").asText()).isEqualTo("SHA-256");
    assertThat(hashes.get(0).get("content").asText()).isEqualTo("ab12");

    JsonNode licenses = entry.get("licenses");
    assertThat(licenses.get(0).get("license").get("id").asText()).isEqualTo("Apache-2.0");

    JsonNode dependencies = doc.get("dependencies");
    assertThat(dependencies.get(0).get("ref").asText()).isEqualTo("nexus-repo:maven-hosted");
    assertThat(dependencies.get(0).get("dependsOn").get(0).asText()).isEqualTo("pkg:maven/com.example/foo@1.0.0");
    assertThat(dependencies.get(1).get("ref").asText()).isEqualTo("pkg:maven/com.example/foo@1.0.0");
  }

  @Test
  void npmScopedComponentProducesEncodedPurlAndMergedName() {
    SbomGenerator.ComponentData component = new SbomGenerator.ComponentData(
        "npm", "@demo-scope", "scoped-pkg", "1.0.0", null, Map.of(), List.of());

    JsonNode doc = underTest.generate(SbomFormat.CYCLONEDX, "npm-hosted", List.of(component));
    JsonNode entry = doc.get("components").get(0);

    assertThat(entry.get("purl").asText()).isEqualTo("pkg:npm/%40demo-scope/scoped-pkg@1.0.0");
    assertThat(entry.get("name").asText()).isEqualTo("@demo-scope/scoped-pkg");
    assertThat(entry.has("group")).isFalse();
  }

  @Test
  void pypiComponentIsNormalizedAccordingToPep503() {
    SbomGenerator.ComponentData component = new SbomGenerator.ComponentData(
        "pypi", null, "Demo.Project_Name", "2.0", null, Map.of(), List.of());

    JsonNode doc = underTest.generate(SbomFormat.CYCLONEDX, "pypi-hosted", List.of(component));

    assertThat(doc.get("components").get(0).get("purl").asText()).isEqualTo("pkg:pypi/demo-project-name@2.0");
  }

  @Test
  void npmDependenciesAreLinkedOnlyWhenPresentInInventory() {
    SbomGenerator.ComponentData isOdd = new SbomGenerator.ComponentData(
        "npm", null, "is-odd", "1.0.0", null, Map.of(), List.of("is-number"));
    SbomGenerator.ComponentData isNumber = new SbomGenerator.ComponentData(
        "npm", null, "is-number", "7.0.0", null, Map.of(), List.of());
    SbomGenerator.ComponentData lodash = new SbomGenerator.ComponentData(
        "npm", null, "left-pad", "1.3.0", null, Map.of(), List.of("lodash"));

    JsonNode doc = underTest.generate(SbomFormat.CYCLONEDX, "npm-hosted",
        List.of(isOdd, isNumber, lodash));

    JsonNode dependencies = doc.get("dependencies");
    JsonNode isOddDependency = findDependency(dependencies, "pkg:npm/is-odd@1.0.0");
    assertThat(isOddDependency.get("dependsOn").get(0).asText()).isEqualTo("pkg:npm/is-number@7.0.0");

    JsonNode leftPadDependency = findDependency(dependencies, "pkg:npm/left-pad@1.3.0");
    assertThat(leftPadDependency.get("dependsOn")).isEmpty();
  }

  @Test
  void componentWithoutVersionProducesPurlWithoutVersionPart() {
    SbomGenerator.ComponentData component = new SbomGenerator.ComponentData(
        "raw", null, "archive.tar.gz", null, null, Map.of(), List.of());

    JsonNode doc = underTest.generate(SbomFormat.CYCLONEDX, "raw-hosted", List.of(component));

    assertThat(doc.get("components").get(0).get("purl").asText()).isEqualTo("pkg:generic/archive.tar.gz");
    assertThat(doc.get("components").get(0).has("version")).isFalse();
  }

  @Test
  void spdxDocumentContainsPackagesWithPurlExternalRefs() {
    SbomGenerator.ComponentData component = new SbomGenerator.ComponentData(
        "npm", null, "demo-npm", "1.0.2", "MIT", Map.of("sha256", "ff00"), List.of());

    JsonNode doc = underTest.generate(SbomFormat.SPDX, "npm-hosted", List.of(component));

    assertThat(doc.get("spdxVersion").asText()).isEqualTo("SPDX-2.3");
    assertThat(doc.get("dataLicense").asText()).isEqualTo("CC0-1.0");
    assertThat(doc.get("SPDXID").asText()).isEqualTo("SPDXRef-DOCUMENT");
    assertThat(doc.get("name").asText()).isEqualTo("nexus-npm-hosted");
    assertThat(doc.get("documentNamespace").asText()).startsWith("https://nexus.sonatype.org/spdxdocs/nexus-npm-hosted-");
    assertThat(doc.get("creationInfo").get("creators").get(0).asText())
        .isEqualTo("Tool: " + SbomGenerator.TOOL_NAME + "-" + SbomGenerator.TOOL_VERSION);

    JsonNode pkg = doc.get("packages").get(0);
    assertThat(pkg.get("name").asText()).isEqualTo("demo-npm");
    assertThat(pkg.get("versionInfo").asText()).isEqualTo("1.0.2");
    assertThat(pkg.get("SPDXID").asText()).startsWith("SPDXRef-Package-");
    assertThat(pkg.get("downloadLocation").asText()).isEqualTo("NOASSERTION");
    assertThat(pkg.get("filesAnalyzed").asBoolean()).isFalse();
    assertThat(pkg.get("licenseDeclared").asText()).isEqualTo("MIT");

    JsonNode externalRef = pkg.get("externalRefs").get(0);
    assertThat(externalRef.get("referenceCategory").asText()).isEqualTo("PACKAGE-MANAGER");
    assertThat(externalRef.get("referenceType").asText()).isEqualTo("purl");
    assertThat(externalRef.get("referenceLocator").asText()).isEqualTo("pkg:npm/demo-npm@1.0.2");

    JsonNode checksum = pkg.get("checksums").get(0);
    assertThat(checksum.get("algorithm").asText()).isEqualTo("SHA256");
    assertThat(checksum.get("checksumValue").asText()).isEqualTo("ff00");

    JsonNode relationship = doc.get("relationships").get(0);
    assertThat(relationship.get("spdxElementId").asText()).isEqualTo("SPDXRef-DOCUMENT");
    assertThat(relationship.get("relationshipType").asText()).isEqualTo("DESCRIBES");
    assertThat(relationship.get("relatedSpdxElement").asText()).isEqualTo(pkg.get("SPDXID").asText());
  }

  @Test
  void unknownFormatIsRejected() {
    assertThatThrownBy(() -> SbomFormat.from("pdf")).isInstanceOf(IllegalArgumentException.class);
    assertThat(SbomFormat.from(null)).isEqualTo(SbomFormat.CYCLONEDX);
    assertThat(SbomFormat.from(" SPDX ")).isEqualTo(SbomFormat.SPDX);
  }

  @Test
  void percentEncodeFollowsRfc3986UnreservedSet() {
    assertThat(SbomGenerator.percentEncode("is-number")).isEqualTo("is-number");
    assertThat(SbomGenerator.percentEncode("1.0.0-beta+build")).isEqualTo("1.0.0-beta%2Bbuild");
    assertThat(SbomGenerator.percentEncode("org/example")).isEqualTo("org%2Fexample");
  }

  private static JsonNode findDependency(final JsonNode dependencies, final String ref) {
    for (JsonNode dependency : dependencies) {
      if (ref.equals(dependency.get("ref").asText())) {
        return dependency;
      }
    }
    throw new AssertionError("No dependency with ref " + ref);
  }
}
