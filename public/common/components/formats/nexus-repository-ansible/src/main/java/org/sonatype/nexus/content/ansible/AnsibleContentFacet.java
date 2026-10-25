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
package org.sonatype.nexus.content.ansible;

import java.io.IOException;
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
 * Provides persistent content for the Ansible format: collection tarballs plus the auxiliary
 * documents generated for the Galaxy API.
 */
@Facet.Exposed
public interface AnsibleContentFacet
    extends ContentFacet
{
  Iterable<HashAlgorithm> HASHING = ImmutableList.of(MD5, SHA1, SHA256, SHA512);

  String FILE_NAME_ATTRIBUTE = "ansible.filename";

  String DESCRIPTION_ATTRIBUTE = "ansible.description";

  String AUTHORS_ATTRIBUTE = "ansible.authors";

  String DEPENDENCIES_ATTRIBUTE = "ansible.dependencies";

  Optional<Content> get(String path) throws IOException;

  /**
   * Stores a generated document (for example a cached API response) without attaching it to a
   * component.
   */
  Content put(String path, Payload content) throws IOException;

  /**
   * Stores a collection tarball and attaches it to the component identified by its coordinates.
   *
   * <p>The {@code MANIFEST.json} inside the archive is authoritative: when it disagrees with the
   * requested coordinates an {@link IllegalArgumentException} is raised.</p>
   *
   * @throws IllegalArgumentException when the archive is not a valid collection for these coordinates
   */
  Content putCollection(String path, String namespace, String name, String version, Payload content) throws IOException;

  boolean delete(String path) throws IOException;

  /**
   * Deletes every version of the given collection.
   */
  boolean deleteCollection(String namespace, String name) throws IOException;
}
