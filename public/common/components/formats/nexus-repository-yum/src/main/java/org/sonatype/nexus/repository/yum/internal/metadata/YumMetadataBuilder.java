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
package org.sonatype.nexus.repository.yum.internal.metadata;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

import org.sonatype.nexus.repository.yum.internal.rpm.RpmChangelog;
import org.sonatype.nexus.repository.yum.internal.rpm.RpmDependency;
import org.sonatype.nexus.repository.yum.internal.rpm.RpmPackage;

import com.google.common.hash.Hashing;

/**
 * Builds the Yum/DNF repository metadata ({@code repomd.xml}, {@code primary.xml.gz},
 * {@code filelists.xml.gz} and {@code other.xml.gz}) from parsed RPM headers.
 */
public final class YumMetadataBuilder
{
  private static final String PRIMARY_PATH = "repodata/primary.xml.gz";

  private static final String FILELISTS_PATH = "repodata/filelists.xml.gz";

  private static final String OTHER_PATH = "repodata/other.xml.gz";

  private static final String XML = "application/xml";

  private static final String GZIP = "application/gzip";

  private YumMetadataBuilder() {
    // static utility
  }

  public static YumMetadata build(final List<RpmPackage> packages) throws IOException {
    List<RpmPackage> sorted = packages.stream()
        .sorted(Comparator
            .comparing(RpmPackage::name)
            .thenComparing(RpmPackage::arch)
            .thenComparingLong(RpmPackage::epoch)
            .thenComparing(RpmPackage::version)
            .thenComparing(RpmPackage::release))
        .collect(Collectors.toList());

    String revision = revision(sorted);
    long timestamp = metadataTimestamp(sorted);

    String primaryXml = primaryXml(sorted);
    String filelistsXml = filelistsXml(sorted);
    String otherXml = otherXml(sorted);

    YumMetadataFile primary = gzip(PRIMARY_PATH, primaryXml, GZIP);
    YumMetadataFile filelists = gzip(FILELISTS_PATH, filelistsXml, GZIP);
    YumMetadataFile other = gzip(OTHER_PATH, otherXml, GZIP);

    String repomdXml = repomdXml(revision, timestamp, primary, filelists, other);
    YumMetadataFile repomd = plain(YumMetadata.REPOMD_PATH, repomdXml, XML);

    Map<String, YumMetadataFile> files = new LinkedHashMap<>();
    files.put(repomd.path(), repomd);
    files.put(primary.path(), primary);
    files.put(filelists.path(), filelists);
    files.put(other.path(), other);
    return new YumMetadata(revision, files);
  }

  private static String primaryXml(final List<RpmPackage> packages) {
    StringBuilder xml = new StringBuilder(4096);
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    xml.append("<metadata xmlns=\"http://linux.duke.edu/metadata/common\" ")
        .append("xmlns:rpm=\"http://linux.duke.edu/metadata/rpm\" packages=\"")
        .append(packages.size()).append("\">\n");

    for (RpmPackage pkg : packages) {
      xml.append("  <package type=\"rpm\">\n");
      element(xml, "name", pkg.name());
      element(xml, "arch", pkg.arch());
      xml.append("    <version epoch=\"").append(pkg.epoch()).append("\" ver=\"")
          .append(escape(pkg.version())).append("\" rel=\"").append(escape(pkg.release())).append("\"/>\n");
      xml.append("    <checksum type=\"sha256\" pkgid=\"YES\">")
          .append(escape(pkg.sha256())).append("</checksum>\n");
      element(xml, "summary", pkg.summary());
      element(xml, "description", pkg.description());
      element(xml, "packager", pkg.packager());
      element(xml, "url", pkg.url());
      xml.append("    <time file=\"").append(pkg.fileTime()).append("\" build=\"")
          .append(pkg.buildTime()).append("\"/>\n");
      xml.append("    <size package=\"").append(pkg.packageSize()).append("\" installed=\"")
          .append(pkg.installedSize()).append("\" archive=\"").append(pkg.archiveSize()).append("\"/>\n");
      xml.append("    <location href=\"").append(escape(encodePath(pkg.location()))).append("\"/>\n");
      xml.append("    <format>\n");
      if (!pkg.license().isEmpty()) {
        element(xml, "rpm:license", pkg.license(), 6);
      }
      if (!pkg.vendor().isEmpty()) {
        element(xml, "rpm:vendor", pkg.vendor(), 6);
      }
      if (!pkg.group().isEmpty()) {
        element(xml, "rpm:group", pkg.group(), 6);
      }
      if (!pkg.buildHost().isEmpty()) {
        element(xml, "rpm:buildhost", pkg.buildHost(), 6);
      }
      if (!pkg.sourceRpm().isEmpty()) {
        element(xml, "rpm:sourcerpm", pkg.sourceRpm(), 6);
      }
      xml.append("      <rpm:header-range start=\"").append(pkg.headerStart())
          .append("\" end=\"").append(pkg.headerEnd()).append("\"/>\n");
      dependencies(xml, "rpm:provides", pkg.provides());
      dependencies(xml, "rpm:requires", pkg.requires());
      dependencies(xml, "rpm:conflicts", pkg.conflicts());
      dependencies(xml, "rpm:obsoletes", pkg.obsoletes());
      xml.append("    </format>\n");
      xml.append("  </package>\n");
    }

    xml.append("</metadata>\n");
    return xml.toString();
  }

