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
package org.sonatype.nexus.content.alpine;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.sonatype.nexus.common.hash.HashAlgorithm;
import org.sonatype.nexus.repository.Facet;
import org.sonatype.nexus.repository.content.facet.ContentFacet;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.Payload;

import com.google.common.collect.ImmutableList;

import static org.sonatype.nexus.common.hash.HashAlgorithm.MD5;
import static org.sonatype.nexus.common.hash.HashAlgorithm.SHA1;
import static org.sonatype.nexus.common.hash.HashAlgorithm.SHA256;
import static org.sonatype.nexus.common.hash.HashAlgorithm.SHA512;

/**
 * Persistent content of an Alpine repository: package files plus the generated
 * {@code APKINDEX.tar.gz} documents.
 */
@Facet.Exposed
public interface AlpineContentFacet
    extends ContentFacet
{
  Iterable<HashAlgorithm> HASHING = ImmutableList.of(MD5, SHA1, SHA256, SHA512);

  String INDEX_ENTRY_ATTRIBUTE = "alpine.indexEntry";

  String PACKAGE_NAME_ATTRIBUTE = "alpine.packageName";

  String PACKAGE_VERSION_ATTRIBUTE = "alpine.packageVersion";

  String PACKAGE_ARCH_ATTRIBUTE = "alpine.packageArch";

  Optional<Content> get(String path) throws IOException;

  /**
   * Stores a generated document (for example a cached proxy response) without a component.
   */
  Content put(String path, Payload content) throws IOException;

  /**
   * Parses an uploaded {@code .apk}, stores it and remembers the {@code APKINDEX} stanza derived
   * from its {@code .PKGINFO} so the index can be rebuilt without opening the archive again.
   *
   * @throws IllegalArgumentException when the payload is not an APK v2 package
   */
  Content putPackage(String path, Payload content) throws IOException;

  boolean delete(String path) throws IOException;

  /**
   * Stanzas of the packages stored directly under {@code directory}, ready to be packed into an
   * {@code APKINDEX.tar.gz}. The result is sorted for deterministic output.
   */
  List<String> indexEntries(String directory);
}
