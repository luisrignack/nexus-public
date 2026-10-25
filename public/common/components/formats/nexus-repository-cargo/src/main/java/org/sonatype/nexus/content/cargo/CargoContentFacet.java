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
package org.sonatype.nexus.content.cargo;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.sonatype.nexus.common.hash.HashAlgorithm;
import org.sonatype.nexus.repository.Facet;
import org.sonatype.nexus.repository.cargo.CargoCrateSummary;
import org.sonatype.nexus.repository.content.facet.ContentFacet;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.Payload;

import com.google.common.collect.ImmutableList;

import static org.sonatype.nexus.common.hash.HashAlgorithm.MD5;
import static org.sonatype.nexus.common.hash.HashAlgorithm.SHA1;
import static org.sonatype.nexus.common.hash.HashAlgorithm.SHA256;
import static org.sonatype.nexus.common.hash.HashAlgorithm.SHA512;

/**
 * Provides persistent content for the cargo format.
 *
 * <p>Storage layout:</p>
 * <ul>
 *   <li>{@code crates/{crate}/{crate}-{version}.crate} - the uploaded crate archive</li>
 *   <li>{@code index/{prefix}/{crate}} - sibling record file holding one JSON index record per line (ndjson), it is
 *   both the persisted record and the sparse index document served to cargo</li>
 * </ul>
 *
 * @since 3.89
 */
@Facet.Exposed
public interface CargoContentFacet
    extends ContentFacet
{
  Iterable<HashAlgorithm> HASHING = ImmutableList.of(MD5, SHA1, SHA256, SHA512);

  String FILE_NAME_ATTRIBUTE = "cargo.filename";

  String NAME_ATTRIBUTE = "cargo.name";

  String MAX_VERSION_ATTRIBUTE = "cargo.max-version";

  String DESCRIPTION_ATTRIBUTE = "cargo.description";

  String DOWNLOADS_ATTRIBUTE = "cargo.downloads";

  /**
   * {@code dl} entry of the upstream {@code index/config.json}, remembered so the proxy can route API calls.
   */
  String REMOTE_DL_ATTRIBUTE = "cargo.remote-dl";

  /**
   * {@code api} entry of the upstream {@code index/config.json}, remembered so the proxy can route API calls.
   */
  String REMOTE_API_ATTRIBUTE = "cargo.remote-api";

  Optional<Content> get(String path) throws IOException;

  /**
   * Stores generated or proxied content without attaching it to a component.
   */
  Content put(String path, Payload content) throws IOException;

  /**
   * Same as {@link #put(String, Payload)} but merges the given asset attributes into the asset.
   */
  Content put(String path, Payload content, Map<String, Object> attributes) throws IOException;

  /**
   * Stores a {@code .crate} archive and attaches it to the component identified by crate name and version.
   */
  Content put(String path, String crate, String version, Payload content) throws IOException;

  boolean delete(String path) throws IOException;

  /**
   * Newline delimited index records of a crate, empty when the crate was never published here.
   */
  String readIndex(String crate) throws IOException;

  /**
   * Persists the (already merged and sorted) index records of a crate together with the search metadata of the crate.
   */
  Content writeIndex(String crate, String ndjson, CargoCrateSummary summary) throws IOException;

  /**
   * Search metadata of every crate, read from the index asset attributes without touching the record payloads.
   */
  List<CargoCrateSummary> listCrates() throws IOException;

  /**
   * {@code api} base of the upstream registry remembered when the remote {@code index/config.json} was cached.
   */
  Optional<String> remoteApiBase();

  /**
   * {@code dl} base of the upstream registry remembered when the remote {@code index/config.json} was cached.
   */
  Optional<String> remoteDlBase();

  /**
   * Looks up the search metadata of a single crate.
   */
  default Optional<CargoCrateSummary> crate(final String crateName) throws IOException {
    String needle = crateName.toLowerCase(java.util.Locale.ROOT);
    return listCrates().stream()
        .filter(summary -> summary.getName().equalsIgnoreCase(needle))
        .findFirst();
  }
}
