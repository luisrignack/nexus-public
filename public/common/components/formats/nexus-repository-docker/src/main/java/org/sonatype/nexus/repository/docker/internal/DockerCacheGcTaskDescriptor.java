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
package org.sonatype.nexus.repository.docker.internal;

import jakarta.inject.Singleton;

import org.sonatype.nexus.common.upgrade.AvailabilityVersion;
import org.sonatype.nexus.formfields.CheckboxFormField;
import org.sonatype.nexus.formfields.FormField;
import org.sonatype.nexus.formfields.NumberTextFormField;
import org.sonatype.nexus.formfields.StringTextFormField;
import org.sonatype.nexus.scheduling.TaskDescriptorSupport;

import org.springframework.stereotype.Component;

/**
 * Descriptor for {@link DockerCacheGcTask}.
 */
@AvailabilityVersion(from = "1.0")
@Component
@Singleton
public class DockerCacheGcTaskDescriptor
    extends TaskDescriptorSupport
{
  public static final String TYPE_ID = "repository.docker-gc";

  public static final String MAX_SIZE_MB_FIELD_ID = "maxSizeMb";

  public static final String MAX_AGE_HOURS_FIELD_ID = "maxAgeHours";

  public static final String UPSTREAM_FIELD_ID = "upstream";

  public static final String DRY_RUN_FIELD_ID = "dryRun";

  public DockerCacheGcTaskDescriptor() {
    super(
        TYPE_ID,
        DockerCacheGcTask.class,
        "Docker - Garbage collect pull-through cache",
        VISIBLE,
        EXPOSED,
        new NumberTextFormField(
            MAX_SIZE_MB_FIELD_ID,
            "Max size per upstream (MB)",
            "Maximum disk space in MB per upstream before LRU eviction (0 = unlimited)",
            FormField.OPTIONAL).withInitialValue(0).withMinimumValue(0),
        new NumberTextFormField(
            MAX_AGE_HOURS_FIELD_ID,
            "Max entry age (hours)",
            "Maximum age in hours before entries are evicted unless referenced by an active manifest (0 = unlimited)",
            FormField.OPTIONAL).withInitialValue(0).withMinimumValue(0),
        new StringTextFormField(
            UPSTREAM_FIELD_ID,
            "Upstream filter",
            "Optional upstream name (e.g. ghcr, quay, gcr, or repository name); leave blank for all upstreams",
            FormField.OPTIONAL),
        new CheckboxFormField(
            DRY_RUN_FIELD_ID,
            "Dry run",
            "Calculate and log candidates without deleting files from disk",
            FormField.OPTIONAL).withInitialValue(false));
  }

  @Override
  public boolean allowConcurrentRun() {
    return false;
  }
}
