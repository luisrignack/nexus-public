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
package org.sonatype.nexus.repository.pub.internal;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PubCoordinatesHelperTest
{
  @Test
  void extractsPackageNamesFromMetadataPaths() {
    assertThat(PubCoordinatesHelper.packageFromMetadata("/api/packages/http"),
        equalTo(Optional.of("http")));
    assertThat(PubCoordinatesHelper.packageFromMetadata("/api/packages/flutter_sdk/advisories"),
        equalTo(Optional.of("flutter_sdk")));
    assertFalse(PubCoordinatesHelper.packageFromMetadata("/api/packages/versions/new").isPresent());
    assertFalse(PubCoordinatesHelper.packageFromMetadata("/packages/http/versions/1.0.0.tar.gz").isPresent());
  }

  @Test
  void parsesArchivePaths() {
    assertTrue(PubCoordinatesHelper.isArchive("/packages/http/versions/1.2.0.tar.gz"));
    Optional<String[]> coordinates =
        PubCoordinatesHelper.fromArchivePath("/packages/http/versions/1.2.0.tar.gz");
    assertTrue(coordinates.isPresent());
    assertThat(coordinates.get()[0], equalTo("http"));
    assertThat(coordinates.get()[1], equalTo("1.2.0"));
    assertFalse(PubCoordinatesHelper.fromArchivePath("/packages/http/1.2.0.tar.gz").isPresent());
  }

  @Test
  void validatesCoordinates() {
    assertThat(PubCoordinatesHelper.validatePackageName("flutter_sdk"), equalTo("flutter_sdk"));
    assertThrows(IllegalArgumentException.class, () -> PubCoordinatesHelper.validatePackageName("../evil"));
    assertThrows(IllegalArgumentException.class, () -> PubCoordinatesHelper.validateVersion("1.0.0/evil"));
  }
}
