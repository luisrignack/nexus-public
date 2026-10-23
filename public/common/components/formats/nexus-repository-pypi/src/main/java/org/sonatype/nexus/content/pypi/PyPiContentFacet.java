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
package org.sonatype.nexus.content.pypi;

import java.io.IOException;
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
 * Provides persistent content for the PyPI format.
 */
@Facet.Exposed
public interface PyPiContentFacet
    extends ContentFacet
{
  Iterable<HashAlgorithm> HASHING = ImmutableList.of(MD5, SHA1, SHA256, SHA512);

  String REQUIRES_PYTHON_ATTRIBUTE = "pypi.requires_python";

  String VERSION_ATTRIBUTE = "pypi.version";

  String FILE_NAME_ATTRIBUTE = "pypi.filename";

  Optional<Content> get(String path) throws IOException;

  FluentAsset getOrCreateAsset(String path, String projectName, String version);

  /**
   * Stores a generated document, such as a proxy index, without attaching it to a component.
   */
  Content put(String path, Payload content) throws IOException;

  Content put(
      String path,
      String projectName,
      String version,
      Payload content,
      String requiresPython) throws IOException;

  boolean delete(String path) throws IOException;

  /**
   * Deletes every asset that belongs to the given project.
   */
  boolean deleteProject(String projectName) throws IOException;
}
