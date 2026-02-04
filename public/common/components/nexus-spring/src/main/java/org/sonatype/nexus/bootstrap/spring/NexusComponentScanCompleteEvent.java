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
package org.sonatype.nexus.bootstrap.spring;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * This event is fired after Nexus is done performing its component scan. The flow is as follows Spring does its
 * component scan, using the typical spring @CompnentScan annoation(s) or via packages set in the @SpringBootApplication
 * annotations
 */
public class NexusComponentScanCompleteEvent
    extends ApplicationEvent
{
  public NexusComponentScanCompleteEvent(final ConfigurableApplicationContext configurableApplicationContext) {
    super(configurableApplicationContext);
  }
}
