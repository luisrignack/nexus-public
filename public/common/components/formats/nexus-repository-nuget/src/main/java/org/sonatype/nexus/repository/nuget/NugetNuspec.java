/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype Nexus" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.repository.nuget;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.annotation.Nullable;
import javax.xml.parsers.DocumentBuilderFactory;

import org.sonatype.nexus.repository.InvalidContentException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;

/**
 * Parsed NuGet package manifest ({@code .nuspec}).
 *
 * <p>The manifest is extracted from a {@code .nupkg} archive using only {@link java.util.zip}, no extra
 * dependency is required.</p>
 */
public final class NugetNuspec
{
  private final String id;

  private final String version;

  private final String title;

  private final String description;

  private final String authors;

  private final String owners;

  private final String projectUrl;

  private final String licenseUrl;

  private final String tags;

  private final List<String> dependencies;

  private final boolean requiresLicenseAcceptance;

  private NugetNuspec(
      final String id,
      final String version,
      @Nullable final String title,
      @Nullable final String description,
      @Nullable final String authors,
      @Nullable final String owners,
      @Nullable final String projectUrl,
      @Nullable final String licenseUrl,
      @Nullable final String tags,
      final List<String> dependencies,
      final boolean requiresLicenseAcceptance)
  {
    this.id = id;
    this.version = version;
    this.title = title;
    this.description = description;
    this.authors = authors;
    this.owners = owners;
    this.projectUrl = projectUrl;
    this.licenseUrl = licenseUrl;
    this.tags = tags;
    this.dependencies = dependencies;
    this.requiresLicenseAcceptance = requiresLicenseAcceptance;
  }

  public String getId() {
    return id;
  }

  public String getVersion() {
    return version;
  }

  @Nullable
  public String getTitle() {
    return title;
  }

  @Nullable
  public String getDescription() {
    return description;
  }

  @Nullable
  public String getAuthors() {
    return authors;
  }

  @Nullable
  public String getOwners() {
    return owners;
  }

  @Nullable
  public String getProjectUrl() {
    return projectUrl;
  }

  @Nullable
  public String getLicenseUrl() {
    return licenseUrl;
  }

  @Nullable
  public String getTags() {
    return tags;
  }

  public List<String> getDependencies() {
    return dependencies;
  }

  public boolean isRequiresLicenseAcceptance() {
    return requiresLicenseAcceptance;
  }

  /**
   * The metadata persisted with the component/asset so registration and search do not need to read blobs.
   */
  public Map<String, Object> toAttributeMap() {
    Map<String, Object> attributes = new LinkedHashMap<>();
    attributes.put("id", id);
    attributes.put("version", version);
    putIfPresent(attributes, "title", title);
    putIfPresent(attributes, "description", description);
    putIfPresent(attributes, "authors", authors);
    putIfPresent(attributes, "owners", owners);
    putIfPresent(attributes, "projectUrl", projectUrl);
    putIfPresent(attributes, "licenseUrl", licenseUrl);
    putIfPresent(attributes, "tags", tags);
    if (!dependencies.isEmpty()) {
      attributes.put("dependencies", dependencies);
    }
    return attributes;
  }

  private static void putIfPresent(final Map<String, Object> attributes, final String key, @Nullable final String value) {
    if (value != null && !value.trim().isEmpty()) {
      attributes.put(key, value.trim());
    }
  }

  /**
   * Extracts the {@code <id>.nuspec} manifest from a nupkg (zip) stream.
   *
   * @throws InvalidContentException if the archive has no manifest or it is invalid
   */
  public static NugetNuspec extract(final InputStream nupkg) {
    return parse(extractXml(nupkg));
  }

