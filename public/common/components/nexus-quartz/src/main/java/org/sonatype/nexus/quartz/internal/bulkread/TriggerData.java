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

import org.quartz.TriggerKey;

import static org.quartz.TriggerKey.triggerKey;

/**
 * Immutable data holder for trigger base data from QRTZ_TRIGGERS table.
 */
record TriggerData(
    String triggerName,
    String triggerGroup,
    String jobName,
    String jobGroup,
    String description,
    long nextFireTime,
    long prevFireTime,
    long startTime,
    long endTime,
    int priority,
    String calendarName,
    int misfireInstr,
    byte[] jobData,
    String triggerType)
{
  static TriggerData fromResultSet(final ResultSet rs) throws SQLException {
    return new TriggerData(
        rs.getString("TRIGGER_NAME"),
        rs.getString("TRIGGER_GROUP"),
        rs.getString("JOB_NAME"),
        rs.getString("JOB_GROUP"),
        rs.getString("DESCRIPTION"),
        rs.getLong("NEXT_FIRE_TIME"),
        rs.getLong("PREV_FIRE_TIME"),
        rs.getLong("START_TIME"),
        rs.getLong("END_TIME"),
        rs.getInt("PRIORITY"),
        rs.getString("CALENDAR_NAME"),
        rs.getInt("MISFIRE_INSTR"),
        rs.getBytes("JOB_DATA"),
        rs.getString("TRIGGER_TYPE"));
  }

  /**
   * Returns the TriggerKey for this trigger.
   */
  TriggerKey getKey() {
    return triggerKey(triggerName, triggerGroup);
  }
}
