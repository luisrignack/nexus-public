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
package org.sonatype.nexus.content.pub;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.sonatype.nexus.common.hash.HashAlgorithm;
import org.sonatype.nexus.repository.Facet;
import org.sonatype.nexus.repository.content.facet.ContentFacet;
import org.sonatype.nexus.repository.pub.PubPackageMetadata;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.Payload;

import com.google.common.collect.ImmutableList;

import static org.sonatype.nexus.common.hash.HashAlgorithm.MD5;
import static org.sonatype.nexus.common.hash.HashAlgorithm.SHA1;
import static org.sonatype.nexus.common.hash.HashAlgorithm.SHA256;
import static org.sonatype.nexus.common.hash.HashAlgorithm.SHA512;

/**
 * Persistent content of a Pub repository: package archives plus the attributes needed to rebuild
 * the {@code /api/packages/<name>} document without opening the archives again.
 */
@Facet.Exposed
public interface PubContentFacet
    extends ContentFacet
{
  Iterable<HashAlgorithm> HASHING = ImmutableList.of(MD5, SHA1, SHA256, SHA512);

  String PACKAGE_NAME_ATTRIBUTE = "pub.packageName";

  String PACKAGE_VERSION_ATTRIBUTE = "pub.packageVersion";

  String PUBSPEC_ATTRIBUTE = "pub.pubspec";

  String SHA256_ATTRIBUTE = "pub.sha256";

  Optional<Content> get(String path) throws IOException;

  /**
   * Stores a generated document (for example a cached proxy response) without a component.
   */
  Content put(String path, Payload content) throws IOException;

  /**
   * Parses, validates and stores a published package archive.
   *
   * @throws IllegalArgumentException when the archive has no valid pubspec.yaml
   */
  Content putPackage(byte[] archive) throws IOException;

  boolean delete(String path) throws IOException;

  /**
   * All versions stored for a package, sorted by path.
   */
  List<PubPackageMetadata> packages(String name);
}
