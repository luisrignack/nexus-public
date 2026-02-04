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
package org.sonatype.nexus.common.app;

import org.springframework.core.Ordered;

/**
 * List of the filter priorities. Please field order by priority, 100 spacing used to allow future insertions without
 * modifying all lines.
 */
public final class WebFilterPriority
{
  /**
   * A filter which provides the servlet to the thread
   */
  public static final int SERVLET_FILTER = Ordered.HIGHEST_PRECEDENCE;

  /**
   * Filters providing authentication
   */
  public static final int AUTHENTICATION = 0;

  /**
   * Anti-CSRF protection, must be after {@link AUTHENTICATION} as auth is used as a heuristic.
   */
  public static final int ANTI_CSRF = AUTHENTICATION + 100;

  public static final int WEB = -0x70000000;

  public static final int LICENSING = -0x60000000;

  public static final int LEGACY_HTTP_BRIDGE = -0x50000000;

  public static final int WEB_RESOURCES = Ordered.LOWEST_PRECEDENCE;

  private WebFilterPriority() {
    // prevent creation
  }
}
