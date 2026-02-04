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
package org.sonatype.nexus.repository.apt.internal.upgrade;

import org.sonatype.nexus.common.upgrade.AvailabilityVersion;
import org.sonatype.nexus.scheduling.TaskDescriptorSupport;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@AvailabilityVersion(from = "1.0")
@Component
public class RepairAptMetadataLeadingSlashTaskDescriptor
    extends TaskDescriptorSupport
{
  private static final String TASK_NAME = "Upgrade - Fix apt filenames with leading slash";

  static final String TASK_DESCRIPTION = "Updates assets stored with Filename using a leading slash";

  static final String TYPE_ID = "apt.upgrade.filename.leading.slash";

  @Autowired
  public RepairAptMetadataLeadingSlashTaskDescriptor(
      @Value("${apt.upgrade.filename.leading.slash:false}") final boolean visible)
  {
    super(TYPE_ID, RepairAptMetadataLeadingSlashTask.class, TASK_NAME, visible, visible);
  }
}