  private static void dependencies(
      final StringBuilder xml,
      final String container,
      final List<RpmDependency> dependencies)
  {
    if (dependencies.isEmpty()) {
      return;
    }
    xml.append("      <").append(container).append(">\n");
    for (RpmDependency dependency : dependencies) {
      xml.append("        <rpm:entry name=\"").append(escape(dependency.name())).append("\"");
      String flags = dependency.flagsString();
      if (dependency.hasVersion() && flags != null) {
        xml.append(" flags=\"").append(flags).append("\"")
            .append(" epoch=\"").append(dependency.epoch()).append("\"")
            .append(" ver=\"").append(escape(dependency.version())).append("\"");
        if (!dependency.release().isEmpty()) {
          xml.append(" rel=\"").append(escape(dependency.release())).append("\"");
        }
      }
      xml.append("/>\n");
    }
    xml.append("      </").append(container).append(">\n");
  }

  private static String filelistsXml(final List<RpmPackage> packages) {
    StringBuilder xml = new StringBuilder(4096);
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    xml.append("<filelists xmlns=\"http://linux.duke.edu/metadata/filelists\" packages=\"")
        .append(packages.size()).append("\">\n");
    for (RpmPackage pkg : packages) {
      xml.append("  <package pkgid=\"").append(escape(pkg.sha256())).append("\" name=\"")
          .append(escape(pkg.name())).append("\" arch=\"").append(escape(pkg.arch())).append("\">\n");
      version(xml, pkg);
      for (String file : pkg.files()) {
        element(xml, "file", file, 4);
      }
      xml.append("  </package>\n");
    }
    xml.append("</filelists>\n");
    return xml.toString();
  }

  private static String otherXml(final List<RpmPackage> packages) {
    StringBuilder xml = new StringBuilder(2048);
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    xml.append("<otherdata xmlns=\"http://linux.duke.edu/metadata/other\" packages=\"")
        .append(packages.size()).append("\">\n");
    for (RpmPackage pkg : packages) {
      xml.append("  <package pkgid=\"").append(escape(pkg.sha256())).append("\" name=\"")
          .append(escape(pkg.name())).append("\" arch=\"").append(escape(pkg.arch())).append("\">\n");
      version(xml, pkg);
      for (RpmChangelog changelog : pkg.changelogs()) {
        xml.append("    <changelog author=\"").append(escape(changelog.author()))
            .append("\" date=\"").append(changelog.date()).append("\">")
            .append(escape(changelog.text())).append("</changelog>\n");
      }
      xml.append("  </package>\n");
    }
    xml.append("</otherdata>\n");
    return xml.toString();
  }

  private static void version(final StringBuilder xml, final RpmPackage pkg) {
    xml.append("    <version epoch=\"").append(pkg.epoch()).append("\" ver=\"")
        .append(escape(pkg.version())).append("\" rel=\"").append(escape(pkg.release())).append("\"/>\n");
  }

