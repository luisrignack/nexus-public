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
package org.sonatype.nexus.backup;

import jakarta.inject.Singleton;

import org.sonatype.nexus.common.upgrade.AvailabilityVersion;
import org.sonatype.nexus.formfields.CheckboxFormField;
import org.sonatype.nexus.formfields.FormField;
import org.sonatype.nexus.formfields.StringTextFormField;
import org.sonatype.nexus.scheduling.TaskDescriptorSupport;

import org.springframework.stereotype.Component;

/**
 * Descriptor for {@link RepositoryBackupTask}.
 */
@AvailabilityVersion(from = "1.0")
@Component
@Singleton
public class RepositoryBackupTaskDescriptor
    extends TaskDescriptorSupport
{
  public static final String TYPE_ID = "nexus.backup.task";

  public static final String LOCATION = "location";

  public static final String INCLUDE_BLOBS = "includeBlobs";

  public RepositoryBackupTaskDescriptor() {
    super(
        TYPE_ID,
        RepositoryBackupTask.class,
        "Admin - Backup datastore and blob stores",
        VISIBLE,
        EXPOSED,
        new StringTextFormField(
            LOCATION,
            "Location",
            "Directory (relative to the Nexus work directory) where the backup is written",
            FormField.MANDATORY).withInitialValue("backups"),
        new CheckboxFormField(
            INCLUDE_BLOBS,
            "Include blob stores",
            "Copy every file-based blob store with SHA-256 verification (external blob stores are skipped)",
            FormField.OPTIONAL).withInitialValue(true));
  }

  @Override
  public boolean allowConcurrentRun() {
    return false;
  }
}
