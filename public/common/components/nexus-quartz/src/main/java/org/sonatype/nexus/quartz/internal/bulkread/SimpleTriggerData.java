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
 * Immutable data holder for simple trigger data from QRTZ_SIMPLE_TRIGGERS table.
 */
record SimpleTriggerData(
    String triggerName,
    String triggerGroup,
    int repeatCount,
    long repeatInterval,
    int timesTriggered)
{
  static SimpleTriggerData fromResultSet(final ResultSet rs) throws SQLException {
    return new SimpleTriggerData(
        rs.getString("TRIGGER_NAME"),
        rs.getString("TRIGGER_GROUP"),
        rs.getInt("REPEAT_COUNT"),
        rs.getLong("REPEAT_INTERVAL"),
        rs.getInt("TIMES_TRIGGERED"));
  }

  /**
   * Returns the TriggerKey for this simple trigger.
   */
  TriggerKey getKey() {
    return triggerKey(triggerName, triggerGroup);
  }
}
