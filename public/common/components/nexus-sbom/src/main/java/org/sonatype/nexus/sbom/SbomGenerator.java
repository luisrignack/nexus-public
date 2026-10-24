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

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import static java.util.Locale.ROOT;

/**
 * Builds SBOM documents (CycloneDX 1.5 and SPDX 2.3) from the component inventory of a repository.
 *
 * <p>Every component is mapped to a Package URL; hashes come from the checksums of the component
 * assets; licenses and dependency relations are taken from the format attributes when the format
 * declares them (for example {@code npm.license} and npm {@code dependencies}).</p>
 */
public class SbomGenerator
{
  public static final String TOOL_NAME = "nexus-repository-sbom";

  public static final String TOOL_VERSION = "3.89.0";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final Map<String, String> CYCLONEDX_ALGORITHMS = Map.of(
      "md5", "MD5",
      "sha1", "SHA-1",
      "sha256", "SHA-256",
      "sha512", "SHA-512");

  private static final Map<String, String> SPDX_ALGORITHMS = Map.of(
      "md5", "MD5",
      "sha1", "SHA1",
      "sha256", "SHA256",
      "sha512", "SHA512");

  /**
   * Immutable snapshot of a component used as generator input.
   */
  public static final class ComponentData
  {
    public final String format;

    @Nullable
    public final String namespace;

    public final String name;

    @Nullable
    public final String version;

    @Nullable
    public final String license;

    public final Map<String, String> checksums;

    public final List<String> dependencyNames;

    public ComponentData(
        final String format,
        @Nullable final String namespace,
        final String name,
        @Nullable final String version,
        @Nullable final String license,
        final Map<String, String> checksums,
        final List<String> dependencyNames)
    {
      this.format = format;
      this.namespace = namespace;
      this.name = name;
      this.version = version;
      this.license = license;
      this.checksums = checksums;
      this.dependencyNames = dependencyNames;
    }
  }

  /**
   * Generates the SBOM document for the given repository inventory.
   */
  public ObjectNode generate(
      final SbomFormat format,
      final String repositoryName,
      final List<ComponentData> components)
  {
    return switch (format) {
      case CYCLONEDX -> cycloneDx(repositoryName, components);
      case SPDX -> spdx(repositoryName, components);
    };
  }

