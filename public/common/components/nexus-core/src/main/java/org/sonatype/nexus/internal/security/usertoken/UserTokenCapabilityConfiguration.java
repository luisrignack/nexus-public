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
package org.sonatype.nexus.internal.security.usertoken;

import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Configuration of the {@code usertoken} capability.
 *
 * @since 3.89
 */
public class UserTokenCapabilityConfiguration
{
  static final String PROTECT_CONTENT = "protectContent";

  static final String EXPIRATION_ENABLED = "expirationEnabled";

  static final String EXPIRATION_DAYS = "expirationDays";

  private final boolean protectContent;

  private final boolean expirationEnabled;

  private final int expirationDays;

  public UserTokenCapabilityConfiguration(final Map<String, String> properties) {
    checkNotNull(properties);
    this.protectContent = Boolean.parseBoolean(properties.get(PROTECT_CONTENT));
    this.expirationEnabled = Boolean.parseBoolean(properties.get(EXPIRATION_ENABLED));
    this.expirationDays = parse(properties.get(EXPIRATION_DAYS));
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

  private static int parse(final String value) {
    if (value == null || value.isBlank()) {
      return UserTokenConfiguration.DEFAULT_EXPIRATION_DAYS;
    }
    try {
      int days = Integer.parseInt(value);
      return days >= 1 && days <= 999 ? days : UserTokenConfiguration.DEFAULT_EXPIRATION_DAYS;
    }
    catch (NumberFormatException e) {
      return UserTokenConfiguration.DEFAULT_EXPIRATION_DAYS;
    }
  }
}
