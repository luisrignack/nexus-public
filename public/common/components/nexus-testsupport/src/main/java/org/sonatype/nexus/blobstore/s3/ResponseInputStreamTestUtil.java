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

package org.sonatype.nexus.blobstore.s3;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

public class ResponseInputStreamTestUtil
{
  public static ResponseInputStream<GetObjectResponse> getResponseInputStream(String contents) {
    final InputStream inputStream = new ByteArrayInputStream(contents.getBytes());
    final GetObjectResponse response = GetObjectResponse.builder().build();
    return new ResponseInputStream<>(response, inputStream);
  }
}
