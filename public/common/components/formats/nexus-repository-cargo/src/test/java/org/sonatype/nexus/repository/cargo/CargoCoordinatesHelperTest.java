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
package org.sonatype.nexus.repository.cargo;

import java.util.Optional;

import org.sonatype.goodies.testsupport.TestSupport;
import org.sonatype.nexus.repository.cargo.CargoCoordinatesHelper.CrateCoordinates;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link CargoCoordinatesHelper} tests: sparse index prefixes, storage paths and api path parsing.
 */
public class CargoCoordinatesHelperTest
    extends TestSupport
{
  @Test
  public void prefixForOneCharacterName() {
    assertEquals("1/a", CargoCoordinatesHelper.prefix("a"));
    assertEquals("index/1/a", CargoCoordinatesHelper.indexPath("a"));
    assertEquals("index/1/z", CargoCoordinatesHelper.indexPath("Z"));
  }

  @Test
  public void prefixForTwoCharacterName() {
    assertEquals("2/ab", CargoCoordinatesHelper.prefix("ab"));
    assertEquals("index/2/ab", CargoCoordinatesHelper.indexPath("ab"));
  }

  @Test
  public void prefixForThreeCharacterName() {
    assertEquals("3/a", CargoCoordinatesHelper.prefix("abc"));
    assertEquals("index/3/a/abc", CargoCoordinatesHelper.indexPath("abc"));
  }

  @Test
  public void prefixForNamesOfFourOrMoreCharacters() {
    assertEquals("ab/cd", CargoCoordinatesHelper.prefix("abcd"));
    assertEquals("se/rd", CargoCoordinatesHelper.prefix("serde"));
    assertEquals("12/34", CargoCoordinatesHelper.prefix("123456"));
    assertEquals("index/ab/cd/abcd", CargoCoordinatesHelper.indexPath("abcd"));
    assertEquals("index/se/rd/serde", CargoCoordinatesHelper.indexPath("serde"));
    // crate names are case insensitive, storage always lower cases them
    assertEquals("index/se/rd/serde", CargoCoordinatesHelper.indexPath("Serde"));
    assertEquals("index/to/ki/tokio", CargoCoordinatesHelper.indexPath("TOKIO"));
  }

  @Test
  public void crateStoragePath() {
    assertEquals("crates/serde/serde-1.0.0.crate", CargoCoordinatesHelper.cratePath("serde", "1.0.0"));
    assertEquals("crates/serde/serde-1.0.0-rc.1.crate", CargoCoordinatesHelper.cratePath("Serde", "1.0.0-rc.1"));
  }

  @Test
  public void normalizeDecodesAndStripsLeadingSlash() {
    assertEquals("index/se/rd/serde", CargoCoordinatesHelper.normalize("/index/se/rd/serde"));
    assertEquals("index/se/rd/serde", CargoCoordinatesHelper.normalize("index/se/rd/serde"));
    assertEquals("api/v1/crates/my crate", CargoCoordinatesHelper.normalize("/api/v1/crates/my%20crate"));
    assertEquals("/", CargoCoordinatesHelper.normalize("/"));
    assertEquals("/", CargoCoordinatesHelper.normalize(""));
    assertEquals("/", CargoCoordinatesHelper.normalize(null));
  }

  @Test
  public void parseDownloadPath() {
    Optional<CrateCoordinates> download =
        CargoCoordinatesHelper.parseDownload("api/v1/crates/serde/1.0.0/download");
    assertTrue(download.isPresent());
    assertEquals("serde", download.get().getCrate());
    assertEquals("1.0.0", download.get().getVersion());

    assertFalse(CargoCoordinatesHelper.parseDownload("api/v1/crates/serde/1.0.0").isPresent());
    assertFalse(CargoCoordinatesHelper.parseDownload("api/v1/crates/serde").isPresent());
    assertFalse(CargoCoordinatesHelper.parseDownload("api/v1/crates").isPresent());
  }

  @Test
  public void parsePublishPathsWithAndWithoutUploadSuffix() {
    Optional<CrateCoordinates> plain = CargoCoordinatesHelper.parsePublish("api/v1/crates/serde/1.0.0");
    assertTrue(plain.isPresent());
    assertEquals("serde", plain.get().getCrate());
    assertEquals("1.0.0", plain.get().getVersion());

    Optional<CrateCoordinates> upload = CargoCoordinatesHelper.parsePublish("api/v1/crates/serde/1.0.0/upload");
    assertTrue(upload.isPresent());
    assertEquals("serde", upload.get().getCrate());
    assertEquals("1.0.0", upload.get().getVersion());

    assertFalse(CargoCoordinatesHelper.parsePublish("api/v1/crates/serde").isPresent());
    assertFalse(CargoCoordinatesHelper.parsePublish("index/se/rd/serde").isPresent());
  }

  @Test
  public void parseSearchAndDetailPaths() {
    assertTrue(CargoCoordinatesHelper.isSearchPath("api/v1/crates"));
    assertTrue(CargoCoordinatesHelper.isSearchPath("api/v1/crates/"));
    assertFalse(CargoCoordinatesHelper.isSearchPath("api/v1/crates/serde"));

    assertEquals(Optional.of("serde"), CargoCoordinatesHelper.crateFromDetailPath("api/v1/crates/serde"));
    assertFalse(CargoCoordinatesHelper.crateFromDetailPath("api/v1/crates/serde/1.0.0").isPresent());

    assertTrue(CargoCoordinatesHelper.isConfigPath("index/config.json"));
    assertFalse(CargoCoordinatesHelper.isConfigPath("index/se/rd/serde"));
  }

  @Test
  public void indexPathRecognition() {
    assertTrue(CargoCoordinatesHelper.isIndexPath("index/se/rd/serde"));
    assertTrue(CargoCoordinatesHelper.isIndexPath("index/1/a"));
    assertTrue(CargoCoordinatesHelper.isIndexPath("index/2/ab"));
    assertFalse(CargoCoordinatesHelper.isIndexPath("index/config.json"));
    assertFalse(CargoCoordinatesHelper.isIndexPath("index/serde"));
    assertFalse(CargoCoordinatesHelper.isIndexPath("crates/serde/serde-1.0.0.crate"));

    assertEquals(Optional.of("serde"), CargoCoordinatesHelper.crateFromIndexPath("index/se/rd/serde"));
    assertEquals(Optional.of("a"), CargoCoordinatesHelper.crateFromIndexPath("index/1/a"));
  }

  @Test
  public void queryStringEncodesValues() {
    org.sonatype.nexus.repository.view.Parameters parameters = new org.sonatype.nexus.repository.view.Parameters();
    assertEquals("", CargoCoordinatesHelper.queryString(parameters));
    assertEquals("", CargoCoordinatesHelper.queryString(null));

    parameters.set("q", "serialization format");
    parameters.set("per_page", "20");
    assertEquals("q=serialization+format&per_page=20", CargoCoordinatesHelper.queryString(parameters));
  }
}
