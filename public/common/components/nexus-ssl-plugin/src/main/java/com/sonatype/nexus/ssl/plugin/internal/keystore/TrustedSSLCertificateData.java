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
package com.sonatype.nexus.ssl.plugin.internal.keystore;

/**
 * {@link TrustedSSLCertificate} data.
 *
 */
public class TrustedSSLCertificateData
    implements TrustedSSLCertificate
{
  private String alias;

  private String pem;

  public TrustedSSLCertificateData() {
  }

  public TrustedSSLCertificateData(final String alias, final String pem) {
    this.alias = alias;
    this.pem = pem;
  }

  @Override
  public String getAlias() {
    return alias;
  }

  public void setAlias(final String alias) {
    this.alias = alias;
  }

  @Override
  public String getPem() {
    return pem;
  }

  public void setPem(final String pem) {
    this.pem = pem;
  }
}
