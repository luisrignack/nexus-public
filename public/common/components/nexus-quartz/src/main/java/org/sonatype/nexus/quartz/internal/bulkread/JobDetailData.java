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
package org.sonatype.nexus.quartz.internal.bulkread;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.quartz.JobKey;

import static org.quartz.JobKey.jobKey;

/**
 * Holds data from QRTZ_JOB_DETAILS table.
 * Note: IS_NONCONCURRENT and IS_UPDATE_DATA are not included as they are derived
 * from Job class annotations ({@link org.quartz.DisallowConcurrentExecution @DisallowConcurrentExecution} and
 * {@link org.quartz.PersistJobDataAfterExecution @PersistJobDataAfterExecution}).
 */
record JobDetailData(
    String schedName,
    String jobName,
    String jobGroup,
    String description,
    String jobClassName,
    boolean isDurable,
    boolean requestsRecovery,
    byte[] jobData)
{
  /**
   * Creates a JobDetailData from a ResultSet row.
   */
  static JobDetailData fromResultSet(final ResultSet rs) throws SQLException {
    return new JobDetailData(
        rs.getString("SCHED_NAME"),
        rs.getString("JOB_NAME"),
        rs.getString("JOB_GROUP"),
        rs.getString("DESCRIPTION"),
        rs.getString("JOB_CLASS_NAME"),
        rs.getBoolean("IS_DURABLE"),
        rs.getBoolean("REQUESTS_RECOVERY"),
        rs.getBytes("JOB_DATA"));
  }

  /**
   * Returns the JobKey for this job detail.
   */
  JobKey getKey() {
    return jobKey(jobName, jobGroup);
  }
}