  private ObjectNode cycloneDx(final String repositoryName, final List<ComponentData> components) {
    ObjectNode root = MAPPER.createObjectNode();
    root.put("bomFormat", "CycloneDX");
    root.put("specVersion", "1.5");
    root.put("serialNumber", "urn:uuid:" + UUID.randomUUID());
    root.put("version", 1);

    ObjectNode metadata = root.putObject("metadata");
    metadata.put("timestamp", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    ObjectNode tool = metadata.putArray("tools").addObject();
    tool.put("vendor", "Sonatype Nexus fork");
    tool.put("name", TOOL_NAME);
    tool.put("version", TOOL_VERSION);
    ObjectNode rootComponent = metadata.putObject("component");
    rootComponent.put("type", "application");
    rootComponent.put("name", "nexus:" + repositoryName);
    rootComponent.put("bom-ref", rootRef(repositoryName));

    ArrayNode componentsNode = root.putArray("components");
    ArrayNode dependenciesNode = root.putArray("dependencies");
    ObjectNode rootDependency = dependenciesNode.addObject();
    rootDependency.put("ref", rootRef(repositoryName));
    ArrayNode rootDependsOn = rootDependency.putArray("dependsOn");

    for (ComponentData data : components) {
      String purl = purl(data);

      ObjectNode component = componentsNode.addObject();
      component.put("type", "library");
      component.put("bom-ref", purl);
      String group = group(data);
      if (group != null) {
        component.put("group", group);
      }
      component.put("name", displayName(data));
      if (data.version != null && !data.version.isEmpty()) {
        component.put("version", data.version);
      }
      component.put("purl", purl);

      if (!data.checksums.isEmpty()) {
        ArrayNode hashes = component.putArray("hashes");
        data.checksums.forEach((algorithm, value) -> {
          String cdxAlgorithm = CYCLONEDX_ALGORITHMS.get(algorithm.toLowerCase(ROOT));
          if (cdxAlgorithm != null) {
            ObjectNode hash = hashes.addObject();
            hash.put("alg", cdxAlgorithm);
            hash.put("content", value);
          }
        });
      }

      if (data.license != null && !data.license.isEmpty()) {
        ObjectNode licenseNode = component.putArray("licenses").addObject().putObject("license");
        if (data.license.matches("[A-Za-z0-9.+-]+")) {
          licenseNode.put("id", data.license);
        }
        else {
          licenseNode.put("name", data.license);
        }
      }

      ArrayNode properties = component.putArray("properties");
      addProperty(properties, "nexus:repository", repositoryName);
      addProperty(properties, "nexus:component:format", data.format);

      rootDependsOn.add(purl);

      ObjectNode dependency = dependenciesNode.addObject();
      dependency.put("ref", purl);
      ArrayNode dependsOn = dependency.putArray("dependsOn");
      for (String dependencyName : data.dependencyNames) {
        String dependencyPurl = internalDependencyPurl(dependencyName, components);
        if (dependencyPurl != null) {
          dependsOn.add(dependencyPurl);
        }
      }
    }

    return root;
  }

  private ObjectNode spdx(final String repositoryName, final List<ComponentData> components) {
    ObjectNode root = MAPPER.createObjectNode();
    root.put("spdxVersion", "SPDX-2.3");
    root.put("dataLicense", "CC0-1.0");
    root.put("SPDXID", "SPDXRef-DOCUMENT");
    root.put("name", "nexus-" + repositoryName);
    root.put("documentNamespace", "https://nexus.sonatype.org/spdxdocs/nexus-"
        + repositoryName.replaceAll("[^A-Za-z0-9.-]", "-") + "-" + UUID.randomUUID());

    ObjectNode creationInfo = root.putObject("creationInfo");
    creationInfo.put("created", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    creationInfo.putArray("creators").add("Tool: " + TOOL_NAME + "-" + TOOL_VERSION);

    ArrayNode packages = root.putArray("packages");
    ArrayNode relationships = root.putArray("relationships");

    int index = 0;
    for (ComponentData data : components) {
      String spdxId = "SPDXRef-Package-" + (++index);

      ObjectNode pkg = packages.addObject();
      pkg.put("name", displayName(data));
      if (data.version != null && !data.version.isEmpty()) {
        pkg.put("versionInfo", data.version);
      }
      pkg.put("SPDXID", spdxId);
      pkg.put("downloadLocation", "NOASSERTION");
      pkg.put("filesAnalyzed", false);
      pkg.put("licenseConcluded", "NOASSERTION");
      pkg.put("licenseDeclared", data.license != null && !data.license.isEmpty() ? data.license : "NOASSERTION");
      pkg.put("copyrightText", "NOASSERTION");

      if (!data.checksums.isEmpty()) {
        ArrayNode checksums = pkg.putArray("checksums");
        data.checksums.forEach((algorithm, value) -> {
          String spdxAlgorithm = SPDX_ALGORITHMS.get(algorithm.toLowerCase(ROOT));
          if (spdxAlgorithm != null) {
            ObjectNode checksum = checksums.addObject();
            checksum.put("algorithm", spdxAlgorithm);
            checksum.put("checksumValue", value);
          }
        });
      }

      ObjectNode externalRef = pkg.putArray("externalRefs").addObject();
      externalRef.put("referenceCategory", "PACKAGE-MANAGER");
      externalRef.put("referenceType", "purl");
      externalRef.put("referenceLocator", purl(data));

      ObjectNode relationship = relationships.addObject();
      relationship.put("spdxElementId", "SPDXRef-DOCUMENT");
      relationship.put("relationshipType", "DESCRIBES");
      relationship.put("relatedSpdxElement", spdxId);
    }

    return root;
  }

  private static void addProperty(final ArrayNode properties, final String name, final String value) {
    ObjectNode property = properties.addObject();
    property.put("name", name);
    property.put("value", value);
  }

  private static String internalDependencyPurl(final String dependencyName, final List<ComponentData> components) {
    return components.stream()
        .filter(candidate -> fullName(candidate).equals(dependencyName))
        .findFirst()
        .map(SbomGenerator::purl)
        .orElse(null);
  }

  private static String rootRef(final String repositoryName) {
    return "nexus-repo:" + repositoryName;
  }

  static String fullName(final ComponentData data) {
    if (data.namespace == null || data.namespace.isEmpty()) {
      return data.name;
    }
    return data.namespace + "/" + data.name;
  }

  static String displayName(final ComponentData data) {
    if (data.namespace == null || data.namespace.isEmpty()) {
      return data.name;
    }
    if (data.namespace.startsWith("@")) {
      return data.namespace + "/" + data.name;
    }
    if ("maven".equals(data.format)) {
      return data.name;
    }
    return data.namespace + "/" + data.name;
  }

  @Nullable
  static String group(final ComponentData data) {
    if (data.namespace == null || data.namespace.isEmpty() || data.namespace.startsWith("@")) {
      return null;
    }
    return data.namespace;
  }

  /**
   * Builds the Package URL for a component following the purl spec for the known formats.
   */
  static String purl(final ComponentData data) {
    String versionPart = data.version == null || data.version.isEmpty() ? "" : "@" + percentEncode(data.version);
    switch (data.format) {
      case "maven":
        return "pkg:maven/" + (data.namespace == null || data.namespace.isEmpty()
            ? "_"
            : percentEncode(data.namespace)) + "/" + percentEncode(data.name) + versionPart;
      case "npm": {
        String pkg = data.namespace != null && !data.namespace.isEmpty()
            ? (data.namespace.startsWith("@")
                ? "%40" + percentEncode(data.namespace.substring(1)) + "/" + percentEncode(data.name)
                : percentEncode(data.namespace) + "/" + percentEncode(data.name))
            : percentEncode(data.name);
        return "pkg:npm/" + pkg + versionPart;
      }
      case "pypi":
        return "pkg:pypi/" + percentEncode(normalizePythonName(data.name)) + versionPart;
      case "nuget":
        return "pkg:nuget/" + percentEncode(data.name) + versionPart;
      case "gem":
      case "rubygems":
        return "pkg:gem/" + percentEncode(data.name) + versionPart;
      case "golang":
        return "pkg:golang/" + (data.namespace == null || data.namespace.isEmpty()
            ? percentEncode(data.name)
            : percentEncode(data.namespace) + "/" + percentEncode(data.name)) + versionPart;
      case "helm":
        return "pkg:helm/" + percentEncode(data.name) + versionPart;
      case "conda":
        return "pkg:conda/" + percentEncode(data.name) + versionPart;
      default:
        return "pkg:generic/" + (data.namespace == null || data.namespace.isEmpty()
            ? percentEncode(data.name)
            : percentEncode(data.namespace) + "/" + percentEncode(data.name)) + versionPart;
    }
  }

  /**
   * Percent-encodes everything outside the RFC 3986 unreserved set, as required by the purl spec.
   */
  static String percentEncode(final String value) {
    StringBuilder result = new StringBuilder(value.length());
    for (byte b : value.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
      char c = (char) (b & 0xFF);
      if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
          || c == '-' || c == '.' || c == '_' || c == '~') {
        result.append(c);
      }
      else {
        result.append('%').append(String.format("%02X", b));
      }
    }
    return result.toString();
  }

  /**
   * Normalizes a Python project name according to PEP 503.
   */
  static String normalizePythonName(final String name) {
    return name.toLowerCase(ROOT).replaceAll("[-_.]+", "-");
  }
}
