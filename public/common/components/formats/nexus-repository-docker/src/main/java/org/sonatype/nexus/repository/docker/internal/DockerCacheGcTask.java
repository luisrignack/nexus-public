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

import jakarta.inject.Inject;

import org.sonatype.nexus.repository.docker.internal.DockerCache.GcResult;
import org.sonatype.nexus.scheduling.Cancelable;
import org.sonatype.nexus.scheduling.TaskSupport;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Scheduled task that runs garbage collection on the Docker pull-through disk cache.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DockerCacheGcTask
    extends TaskSupport
    implements Cancelable
{
  private static final long BYTES_PER_MB = 1024L * 1024L;

  private static final long SECONDS_PER_HOUR = 3600L;

  private final DockerCache cache;

  @Inject
  public DockerCacheGcTask(final DockerCache cache) {
    this.cache = checkNotNull(cache);
  }

  @Override
  public String getMessage() {
    return "Garbage collect Docker pull-through cache";
  }

  @Override
  protected GcResult execute() throws Exception {
    if (isCanceled()) {
      return null;
    }

    long maxSizeMb = getConfiguration().getLong(DockerCacheGcTaskDescriptor.MAX_SIZE_MB_FIELD_ID, 0L);
    long maxAgeHours = getConfiguration().getLong(DockerCacheGcTaskDescriptor.MAX_AGE_HOURS_FIELD_ID, 0L);
    boolean dryRun = getConfiguration().getBoolean(DockerCacheGcTaskDescriptor.DRY_RUN_FIELD_ID, false);
    String upstream = getConfiguration().getString(DockerCacheGcTaskDescriptor.UPSTREAM_FIELD_ID);

    long maxSizeBytes = maxSizeMb > 0 ? maxSizeMb * BYTES_PER_MB : 0L;
    long maxAgeSeconds = maxAgeHours > 0 ? maxAgeHours * SECONDS_PER_HOUR : 0L;

    return cache.gc(maxSizeBytes, maxAgeSeconds, dryRun, upstream);
  }
}
