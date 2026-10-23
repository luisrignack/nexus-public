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
package org.sonatype.nexus.repository.yum.internal.rpm;

import java.util.Collections;
import java.util.List;

/**
 * Metadata extracted from an RPM package header.
 */
public final class RpmPackage
{
  private final String name;

  private final long epoch;

  private final String version;

  private final String release;

  private final String arch;

  private final String summary;

  private final String description;

  private final String packager;

  private final String url;

  private final String license;

  private final String vendor;

  private final String group;

  private final String buildHost;

  private final String sourceRpm;

  private final long buildTime;

  private final long installedSize;

  private final long archiveSize;

  private final long headerStart;

  private final long headerEnd;

  private final String location;

  private final String sha256;

  private final long packageSize;

  private final List<String> files;

  private final List<RpmDependency> provides;

  private final List<RpmDependency> requires;

  private final List<RpmDependency> conflicts;

  private final List<RpmDependency> obsoletes;

  private final List<RpmChangelog> changelogs;

  private long fileTime;

  public RpmPackage(
      final String name,
      final long epoch,
      final String version,
      final String release,
      final String arch,
      final String summary,
      final String description,
      final String packager,
      final String url,
      final String license,
      final String vendor,
      final String group,
      final String buildHost,
      final String sourceRpm,
      final long buildTime,
      final long installedSize,
      final long archiveSize,
      final long headerStart,
      final long headerEnd,
      final String location,
      final String sha256,
      final long packageSize,
      final List<String> files,
      final List<RpmDependency> provides,
      final List<RpmDependency> requires,
      final List<RpmDependency> conflicts,
      final List<RpmDependency> obsoletes,
      final List<RpmChangelog> changelogs)
  {
    this.name = name;
    this.epoch = epoch;
    this.version = version;
    this.release = release;
    this.arch = arch;
    this.summary = summary;
    this.description = description;
    this.packager = packager;
    this.url = url;
    this.license = license;
    this.vendor = vendor;
    this.group = group;
    this.buildHost = buildHost;
    this.sourceRpm = sourceRpm;
    this.buildTime = buildTime;
    this.installedSize = installedSize;
    this.archiveSize = archiveSize;
    this.headerStart = headerStart;
    this.headerEnd = headerEnd;
    this.location = location;
    this.sha256 = sha256;
    this.packageSize = packageSize;
    this.files = List.copyOf(files);
    this.provides = List.copyOf(provides);
    this.requires = List.copyOf(requires);
    this.conflicts = List.copyOf(conflicts);
    this.obsoletes = List.copyOf(obsoletes);
    this.changelogs = List.copyOf(changelogs);
    this.fileTime = buildTime;
  }

  public String name() {
    return name;
  }

  public long epoch() {
    return epoch;
  }

  public String version() {
    return version;
  }

  public String release() {
    return release;
  }

  public String arch() {
    return arch;
  }

  public String summary() {
    return summary;
  }

  public String description() {
    return description;
  }

  public String packager() {
    return packager;
  }

  public String url() {
    return url;
  }

  public String license() {
    return license;
  }

  public String vendor() {
    return vendor;
  }

  public String group() {
    return group;
  }

  public String buildHost() {
    return buildHost;
  }

  public String sourceRpm() {
    return sourceRpm;
  }

  public long buildTime() {
    return buildTime;
  }

  public long installedSize() {
    return installedSize;
  }

  public long archiveSize() {
    return archiveSize;
  }

  public long headerStart() {
    return headerStart;
  }

  public long headerEnd() {
    return headerEnd;
  }

  public String location() {
    return location;
  }

  public String sha256() {
    return sha256;
  }

  public long packageSize() {
    return packageSize;
  }

  public List<String> files() {
    return Collections.unmodifiableList(files);
  }

  public List<RpmDependency> provides() {
    return Collections.unmodifiableList(provides);
  }

  public List<RpmDependency> requires() {
    return Collections.unmodifiableList(requires);
  }

  public List<RpmDependency> conflicts() {
    return Collections.unmodifiableList(conflicts);
  }

  public List<RpmDependency> obsoletes() {
    return Collections.unmodifiableList(obsoletes);
  }

  public List<RpmChangelog> changelogs() {
    return Collections.unmodifiableList(changelogs);
  }

  public long fileTime() {
    return fileTime;
  }

  public RpmPackage withFileTime(final long fileTime) {
    this.fileTime = fileTime;
    return this;
  }

  /**
   * The package identifier used by Yum: {@code epoch:name-version-release.arch}.
   */
  public String nevra() {
    return (epoch > 0 ? epoch + ":" : "") + name + "-" + version + "-" + release + "." + arch;
  }

  /**
   * The component version stored in Nexus for this package.
   */
  public String componentVersion() {
    return (epoch > 0 ? epoch + ":" : "") + version + "-" + release;
  }
}
