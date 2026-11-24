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
package org.sonatype.nexus.repository.pub;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

import org.sonatype.nexus.repository.pub.internal.PubCoordinatesHelper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Metadata of a Pub package archive, read from its {@code pubspec.yaml}.
 *
 * <p>Only the pubspec member of the gzip-compressed tar archive is read; the rest of the archive is
 * streamed past. The SHA-256 of the complete archive is recorded because the Pub protocol sends it
 * as {@code archive_sha256} and the client verifies the download against it.</p>
 */
public class PubPackageMetadata
{
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final String name;

  private final String version;

  private final Map<String, Object> pubspec;

  private final String sha256;

  private final byte[] archive;

  public PubPackageMetadata(
      final String name,
      final String version,
      final Map<String, Object> pubspec,
      final String sha256,
      final byte[] archive)
  {
    this.name = name;
    this.version = version;
    this.pubspec = pubspec;
    this.sha256 = sha256;
    this.archive = archive;
  }

  /**
   * Reads and validates a published package archive.
   *
   * @throws IllegalArgumentException if the archive is not a valid Pub package
   */
  public static PubPackageMetadata read(final byte[] archive) throws IOException {
    if (archive == null || archive.length == 0) {
      throw new IllegalArgumentException("Empty Pub package archive");
    }

    String name = null;
    String version = null;
    Map<String, Object> pubspec = null;
    try (InputStream input = new GzipCompressorInputStream(new ByteArrayInputStream(archive));
         TarArchiveInputStream tar = new TarArchiveInputStream(input))
    {
      TarArchiveEntry entry;
      while ((entry = tar.getNextEntry()) != null) {
        if (entry.isDirectory() || !isPubspec(entry.getName())) {
          continue;
        }
        byte[] yaml = readEntry(tar);
        pubspec = parsePubspec(yaml);
        Object parsedName = pubspec.get("name");
        Object parsedVersion = pubspec.get("version");
        name = parsedName == null ? null : String.valueOf(parsedName);
        version = parsedVersion == null ? null : String.valueOf(parsedVersion);
        break;
      }
    }
    catch (IOException e) {
      throw new IllegalArgumentException("Not a valid Pub package archive: " + e.getMessage(), e);
    }

    if (pubspec == null) {
      throw new IllegalArgumentException("Pub package archive has no pubspec.yaml");
    }
    PubCoordinatesHelper.validatePackageName(name);
    PubCoordinatesHelper.validateVersion(version);
    return new PubPackageMetadata(name, version, pubspec, sha256(archive), archive);
  }

  private static boolean isPubspec(final String name) {
    String normalized = name.startsWith("./") ? name.substring(2) : name;
    return "pubspec.yaml".equals(normalized) || normalized.endsWith("/pubspec.yaml");
  }

  private static byte[] readEntry(final TarArchiveInputStream tar) throws IOException {
    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(8192);
    byte[] buffer = new byte[8192];
    int read;
    while ((read = tar.read(buffer)) != -1) {
      out.write(buffer, 0, read);
    }
    return out.toByteArray();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> parsePubspec(final byte[] yaml) {
    try {
      // SafeConstructor prevents YAML tags from instantiating arbitrary Java types.
      Object parsed = new Yaml(new SafeConstructor(new LoaderOptions()))
          .load(new String(yaml, StandardCharsets.UTF_8));
      if (!(parsed instanceof Map)) {
        throw new IllegalArgumentException("pubspec.yaml must contain a mapping");
      }
      return (Map<String, Object>) parsed;
    }
    catch (RuntimeException e) {
      throw new IllegalArgumentException("Invalid pubspec.yaml: " + e.getMessage(), e);
    }
  }

  private static String sha256(final byte[] archive) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(archive));
    }
    catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  public String getPubspecJson() {
    try {
      return MAPPER.writeValueAsString(pubspec);
    }
    catch (JsonProcessingException e) {
      throw new IllegalStateException("Unable to serialize pubspec", e);
    }
  }

  public String getName() {
    return name;
  }

  public String getVersion() {
    return version;
  }

  public Map<String, Object> getPubspec() {
    return pubspec;
  }

  public String getSha256() {
    return sha256;
  }

  public byte[] getArchive() {
    return archive;
  }
}