  private static String repomdXml(
      final String revision,
      final long timestamp,
      final YumMetadataFile primary,
      final YumMetadataFile filelists,
      final YumMetadataFile other)
  {
    StringBuilder xml = new StringBuilder(1024);
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    xml.append("<repomd xmlns=\"http://linux.duke.edu/metadata/repo\">\n");
    xml.append("  <revision>").append(escape(revision)).append("</revision>\n");
    data(xml, "primary", timestamp, primary);
    data(xml, "filelists", timestamp, filelists);
    data(xml, "other", timestamp, other);
    xml.append("</repomd>\n");
    return xml.toString();
  }

  private static void data(
      final StringBuilder xml,
      final String type,
      final long timestamp,
      final YumMetadataFile file)
  {
    xml.append("  <data type=\"").append(type).append("\">\n");
    xml.append("    <checksum type=\"sha256\">").append(escape(file.checksum())).append("</checksum>\n");
    xml.append("    <open-checksum type=\"sha256\">").append(escape(file.openChecksum())).append("</open-checksum>\n");
    xml.append("    <location href=\"").append(escape(file.path())).append("\"/>\n");
    xml.append("    <timestamp>").append(timestamp).append("</timestamp>\n");
    xml.append("    <size>").append(file.size()).append("</size>\n");
    xml.append("    <open-size>").append(file.openSize()).append("</open-size>\n");
    xml.append("  </data>\n");
  }

  private static YumMetadataFile gzip(final String path, final String xml, final String contentType) throws IOException {
    byte[] raw = xml.getBytes(StandardCharsets.UTF_8);
    ByteArrayOutputStream compressed = new ByteArrayOutputStream(Math.max(256, raw.length / 2));
    try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
      gzip.write(raw);
    }
    byte[] content = compressed.toByteArray();
    return new YumMetadataFile(
        path,
        content,
        content.length,
        raw.length,
        sha256(content),
        sha256(raw),
        contentType);
  }

  private static YumMetadataFile plain(final String path, final String xml, final String contentType) {
    byte[] content = xml.getBytes(StandardCharsets.UTF_8);
    return new YumMetadataFile(path, content, content.length, content.length, sha256(content), sha256(content), contentType);
  }

  private static String revision(final List<RpmPackage> packages) {
    String identity = packages.stream()
        .map(pkg -> pkg.sha256() + ":" + pkg.location())
        .sorted()
        .collect(Collectors.joining(","));
    return sha256(identity.getBytes(StandardCharsets.UTF_8));
  }

  private static long metadataTimestamp(final List<RpmPackage> packages) {
    long timestamp = 0;
    for (RpmPackage pkg : packages) {
      timestamp = Math.max(timestamp, Math.max(pkg.fileTime(), pkg.buildTime()));
    }
    return timestamp;
  }

  private static String sha256(final byte[] bytes) {
    return Hashing.sha256().hashBytes(bytes).toString();
  }

  private static String encodePath(final String path) {
    String normalized = path.startsWith("/") ? path.substring(1) : path;
    return java.util.Arrays.stream(normalized.split("/", -1))
        .map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"))
        .collect(Collectors.joining("/"));
  }

  private static void element(final StringBuilder xml, final String name, final String value) {
    element(xml, name, value, 4);
  }

  private static void element(
      final StringBuilder xml,
      final String name,
      final String value,
      final int indent)
  {
    xml.append(" ".repeat(indent)).append('<').append(name).append('>')
        .append(escape(value == null ? "" : value))
        .append("</").append(name).append(">\n");
  }

  private static String escape(final String value) {
    if (value == null) {
      return "";
    }
    StringBuilder escaped = new StringBuilder(value.length() + 16);
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '&' -> escaped.append("&amp;");
        case '<' -> escaped.append("&lt;");
        case '>' -> escaped.append("&gt;");
        case '"' -> escaped.append("&quot;");
        case '\'' -> escaped.append("&apos;");
        default -> escaped.append(c);
      }
    }
    return escaped.toString();
  }
}
