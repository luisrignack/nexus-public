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
package org.sonatype.nexus.repository.pypi;

import org.sonatype.goodies.testsupport.TestSupport;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class PyPiCoordinatesHelperTest
    extends TestSupport
{
  private static final String WHEEL =
      "https://files.pythonhosted.org/packages/d9/5a/e7c31adbe875f2abbb91bd84cf2dc52d792b5a01506781dbcf25c91daf11/"
          + "six-1.16.0-py2.py3-none-any.whl";

  @Test
  public void sidecarMetadataIsAppendedToTheUpstreamUrl() {
    assertThat(
        PyPiCoordinatesHelper.withRequestedSidecar(WHEEL, "six-1.16.0-py2.py3-none-any.whl.metadata"),
        is(WHEEL + ".metadata"));
  }

  @Test
  public void sidecarSignatureIsAppendedToTheUpstreamUrl() {
    assertThat(
        PyPiCoordinatesHelper.withRequestedSidecar(WHEEL, "six-1.16.0-py2.py3-none-any.whl.asc"),
        is(WHEEL + ".asc"));
  }

  @Test
  public void plainFileNameIsUnchanged() {
    assertThat(
        PyPiCoordinatesHelper.withRequestedSidecar(WHEEL, "six-1.16.0-py2.py3-none-any.whl"),
        is(WHEEL));
  }

  @Test
  public void unrelatedFileNameIsUnchanged() {
    assertThat(
        PyPiCoordinatesHelper.withRequestedSidecar(WHEEL, "other-1.0.tar.gz"),
        is(WHEEL));
  }
}
