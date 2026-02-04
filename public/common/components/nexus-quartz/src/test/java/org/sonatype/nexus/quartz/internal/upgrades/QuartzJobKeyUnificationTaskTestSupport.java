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
package org.sonatype.nexus.quartz.internal.upgrades;

import java.util.UUID;

import org.sonatype.nexus.quartz.SleeperTask;
import org.sonatype.nexus.quartz.SleeperTaskDescriptor;
import org.sonatype.nexus.quartz.TaskSchedulerTestSupport;
import org.sonatype.nexus.quartz.internal.task.QuartzTaskInfo;
import org.sonatype.nexus.scheduling.TaskConfiguration;
import org.sonatype.nexus.scheduling.TaskInfo;

import org.mockito.MockedStatic;
import org.quartz.JobKey;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

/**
 * Base test support class for {@link QuartzJobKeyUnificationTask} tests.
 * Provides shared helper methods for creating legacy tasks and running migrations.
 */
public abstract class QuartzJobKeyUnificationTaskTestSupport
    extends TaskSchedulerTestSupport
{
  protected static final long WAIT_TIMEOUT = 20000L; // 20 seconds

  /**
   * Creates a "legacy" task with mismatched job_name and task_configuration_id by temporarily mocking JobKey.jobKey()
   * to return a different UUID.
   */
  protected QuartzTaskInfo createLegacyTask(String taskName) {
    try (MockedStatic<JobKey> mockedJobKey = mockStatic(JobKey.class, CALLS_REAL_METHODS)) {
      mockedJobKey.when(() -> JobKey.jobKey(anyString(), anyString()))
          .thenReturn(new JobKey(UUID.randomUUID().toString(), "nexus"));

      return createNamedTask(taskName);
    }
  }

  /**
   * Creates a named SleeperTask with manual schedule (stays in WAITING state).
   */
  protected QuartzTaskInfo createNamedTask(String taskName) {
    TaskConfiguration config = taskScheduler.createTaskConfigurationInstance(SleeperTaskDescriptor.TYPE_ID);
    config.setName(taskName);
    config.setString(SleeperTask.RESULT_KEY, RESULT);
    return (QuartzTaskInfo) taskScheduler.scheduleTask(config, taskScheduler.getScheduleFactory().manual());
  }

  /**
   * Runs the QuartzJobKeyUnificationTask and returns its TaskInfo.
   */
  protected TaskInfo runMigrationTask() {
    TaskConfiguration config =
        taskScheduler.createTaskConfigurationInstance(QuartzJobKeyUnificationTaskDescriptor.TYPE_ID);
    config.setName("Job Key Migration Test");
    return taskScheduler.scheduleTask(config, taskScheduler.getScheduleFactory().now());
  }

  /**
   * Finds a task by its name.
   */
  protected QuartzTaskInfo findTaskByName(String name) {
    return taskScheduler.listsTasks()
        .stream()
        .filter(t -> name.equals(t.getName()))
        .findFirst()
        .map(QuartzTaskInfo.class::cast)
        .orElseThrow(() -> new AssertionError("Task not found: " + name));
  }

  /**
   * Asserts that a task is unified (job_name == config_id).
   */
  protected void assertTaskUnified(QuartzTaskInfo task) {
    assertThat(task.getName() + " should be unified",
        task.getJobKey().getName(), is(task.getConfiguration().getId()));
  }

  /**
   * Asserts that a task is NOT unified (legacy - job_name != config_id).
   */
  protected void assertTaskLegacy(QuartzTaskInfo task) {
    assertThat(task.getName() + " should be legacy (not unified)",
        task.getJobKey().getName(), is(not(task.getConfiguration().getId())));
  }
}
