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
package org.sonatype.nexus.repository.ansible;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

/**
 * Metadata of an Ansible collection, read from the {@code MANIFEST.json} that every collection
 * tarball carries at {@code files/ansible_collections/{namespace}/{name}/MANIFEST.json}.
 */
public final class AnsibleCollectionMetadata
{
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String MANIFEST_SUFFIX = "/MANIFEST.json";

  private final String namespace;

  private final String name;

  private final String version;

  private final String description;

  private final String readme;

  private final String repository;

  private final String documentation;

  private final String homepage;

  private final List<String> authors;

  private final Map<String, String> dependencies;

  private AnsibleCollectionMetadata(
      final String namespace,
      final String name,
      final String version,
      final String description,
      final String readme,
      final String repository,
      final String documentation,
      final String homepage,
      final List<String> authors,
      final Map<String, String> dependencies)
  {
    this.namespace = namespace;
    this.name = name;
    this.version = version;
    this.description = description;
    this.readme = readme;
    this.repository = repository;
    this.documentation = documentation;
    this.homepage = homepage;
    this.authors = authors;
    this.dependencies = dependencies;
  }

  /**
   * Reads the manifest from a gzipped collection tarball.
   *
   * @throws IOException when the payload is not a readable collection archive
   */
  public static AnsibleCollectionMetadata read(final InputStream tarGz) throws IOException {
    try (InputStream gzip = new GZIPInputStream(tarGz);
         TarArchiveInputStream tar = new TarArchiveInputStream(gzip))
    {
      TarArchiveEntry entry;
      while ((entry = tar.getNextTarEntry()) != null) {
        if (entry.isDirectory()) {
          continue;
        }
        String entryName = entry.getName().replace('\\', '/');
        if (!entryName.endsWith(MANIFEST_SUFFIX)) {
          continue;
        }
        return fromJson(toBytes(tar));
      }
    }
    catch (UncheckedIOException e) {
      throw e.getCause();
    }
    throw new IOException("Archive does not contain a MANIFEST.json (not an Ansible collection)");
  }

  /**
   * Parses a {@code MANIFEST.json} document.
   */
  public static AnsibleCollectionMetadata fromJson(final byte[] json) throws IOException {
    JsonNode root;
    try {
      root = MAPPER.readTree(json);
    }
    catch (IOException e) {
      throw new IOException("MANIFEST.json is not valid JSON: " + e.getMessage(), e);
    }
    if (root == null || !root.isObject()) {
      throw new IOException("MANIFEST.json is not a JSON object");
    }

    String namespace = text(root, "namespace");
    String name = text(root, "name");
    String version = text(root, "version");
    if (namespace == null || namespace.isEmpty() || name == null || name.isEmpty()) {
      throw new IOException("MANIFEST.json is missing namespace/name");
    }
    if (version == null || version.isEmpty()) {
      throw new IOException("MANIFEST.json is missing version");
    }

    List<String> authors = new ArrayList<>();
    JsonNode authorsNode = root.get("authors");
    if (authorsNode != null && authorsNode.isArray()) {
      authorsNode.forEach(node -> {
        if (node.isTextual()) {
          authors.add(node.asText());
        }
      });
    }

    Map<String, String> dependencies = new LinkedHashMap<>();
    JsonNode depsNode = root.get("dependencies");
    if (depsNode != null && depsNode.isObject()) {
      depsNode.fields().forEachRemaining(field -> dependencies.put(field.getKey(), field.getValue().asText()));
    }

    return new AnsibleCollectionMetadata(
        namespace,
        name,
        version,
        text(root, "description"),
        text(root, "readme"),
        text(root, "repository"),
        text(root, "documentation"),
        text(root, "homepage"),
        Collections.unmodifiableList(authors),
        Collections.unmodifiableMap(dependencies));
  }

  public String getNamespace() {
    return namespace;
  }

  public String getName() {
    return name;
  }

  public String getVersion() {
    return version;
  }

  public String getDescription() {
    return description;
  }

  public String getReadme() {
    return readme;
  }

  public String getRepository() {
    return repository;
  }

  public String getDocumentation() {
    return documentation;
  }

  public String getHomepage() {
    return homepage;
  }

  public List<String> getAuthors() {
    return authors;
  }

  public Map<String, String> getDependencies() {
    return dependencies;
  }

  /**
   * Stable key identifying the collection: {@code namespace.name}.
   */
  public String getQualifiedName() {
    return namespace + "." + name;
  }

  private static String text(final JsonNode node, final String field) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? null : value.asText();
  }

  private static byte[] toBytes(final InputStream input) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int read;
    while ((read = input.read(buffer)) != -1) {
      output.write(buffer, 0, read);
    }
    return output.toByteArray();
  }

}
