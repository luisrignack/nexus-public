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
package org.sonatype.nexus.repository.conan;

import org.sonatype.goodies.testsupport.TestSupport;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/**
 * Tests for {@link ConanApiPath} and {@link ConanCoordinates}: how the Conan v1/v2 request paths map onto
 * coordinates, revisions and storage paths.
 */
public class ConanApiPathTest
    extends TestSupport
{
  @Test
  public void parse_recipeRevisions() {
    ConanApiPath parsed = ConanApiPath.parse("/v2/conans/zlib/1.2.13/_/_/revisions");

    assertThat(parsed.kind(), is(ConanApiPath.Kind.RECIPE_REVISIONS));
    assertThat(parsed.coordinates().reference(), is("zlib/1.2.13@_/_"));
    assertThat(parsed.contentPrefix(), is("conans/zlib/1.2.13/_/_/revisions/"));
  }

  @Test
  public void parse_missingUserAndChannelDefaultToUnderscore() {
    ConanApiPath parsed = ConanApiPath.parse("/v2/conans/zlib/1.2.13/revisions");

    assertThat(parsed.kind(), is(ConanApiPath.Kind.RECIPE_REVISIONS));
    assertThat(parsed.coordinates().name(), is("zlib"));
    assertThat(parsed.coordinates().version(), is("1.2.13"));
    assertThat(parsed.coordinates().user(), is("_"));
    assertThat(parsed.coordinates().channel(), is("_"));
    assertThat(parsed.coordinates().namespace(), is("_/_"));
    assertThat(parsed.coordinates().reference(), is("zlib/1.2.13@_/_"));
  }

  @Test
  public void parse_realUserAndChannelArePreserved() {
    ConanApiPath parsed = ConanApiPath.parse("/v1/conans/zlib/1.2.13/conan/stable/revisions");

    assertThat(parsed.kind(), is(ConanApiPath.Kind.RECIPE_REVISIONS));
    assertThat(parsed.coordinates().user(), is("conan"));
    assertThat(parsed.coordinates().channel(), is("stable"));
    assertThat(parsed.coordinates().reference(), is("zlib/1.2.13@conan/stable"));
  }

  @Test
  public void parse_recipeFileExposesTheExportsStoragePath() {
    ConanApiPath parsed = ConanApiPath.parse("/v2/conans/zlib/1.2.13/_/_/revisions/r1/files/conanfile.py");

    assertThat(parsed.kind(), is(ConanApiPath.Kind.RECIPE_FILE));
    assertThat(parsed.revision(), is("r1"));
    assertThat(parsed.filename(), is("conanfile.py"));
    assertThat(parsed.filePath(), is("conans/zlib/1.2.13/_/_/revisions/r1/exports/conanfile.py"));
    assertThat(parsed.isUploadable(), is(true));
  }

  @Test
  public void parse_recipeFileListKeepsNestedFileNames() {
    ConanApiPath parsed = ConanApiPath.parse("/v2/conans/zlib/1.2.13/_/_/revisions/r1/files/sub/dir/file.txt");

    assertThat(parsed.kind(), is(ConanApiPath.Kind.RECIPE_FILE));
    assertThat(parsed.filename(), is("sub/dir/file.txt"));
    assertThat(parsed.filePath(), is("conans/zlib/1.2.13/_/_/revisions/r1/exports/sub/dir/file.txt"));
  }

  @Test
  public void parse_recipeFilesListing() {
    ConanApiPath parsed = ConanApiPath.parse("/v2/conans/zlib/1.2.13/_/_/revisions/r1/files");

    assertThat(parsed.kind(), is(ConanApiPath.Kind.RECIPE_FILES));
    assertThat(parsed.revision(), is("r1"));
    assertThat(parsed.filename(), is(nullValue()));
    assertThat(parsed.contentPrefix(), is("conans/zlib/1.2.13/_/_/revisions/r1/exports/"));
    assertThat(parsed.isUploadable(), is(false));
  }

  @Test
  public void parse_packageRevisions() {
    ConanApiPath parsed = ConanApiPath.parse("/v2/conans/zlib/1.2.13/_/_/revisions/r1/packages/p1/revisions");

    assertThat(parsed.kind(), is(ConanApiPath.Kind.PACKAGE_REVISIONS));
    assertThat(parsed.revision(), is("r1"));
    assertThat(parsed.packageId(), is("p1"));
    assertThat(parsed.contentPrefix(), is("conans/zlib/1.2.13/_/_/revisions/r1/packages/p1/revisions/"));
  }

  @Test
  public void parse_packageFile() {
    ConanApiPath parsed = ConanApiPath.parse(
        "/v2/conans/zlib/1.2.13/_/_/revisions/r1/packages/p1/revisions/r2/files/libz.so");

    assertThat(parsed.kind(), is(ConanApiPath.Kind.PACKAGE_FILE));
    assertThat(parsed.revision(), is("r1"));
    assertThat(parsed.packageId(), is("p1"));
    assertThat(parsed.packageRevision(), is("r2"));
    assertThat(parsed.filename(), is("libz.so"));
    assertThat(parsed.filePath(), is("conans/zlib/1.2.13/_/_/revisions/r1/packages/p1/revisions/r2/libz.so"));
    assertThat(parsed.isUploadable(), is(true));
  }

  @Test
  public void parse_packageFilesListing() {
    ConanApiPath parsed = ConanApiPath.parse(
        "/v2/conans/zlib/1.2.13/_/_/revisions/r1/packages/p1/revisions/r2/files");

    assertThat(parsed.kind(), is(ConanApiPath.Kind.PACKAGE_FILES));
    assertThat(parsed.contentPrefix(), is("conans/zlib/1.2.13/_/_/revisions/r1/packages/p1/revisions/r2/"));
  }

  @Test
  public void parse_searchAndPing() {
    assertThat(ConanApiPath.parse("/v1/conans/search").kind(), is(ConanApiPath.Kind.SEARCH));
    assertThat(ConanApiPath.parse("/v2/conans/search").kind(), is(ConanApiPath.Kind.SEARCH));
    assertThat(ConanApiPath.parse("/v1/ping").kind(), is(ConanApiPath.Kind.PING));
    assertThat(ConanApiPath.parse("/v2/ping").kind(), is(ConanApiPath.Kind.PING));
    assertThat(ConanApiPath.parse("/ping").kind(), is(ConanApiPath.Kind.PING));
    assertThat(ConanApiPath.parse("/v2/ping/extra").kind(), is(ConanApiPath.Kind.UNKNOWN));
  }

  @Test
  public void parse_v1DownloadUrls() {
    ConanApiPath parsed = ConanApiPath.parse("/v1/conans/zlib/1.2.13/_/_/download_urls");

    assertThat(parsed.kind(), is(ConanApiPath.Kind.V1_DOWNLOAD_URLS));
    assertThat(parsed.coordinates().reference(), is("zlib/1.2.13@_/_"));
  }

  @Test
  public void parse_v1PackageDownloadUrls() {
    ConanApiPath parsed = ConanApiPath.parse("/v1/conans/zlib/1.2.13/_/_/packages/p1/download_urls");

    assertThat(parsed.kind(), is(ConanApiPath.Kind.V1_PACKAGE_DOWNLOAD_URLS));
    assertThat(parsed.packageId(), is("p1"));
  }

  @Test
  public void parse_unimplementedEndpointsAreUnknown() {
    assertThat(ConanApiPath.parse("/v1/conans/zlib/1.2.13/_/_/digest").kind(), is(ConanApiPath.Kind.UNKNOWN));
    assertThat(ConanApiPath.parse("/v1/conans/zlib/1.2.13/_/_/packages").kind(), is(ConanApiPath.Kind.UNKNOWN));
    assertThat(ConanApiPath.parse("/v2/conans/zlib").kind(), is(ConanApiPath.Kind.UNKNOWN));
    assertThat(ConanApiPath.parse("/v2/whatever").kind(), is(ConanApiPath.Kind.UNKNOWN));
    assertThat(ConanApiPath.parse("").kind(), is(ConanApiPath.Kind.UNKNOWN));
    assertThat(ConanApiPath.parse(null).kind(), is(ConanApiPath.Kind.UNKNOWN));
  }

  @Test
  public void parse_unknownPathsCarryNoCoordinates() {
    ConanApiPath parsed = ConanApiPath.parse("/v2/whatever");

    assertThat(parsed.kind(), is(ConanApiPath.Kind.UNKNOWN));
    assertThat(parsed.coordinates(), is(nullValue()));
    assertThat(parsed.filePath(), is(nullValue()));
    assertThat(parsed.contentPrefix(), is(nullValue()));
    assertThat(parsed.isUploadable(), is(false));
  }

  @Test
  public void coordinates_storagePathsAndApiPaths() {
    ConanCoordinates coordinates = ConanCoordinates.of("zlib", "1.2.13", null, null);

    assertThat(coordinates.storagePath(), is("conans/zlib/1.2.13/_/_"));
    assertThat(coordinates.revisionsPrefix(), is("conans/zlib/1.2.13/_/_/revisions/"));
    assertThat(coordinates.recipeRevisionPrefix("r1"), is("conans/zlib/1.2.13/_/_/revisions/r1/exports/"));
    assertThat(coordinates.packageRevisionPrefix("r1", "p1"),
        is("conans/zlib/1.2.13/_/_/revisions/r1/packages/p1/revisions/"));
    assertThat(coordinates.filesApiPath("r1"), is("conans/zlib/1.2.13/_/_/revisions/r1/files"));
    assertThat(coordinates.packageFilesApiPath("r1", "p1", "r2"),
        is("conans/zlib/1.2.13/_/_/revisions/r1/packages/p1/revisions/r2/files"));
  }

  @Test
  public void coordinates_areReadBackFromStoragePaths() {
    ConanCoordinates coordinates = ConanCoordinates.fromStoragePath(
        "conans/zlib/1.2.13/_/_/revisions/r1/packages/p1/revisions/r2/libz.so");

    assertThat(coordinates.reference(), is("zlib/1.2.13@_/_"));
    assertThat(ConanCoordinates.revisionFromPath("conans/zlib/1.2.13/_/_/revisions/r1/exports/conanfile.py"), is("r1"));
    assertThat(ConanCoordinates.packageIdFromPath(
        "conans/zlib/1.2.13/_/_/revisions/r1/packages/p1/revisions/r2/libz.so"), is("p1"));
    assertThat(ConanCoordinates.packageRevisionFromPath(
        "conans/zlib/1.2.13/_/_/revisions/r1/packages/p1/revisions/r2/libz.so"), is("r2"));
    assertThat(ConanCoordinates.fromStoragePath("conans/zlib"), is((ConanCoordinates) null));
  }

  @Test
  public void coordinates_decodeKeepsPlusInVersions() {
    assertThat(ConanCoordinates.decode("1.0+build2"), is("1.0+build2"));
    assertThat(ConanCoordinates.normalize("/v2/conans/a/1.0+build/_/_/revisions"),
        is("v2/conans/a/1.0+build/_/_/revisions"));
    assertThat(ConanCoordinates.normalize("//leading//slashes/"), is("leading//slashes/"));
  }
}
