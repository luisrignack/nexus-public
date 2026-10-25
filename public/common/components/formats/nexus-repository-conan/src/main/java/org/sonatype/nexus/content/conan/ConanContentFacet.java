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
package org.sonatype.nexus.content.conan;

import java.io.IOException;
import java.util.Optional;

import org.sonatype.nexus.common.hash.HashAlgorithm;
import org.sonatype.nexus.repository.Facet;
import org.sonatype.nexus.repository.content.facet.ContentFacet;
import org.sonatype.nexus.repository.content.fluent.FluentAsset;
import org.sonatype.nexus.repository.conan.ConanCoordinates;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.Payload;

import com.google.common.collect.ImmutableList;

import static org.sonatype.nexus.common.hash.HashAlgorithm.MD5;
import static org.sonatype.nexus.common.hash.HashAlgorithm.SHA1;
import static org.sonatype.nexus.common.hash.HashAlgorithm.SHA256;
import static org.sonatype.nexus.common.hash.HashAlgorithm.SHA512;

/**
 * Provides persistent content for the Conan format.
 *
 * <p>Assets live under the canonical Conan storage layout:
 * {@code conans/{name}/{version}/{user}/{channel}/revisions/{rrev}/exports/...} for recipe files and
 * {@code conans/{name}/{version}/{user}/{channel}/revisions/{rrev}/packages/{pkgid}/revisions/{prev}/...} for
 * package binaries.</p>
 *
 * @since 3.89
 */
@Facet.Exposed
public interface ConanContentFacet
    extends ContentFacet
{
  Iterable<HashAlgorithm> HASHING = ImmutableList.of(MD5, SHA1, SHA256, SHA512);

  /**
   * Reads a stored file.
   */
  Optional<Content> get(String path) throws IOException;

  /**
   * Stores a file at the given storage path, used by the proxy to cache API documents and remote files. The owning
   * component is derived from the path when it follows the canonical Conan layout.
   */
  Content put(String path, Payload content) throws IOException;

  /**
   * Every asset stored under the given prefix (inclusive), used to build revision and file listings.
   */
  Iterable<FluentAsset> assetsWithPrefix(String prefix);

  /**
   * Stores a recipe file (anything the API exposes below {@code .../revisions/{rrev}/files/}).
   */
  FluentAsset putRecipeFile(ConanCoordinates coordinates, String revision, String filename, Payload content)
      throws IOException;

  /**
   * Stores a package binary file (anything the API exposes below
   * {@code .../revisions/{rrev}/packages/{pkgid}/revisions/{prev}/files/}).
   */
  FluentAsset putPackageFile(
      ConanCoordinates coordinates,
      String revision,
      String packageId,
      String packageRevision,
      String filename,
      Payload content) throws IOException;

  /**
   * MD5 of a stored asset, as reported by the Conan {@code files} listing.
   */
  String md5(FluentAsset asset) throws IOException;
}
