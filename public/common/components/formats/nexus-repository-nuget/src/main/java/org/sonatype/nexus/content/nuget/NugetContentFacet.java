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
package org.sonatype.nexus.content.nuget;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.sonatype.nexus.common.hash.HashAlgorithm;
import org.sonatype.nexus.repository.Facet;
import org.sonatype.nexus.repository.content.facet.ContentFacet;
import org.sonatype.nexus.repository.nuget.NugetPackage;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.Payload;

import com.google.common.collect.ImmutableList;

import static org.sonatype.nexus.common.hash.HashAlgorithm.MD5;
import static org.sonatype.nexus.common.hash.HashAlgorithm.SHA1;
import static org.sonatype.nexus.common.hash.HashAlgorithm.SHA256;
import static org.sonatype.nexus.common.hash.HashAlgorithm.SHA512;

/**
 * Provides persistent content for the NuGet format.
 */
@Facet.Exposed
public interface NugetContentFacet
    extends ContentFacet
{
  Iterable<HashAlgorithm> HASHING = ImmutableList.of(MD5, SHA1, SHA256, SHA512);

  String FILE_NAME_ATTRIBUTE = "nuget.filename";

  String METADATA_ATTRIBUTE = "nuget";

  /**
   * Reads a stored asset.
   */
  Optional<Content> get(String path) throws IOException;

  /**
   * Stores a generated or proxied document, without attaching it to a component.
   */
  Content put(String path, Payload content) throws IOException;

  /**
   * Publishes a raw {@code .nupkg}: the {@code .nuspec} manifest is extracted from the archive, its id and
   * version are validated and both files are stored under the canonical flat container layout.
   *
   * @throws org.sonatype.nexus.repository.InvalidContentException when the payload is not a usable package
   */
  NugetPublishResult publish(Payload nupkg) throws IOException;

  /**
   * Deletes an asset and, when it belongs to a package, its sibling manifest.
   */
  boolean delete(String path) throws IOException;

  /**
   * All known packages with their nuspec metadata, used to build registration and search documents.
   */
  List<NugetPackage> packages() throws IOException;

  /**
   * Sorted (SemVer) list of known versions of a package id.
   */
  List<String> versions(String id) throws IOException;
}
