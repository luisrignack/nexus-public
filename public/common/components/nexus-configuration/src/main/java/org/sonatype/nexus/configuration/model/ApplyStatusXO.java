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
package org.sonatype.nexus.configuration.model;

import org.sonatype.nexus.configuration.ApplyStatus;

public class ApplyStatusXO
{
  private ApplyStatus status;

  private String message;

  private String entity;

  public ApplyStatus getStatus() {
    return status;
  }

  public void setStatus(final ApplyStatus status) {
    this.status = status;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(final String message) {
    this.message = message;
  }

  public String getEntity() {
    return entity;
  }

  public void setEntity(final String entity) {
    this.entity = entity;
  }

  public static ApplyStatusXO from(final String entity, final String message, final ApplyStatus status) {
    ApplyStatusXO xo = new ApplyStatusXO();
    xo.setEntity(entity);
    xo.setMessage(message);
    xo.setStatus(status);
    return xo;
  }
}
