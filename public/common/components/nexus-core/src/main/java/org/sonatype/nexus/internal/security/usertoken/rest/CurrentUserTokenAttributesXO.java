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
package org.sonatype.nexus.internal.security.usertoken.rest;

import org.sonatype.nexus.internal.security.usertoken.UserTokenConfiguration;

/**
 * State of the current user's token, consumed by the profile page.
 *
 * @since 3.89
 */
public class CurrentUserTokenAttributesXO
{
  private boolean enabled;

  private boolean protectContent;

  private boolean expirationEnabled;

  private int expirationDays;

  private String expirationTimeTimestamp;

  public CurrentUserTokenAttributesXO(final UserTokenConfiguration configuration, final Long expirationTime) {
    this.enabled = configuration.enabled();
    this.protectContent = configuration.protectContent();
    this.expirationEnabled = configuration.expirationEnabled();
    this.expirationDays = configuration.expirationDays();
    this.expirationTimeTimestamp = expirationTime == null ? null : Long.toString(expirationTime);
  }

  public boolean isEnabled() {
    return enabled;
  }

  public boolean isProtectContent() {
    return protectContent;
  }

  public boolean isExpirationEnabled() {
    return expirationEnabled;
  }

  public int getExpirationDays() {
    return expirationDays;
  }

  public String getExpirationTimeTimestamp() {
    return expirationTimeTimestamp;
  }
}
