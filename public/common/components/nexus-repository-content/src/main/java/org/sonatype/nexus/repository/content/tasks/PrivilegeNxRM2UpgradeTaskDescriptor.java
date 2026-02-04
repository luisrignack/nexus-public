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
package org.sonatype.nexus.repository.content.tasks;

import org.sonatype.nexus.common.upgrade.AvailabilityVersion;
import org.sonatype.nexus.scheduling.TaskDescriptorSupport;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@AvailabilityVersion(from = "1.0")
@Component
public class PrivilegeNxRM2UpgradeTaskDescriptor
    extends TaskDescriptorSupport
{
  public static final String TYPE_ID = "nxrm2.privilege.upgrade";

  @Autowired
  public PrivilegeNxRM2UpgradeTaskDescriptor() {
    super(TYPE_ID, PrivilegeNxRM2UpgradeTask.class,
        "Upgrade NxRM2 privileges Task",
        TaskDescriptorSupport.NOT_VISIBLE,
        TaskDescriptorSupport.NOT_EXPOSED);
  }
}
