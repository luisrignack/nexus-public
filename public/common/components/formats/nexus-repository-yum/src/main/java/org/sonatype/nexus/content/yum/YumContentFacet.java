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
package org.sonatype.nexus.content.yum;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.sonatype.nexus.common.hash.HashAlgorithm;
import org.sonatype.nexus.repository.Facet;
import org.sonatype.nexus.repository.content.facet.ContentFacet;
import org.sonatype.nexus.repository.content.fluent.FluentAsset;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.Payload;

import com.google.common.collect.ImmutableList;

import static org.sonatype.nexus.common.hash.HashAlgorithm.SHA256;

/**
 * Persistent content for the Yum format.
 */
@Facet.Exposed
public interface YumContentFacet
    extends ContentFacet
{
  Iterable<HashAlgorithm> HASHING = ImmutableList.of(SHA256);

  Optional<Content> get(String path) throws IOException;

  /**
   * Stores a generic asset (used by the proxy and for generated metadata).
   */
  Content put(String path, Payload content) throws IOException;

  /**
   * Stores an RPM and records its RPM coordinates on the component.
   */
  Content putRpm(String path, Payload content) throws IOException;

  /**
   * Stores a generated metadata file under the repository.
   */
  Content putGenerated(String path, byte[] content, String contentType) throws IOException;

  /**
   * Stores a generated metadata file under the repository with additional asset attributes.
   */
  Content putGenerated(
      String path,
      byte[] content,
      String contentType,
      Map<String, Object> attributes) throws IOException;

  boolean delete(String path) throws IOException;

  Optional<FluentAsset> find(String path);

  /**
   * All RPM assets in the repository.
   */
  Stream<FluentAsset> rpmAssets();
}
