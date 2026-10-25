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
package org.sonatype.nexus.content.cargo.internal.recipe;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.annotation.Nullable;

import org.sonatype.nexus.repository.view.Payload;

/**
 * A payload that reports a fixed content type while delegating everything else to the wrapped payload.
 *
 * <p>Used to force {@code Content-Type: application/gzip} on {@code .crate} downloads whatever the upstream or the
 * blob store recorded.</p>
 */
class CargoPayload
    implements Payload
{
  private final Payload delegate;

  private final String contentType;

  CargoPayload(final Payload delegate, final String contentType) {
    this.delegate = delegate;
    this.contentType = contentType;
  }

  @Nullable
  @Override
  public String getContentType() {
    return contentType;
  }

  @Override
  public long getSize() {
    return delegate.getSize();
  }

  @Override
  public InputStream openInputStream() throws IOException {
    return delegate.openInputStream();
  }

  @Override
  public void copy(final InputStream input, final OutputStream output) throws IOException {
    delegate.copy(input, output);
  }

  @Override
  public void close() throws IOException {
    delegate.close();
  }
}
