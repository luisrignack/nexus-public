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
package org.sonatype.nexus.content.go;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.sonatype.nexus.common.hash.HashAlgorithm;
import org.sonatype.nexus.repository.Facet;
import org.sonatype.nexus.repository.content.facet.ContentFacet;
import org.sonatype.nexus.repository.content.fluent.FluentAsset;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.Payload;

import com.google.common.collect.ImmutableList;

import static org.sonatype.nexus.common.hash.HashAlgorithm.MD5;
import static org.sonatype.nexus.common.hash.HashAlgorithm.SHA1;
import static org.sonatype.nexus.common.hash.HashAlgorithm.SHA256;
import static org.sonatype.nexus.common.hash.HashAlgorithm.SHA512;

/**
 * Provides persistent content for the Go module format.
 */
@Facet.Exposed
public interface GoContentFacet
    extends ContentFacet
{
  Iterable<HashAlgorithm> HASHING = ImmutableList.of(MD5, SHA1, SHA256, SHA512);

  String FILE_NAME_ATTRIBUTE = "go.filename";

  Optional<Content> get(String path) throws IOException;

  FluentAsset getOrCreateAsset(String path, String module, String version);

  /**
   * Stores a generated document, such as a version list, without attaching it to a component.
   */
  Content put(String path, Payload content) throws IOException;

  /**
   * Stores a module asset and attaches it to the component identified by module path and version.
   */
  Content put(String path, String module, String version, Payload content) throws IOException;

  boolean delete(String path) throws IOException;

  /**
   * All versions recorded for a module, in no particular order.
   */
  List<String> versions(String module);

  /**
   * Deletes every asset that belongs to the given module.
   */
  boolean deleteModule(String module) throws IOException;
}
