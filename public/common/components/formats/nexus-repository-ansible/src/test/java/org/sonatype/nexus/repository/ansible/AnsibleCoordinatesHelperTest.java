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
package org.sonatype.nexus.repository.ansible;

import org.sonatype.goodies.testsupport.Test5Support;
import org.sonatype.nexus.repository.ansible.AnsibleCoordinatesHelper.GalaxyRequest;
import org.sonatype.nexus.repository.ansible.AnsibleCoordinatesHelper.Kind;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class AnsibleCoordinatesHelperTest
    extends Test5Support
{
  @Test
  void rootPathsAreRecognized() {
    assertThat(AnsibleCoordinatesHelper.parse("/").getKind(), is(Kind.ROOT));
    assertThat(AnsibleCoordinatesHelper.parse("/api/v3/").getKind(), is(Kind.ROOT));
    assertThat(AnsibleCoordinatesHelper.parse("api/v3").getKind(), is(Kind.ROOT));
    assertThat(AnsibleCoordinatesHelper.parse("/api/v2").getKind(), is(Kind.ROOT));
    assertThat(AnsibleCoordinatesHelper.parse("/api/").getKind(), is(Kind.API_DISCOVERY));
    assertThat(AnsibleCoordinatesHelper.parse("/api").getKind(), is(Kind.API_DISCOVERY));
  }

  @Test
  void collectionListPathsAreRecognized() {
    GalaxyRequest all = AnsibleCoordinatesHelper.parse(
        "/api/v3/plugin/ansible/content/published/collections/index/");
    assertThat(all.getKind(), is(Kind.INDEX_ALL));

    GalaxyRequest namespace = AnsibleCoordinatesHelper.parse(
        "/api/v3/plugin/ansible/content/published/collections/index/community");
    assertThat(namespace.getKind(), is(Kind.INDEX_NAMESPACE));
    assertThat(namespace.getNamespace(), is("community"));

    GalaxyRequest collection = AnsibleCoordinatesHelper.parse(
        "/api/v3/plugin/ansible/content/published/collections/index/community/general");
    assertThat(collection.getKind(), is(Kind.INDEX_COLLECTION));
    assertThat(collection.getNamespace(), is("community"));
    assertThat(collection.getName(), is("general"));
  }

  @Test
  void versionPathsAreRecognized() {
    GalaxyRequest versions = AnsibleCoordinatesHelper.parse(
        "/api/v3/plugin/ansible/content/published/collections/index/community/general/versions");
    assertThat(versions.getKind(), is(Kind.INDEX_VERSIONS));
    assertThat(versions.getName(), is("general"));

    GalaxyRequest version = AnsibleCoordinatesHelper.parse(
        "/api/v3/plugin/ansible/content/published/collections/index/community/general/versions/1.0.0");
    assertThat(version.getKind(), is(Kind.INDEX_VERSION));
    assertThat(version.getVersion(), is("1.0.0"));
  }

  @Test
  void onlyDocumentsAreRewrittenAsJson() {
    assertThat(AnsibleCoordinatesHelper.parse("/api/v3/").isDocument(), is(true));
    assertThat(AnsibleCoordinatesHelper.parse(
        "/api/v3/plugin/ansible/content/published/collections/index/").isDocument(), is(true));
    assertThat(AnsibleCoordinatesHelper.parse(
        "/api/v3/plugin/ansible/content/published/collections/index/community/general/versions/1.0.0/")
        .isDocument(), is(true));
    assertThat(AnsibleCoordinatesHelper.parse("/api/v2/collections/community/general/").isDocument(), is(true));

    // a tarball must keep its bytes: downloads and stored assets are not documents
    assertThat(AnsibleCoordinatesHelper.parse(
        "/api/v3/collections/community/general/1.0.0/download").isDocument(), is(false));
    assertThat(AnsibleCoordinatesHelper.parse(
        "/collections/community/general/1.0.0/community.general-1.0.0.tar.gz").isDocument(), is(false));
    // public Galaxy serves tarballs from .../collections/artifacts/
    assertThat(AnsibleCoordinatesHelper.parse(
        "/api/v3/plugin/ansible/content/published/collections/artifacts/community-general-13.5.0.tar.gz")
        .isDocument(), is(false));
    // unknown Galaxy paths must not be decoded as text either
    assertThat(AnsibleCoordinatesHelper.parse("/api/v3/nonsense").isDocument(), is(false));
  }

  @Test
  void publicGalaxyArtifactsAreDownloads() {
    GalaxyRequest artifact = AnsibleCoordinatesHelper.parse(
        "/api/v3/plugin/ansible/content/published/collections/artifacts/community-general-13.5.0.tar.gz");
    assertThat(artifact.getKind(), is(Kind.DOWNLOAD));
    assertThat(artifact.isDocument(), is(false));
  }

  @Test
  void galaxyV3AliasesUsedByAnsibleGalaxyAreRecognized() {
    assertThat(AnsibleCoordinatesHelper.parse("/api/v3/collections/").getKind(), is(Kind.INDEX_ALL));

    GalaxyRequest namespace = AnsibleCoordinatesHelper.parse("/api/v3/collections/community/");
    assertThat(namespace.getKind(), is(Kind.INDEX_NAMESPACE));
    assertThat(namespace.getNamespace(), is("community"));

    GalaxyRequest collection = AnsibleCoordinatesHelper.parse("/api/v3/collections/community/general/");
    assertThat(collection.getKind(), is(Kind.INDEX_COLLECTION));
    assertThat(collection.getNamespace(), is("community"));
    assertThat(collection.getName(), is("general"));

    GalaxyRequest versions = AnsibleCoordinatesHelper.parse(
        "/api/v3/collections/community/general/versions/");
    assertThat(versions.getKind(), is(Kind.INDEX_VERSIONS));

    GalaxyRequest version = AnsibleCoordinatesHelper.parse(
        "/api/v3/collections/community/general/versions/1.0.0/");
    assertThat(version.getKind(), is(Kind.INDEX_VERSION));
    assertThat(version.getVersion(), is("1.0.0"));
  }

  @Test
  void downloadPathIsRecognized() {
    GalaxyRequest download = AnsibleCoordinatesHelper.parse(
        "/api/v3/collections/community/general/1.0.0/download/");
    assertThat(download.getKind(), is(Kind.DOWNLOAD));
    assertThat(download.getNamespace(), is("community"));
    assertThat(download.getName(), is("general"));
    assertThat(download.getVersion(), is("1.0.0"));
  }

  @Test
  void galaxyV2PathsMapToTheSameKinds() {
    GalaxyRequest collection = AnsibleCoordinatesHelper.parse("/api/v2/collections/community/general/");
    assertThat(collection.getKind(), is(Kind.INDEX_COLLECTION));
    assertThat(collection.getNamespace(), is("community"));
    assertThat(collection.getName(), is("general"));

    GalaxyRequest version = AnsibleCoordinatesHelper.parse(
        "/api/v2/collections/community/general/versions/2.1.1");
    assertThat(version.getKind(), is(Kind.INDEX_VERSION));
    assertThat(version.getVersion(), is("2.1.1"));
  }

  @Test
  void storedTarballsAreAssets() {
    GalaxyRequest asset = AnsibleCoordinatesHelper.parse(
        "/collections/community/general/1.0.0/community.general-1.0.0.tar.gz");
    assertThat(asset.getKind(), is(Kind.ASSET));
    assertThat(AnsibleCoordinatesHelper.isApiPath(asset.getPath()), is(false));

    String[] coordinates = AnsibleCoordinatesHelper.pathCoordinates(asset.getPath());
    assertThat(coordinates[0], is("community"));
    assertThat(coordinates[1], is("general"));
    assertThat(coordinates[2], is("1.0.0"));
  }

  @Test
  void unknownGalaxyPathsAreUnknown() {
    assertThat(AnsibleCoordinatesHelper.parse("/api/v3/nonsense").getKind(), is(Kind.UNKNOWN));
    assertThat(AnsibleCoordinatesHelper.pathCoordinates("/somewhere/else.tar.gz"), is(nullValue()));
  }

  @Test
  void canonicalPathAndFileName() {
    assertThat(
        AnsibleCoordinatesHelper.canonicalPath("community", "general", "1.0.0"),
        is("/collections/community/general/1.0.0/community.general-1.0.0.tar.gz"));
    assertThat(
        AnsibleCoordinatesHelper.fileName("community", "general", "1.0.0"),
        is("community.general-1.0.0.tar.gz"));
  }

  @Test
  void normalizeHandlesSlashes() {
    assertThat(AnsibleCoordinatesHelper.normalize(null), is("/"));
    assertThat(AnsibleCoordinatesHelper.normalize(""), is("/"));
    assertThat(AnsibleCoordinatesHelper.normalize("/api/v3/"), is("/api/v3"));
    assertThat(AnsibleCoordinatesHelper.normalize("api/v3"), is("/api/v3"));
  }
}