  /**
   * Returns the raw bytes of the {@code .nuspec} entry of a nupkg (zip) stream.
   *
   * @throws InvalidContentException if the archive has no manifest
   */
  public static byte[] extractXml(final InputStream nupkg) {
    if (nupkg == null) {
      throw new InvalidContentException("Missing .nupkg payload");
    }
    try (ZipInputStream zip = new ZipInputStream(nupkg)) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if (entry.isDirectory()) {
          continue;
        }
        String name = entry.getName() == null ? "" : entry.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(NugetCoordinatesHelper.NUSPEC_EXTENSION)) {
          return readEntry(zip);
        }
      }
    }
    catch (IOException e) {
      throw new InvalidContentException("Unable to read the .nupkg archive: " + e.getMessage(), e);
    }
    throw new InvalidContentException("The .nupkg archive does not contain a .nuspec manifest");
  }

  public static NugetNuspec parse(final byte[] xml) {
    return parse(new String(xml, java.nio.charset.StandardCharsets.UTF_8));
  }

  /**
   * Parses nuspec XML and validates {@code <id>} and {@code <version>}.
   *
   * @throws InvalidContentException when the manifest is missing or invalid
   */
  public static NugetNuspec parse(final String xml) {
    if (xml == null || xml.trim().isEmpty()) {
      throw new InvalidContentException("The .nuspec manifest is empty");
    }

    Document document;
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(false);
      factory.setExpandEntityReferences(false);
      setFeatureQuietly(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
      setFeatureQuietly(factory, "http://xml.org/sax/features/external-general-entities", false);
      setFeatureQuietly(factory, "http://xml.org/sax/features/external-parameter-entities", false);
      javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
      builder.setEntityResolver(EMPTY_ENTITY_RESOLVER);
      document = builder.parse(new ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    catch (Exception e) {
      throw new InvalidContentException("Unable to parse the .nuspec manifest: " + e.getMessage(), e);
    }

    Element metadata = firstElement(document, "metadata");
    if (metadata == null) {
      throw new InvalidContentException("The .nuspec manifest has no <metadata> element");
    }

    String id = text(metadata, "id");
    String version = text(metadata, "version");
    if (id == null || id.trim().isEmpty()) {
      throw new InvalidContentException("The .nuspec manifest is missing the <id> element");
    }
    if (version == null || version.trim().isEmpty()) {
      throw new InvalidContentException("The .nuspec manifest is missing the <version> element");
    }
    if (!NugetCoordinatesHelper.isValidId(id)) {
      throw new InvalidContentException("Invalid package id: '" + id.trim() + "'");
    }
    if (!NugetCoordinatesHelper.isValidVersion(version)) {
      throw new InvalidContentException("Invalid package version: '" + version.trim() + "'");
    }

    List<String> dependencies = new ArrayList<>();
    NodeList dependencyNodes = document.getElementsByTagName("dependency");
    for (int i = 0; i < dependencyNodes.getLength(); i++) {
      Node node = dependencyNodes.item(i);
      if (!(node instanceof Element)) {
        continue;
      }
      Element dependency = (Element) node;
      String dependencyId = dependency.getAttribute("id");
      if (dependencyId == null || dependencyId.trim().isEmpty()) {
        continue;
      }
      String dependencyVersion = dependency.getAttribute("version");
      dependencies.add(dependencyVersion == null || dependencyVersion.trim().isEmpty()
          ? dependencyId.trim()
          : dependencyId.trim() + ":" + dependencyVersion.trim());
    }

    return new NugetNuspec(
        id.trim(),
        version.trim(),
        text(metadata, "title"),
        text(metadata, "description"),
        text(metadata, "authors"),
        text(metadata, "owners"),
        text(metadata, "projectUrl"),
        text(metadata, "licenseUrl"),
        text(metadata, "tags"),
        dependencies,
        Boolean.parseBoolean(text(metadata, "requiresLicenseAcceptance")));
  }

  private static final EntityResolver EMPTY_ENTITY_RESOLVER =
      (publicId, systemId) -> new InputSource(new StringReader(""));

  private static void setFeatureQuietly(
      final DocumentBuilderFactory factory,
      final String feature,
      final boolean value)
  {
    try {
      factory.setFeature(feature, value);
    }
    catch (Exception e) {
      // feature not supported by the parser; keep going with parser defaults
    }
  }

  @Nullable
  private static Element firstElement(final Document document, final String name) {
    NodeList nodes = document.getElementsByTagName(name);
    if (nodes.getLength() == 0) {
      return null;
    }
    Node node = nodes.item(0);
    return node instanceof Element ? (Element) node : null;
  }

  @Nullable
  private static String text(final Element parent, final String name) {
    NodeList nodes = parent.getElementsByTagName(name);
    if (nodes.getLength() == 0) {
      return null;
    }
    String value = nodes.item(0).getTextContent();
    return value == null ? null : value.trim();
  }

  private static byte[] readEntry(final InputStream input) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    byte[] chunk = new byte[8192];
    int read;
    while ((read = input.read(chunk)) != -1) {
      buffer.write(chunk, 0, read);
    }
    return buffer.toByteArray();
  }
}
