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

import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

/**
 * A published NuGet package version with the metadata exposed through the registration and search endpoints.
 */
public class NugetPackage
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

  private final String published;

  private final boolean listed;

  public NugetPackage(
      final String id,
      final String version,
      @Nullable final String title,
      @Nullable final String description,
      @Nullable final String authors,
      @Nullable final String owners,
      @Nullable final String projectUrl,
      @Nullable final String licenseUrl,
      @Nullable final String tags,
      @Nullable final List<String> dependencies,
      @Nullable final String published,
      final boolean listed)
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
    this.dependencies = dependencies == null ? Collections.emptyList() : dependencies;
    this.published = published;
    this.listed = listed;
  }

  public static NugetPackage of(final NugetNuspec nuspec, @Nullable final String published) {
    return new NugetPackage(
        nuspec.getId(),
        nuspec.getVersion(),
        nuspec.getTitle(),
        nuspec.getDescription(),
        nuspec.getAuthors(),
        nuspec.getOwners(),
        nuspec.getProjectUrl(),
        nuspec.getLicenseUrl(),
        nuspec.getTags(),
        nuspec.getDependencies(),
        published,
        true);
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

  @Nullable
  public String getPublished() {
    return published;
  }

  public boolean isListed() {
    return listed;
  }
}
