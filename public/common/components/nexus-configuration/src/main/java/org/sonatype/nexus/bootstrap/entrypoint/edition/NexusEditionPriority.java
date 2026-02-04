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
package org.sonatype.nexus.bootstrap.entrypoint.edition;

/**
 * List of the edition priorities. Using 100 spacing to allow future insertions without modifying all lines.
 */
public final class NexusEditionPriority
{
  // Cloud edition will always take priority and be checked first
  public static final int CLOUD_PRIORITY = Integer.MIN_VALUE;

  public static final int PRO_PRIORITY = CLOUD_PRIORITY + 100;

  public static final int COMMUNITY_PRIORITY = PRO_PRIORITY + 100;

  // Core is the last edition to be checked
  public static final int CORE_PRIORITY = COMMUNITY_PRIORITY + 100;
}
