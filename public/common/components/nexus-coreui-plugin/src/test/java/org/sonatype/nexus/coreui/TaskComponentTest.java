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
package org.sonatype.nexus.coreui;

import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.validation.Validator;

import org.sonatype.goodies.testsupport.Test5Support;
import org.sonatype.nexus.scheduling.CurrentState;
import org.sonatype.nexus.scheduling.ExternalTaskState;
import org.sonatype.nexus.scheduling.TaskConfiguration;
import org.sonatype.nexus.scheduling.TaskInfo;
import org.sonatype.nexus.scheduling.TaskScheduler;
import org.sonatype.nexus.scheduling.TaskState;
import org.sonatype.nexus.scheduling.TaskUtils;
import org.sonatype.nexus.scheduling.schedule.Manual;
import org.sonatype.nexus.scheduling.schedule.Schedule;
import org.sonatype.nexus.scheduling.schedule.Weekly;
import org.sonatype.nexus.scheduling.spi.TaskResultStateStore;
import org.sonatype.nexus.testcommon.extensions.AuthenticationExtension;
import org.sonatype.nexus.testcommon.extensions.AuthenticationExtension.WithUser;
import org.sonatype.nexus.testcommon.validation.ValidationExtension;

import jakarta.inject.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests {@link TaskComponent}.
 */
@ExtendWith(ValidationExtension.class)
@ExtendWith(AuthenticationExtension.class)
@WithUser
class TaskComponentTest
    extends Test5Support
{
  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private TaskScheduler scheduler;

  @Mock
  private Validator validator;

  @Mock
  private TaskInfo taskInfo;

  private final Provider<Validator> validatorProvider = () -> validator;

  private TaskComponent component;

  @Mock
  private TaskScheduler taskScheduler;

  private TaskUtils taskUtils;

  @BeforeEach
  void setup() {
    Provider<TaskScheduler> mockSchedulerProvider = () -> taskScheduler;
    TaskResultStateStore mockResultStore = mock(TaskResultStateStore.class);
    lenient().when(mockResultStore.isSupported()).thenReturn(false);
    taskUtils = new TaskUtils(mockSchedulerProvider, mockResultStore);
    component = new TaskComponent(scheduler, validatorProvider, false, taskUtils);
  }

  @Test
  void testValidateState_running() {
    ExternalTaskState extState = mock(ExternalTaskState.class);
    when(extState.getState()).thenReturn(TaskState.RUNNING);
    when(scheduler.toExternalTaskState(taskInfo)).thenReturn(extState);

    assertThrows(IllegalStateException.class, () -> component.validateState("taskId", taskInfo),
        "Task can not be edited while it is being executed or it is in line to be executed");
  }

  @Test
  void testValidateState_notRunning() {
    ExternalTaskState extState = mock(ExternalTaskState.class);
    when(extState.getState()).thenReturn(TaskState.WAITING);
    when(scheduler.toExternalTaskState(taskInfo)).thenReturn(extState);

    component.validateState("taskId", taskInfo);
  }

  @Test
  void testValidateScriptUpdate_noSourceChange() {
    TaskConfiguration taskConfiguration = new TaskConfiguration();
    taskConfiguration.setString("source", "println 'hello'");

    when(taskInfo.getConfiguration()).thenReturn(taskConfiguration);

    TaskXO taskXO = new TaskXO();
    taskXO.setProperties(Map.of("source", "println 'hello'"));

    component.validateScriptUpdate(taskInfo, taskXO);
  }

  @Test
  void testValidateScriptUpdate_sourceChange_allowCreation() {
    TaskConfiguration taskConfiguration = new TaskConfiguration();
    taskConfiguration.setString("source", "println 'hello'");

    when(taskInfo.getConfiguration()).thenReturn(taskConfiguration);

    TaskXO taskXO = new TaskXO();
    taskXO.setProperties(Map.of("source", "println 'hello world'"));

    component = new TaskComponent(scheduler, validatorProvider, true, taskUtils);
    component.validateScriptUpdate(taskInfo, taskXO);
  }

  @Test
  void testValidateScriptUpdate_sourceChange_doNotAllowCreation() {
    TaskConfiguration taskConfiguration = new TaskConfiguration();
    taskConfiguration.setString("source", "println 'hello'");

    TaskInfo taskInfo = mock(TaskInfo.class);
    when(taskInfo.getConfiguration()).thenReturn(taskConfiguration);

    TaskXO taskXO = new TaskXO();
    taskXO.setProperties(Map.of("source", "println 'hello world'"));

    assertThrows(IllegalStateException.class, () -> component.validateScriptUpdate(taskInfo, taskXO),
        "Script source updates are not allowed");
  }

  @Test
  void testNotExposedTaskCannotBeCreated() throws Exception {
    TaskConfiguration taskConfiguration = new TaskConfiguration();
    taskConfiguration.setString("source", "println 'hello'");
    taskConfiguration.setExposed(false);

    when(scheduler.getScheduleFactory().manual()).thenReturn(new Manual());

    TaskXO taskXO = new TaskXO();
    taskXO.setName("my-task");
    taskXO.setId("my-id");
    taskXO.setEnabled(true);
    taskXO.setTypeId("task-type-id");
    taskXO.setProperties(Map.of("source", "println 'hello world'"));
    taskXO.setSchedule("manual");

    assertThrows(IllegalStateException.class, () -> component.create(taskXO), "This task is not allowed to be created");
  }

  @Test
  void testAppendPlanReconciliationText_NoDryRun() {
    TaskConfiguration taskConfiguration = mock(TaskConfiguration.class);
    setupTask(taskConfiguration);

    List<TaskXO> tasks = component.read();
    assertEquals(1, tasks.size());
    assertEquals(TaskComponent.PLAN_RECONCILIATION_TASK_ID, tasks.get(0).getTypeId());

    assertEquals("Ok [0s]", tasks.get(0).getLastRunResult());
  }

  @Test
  void testAppendPlanReconciliationText_DryRun() {
    TaskConfiguration taskConfiguration = mock(TaskConfiguration.class);
    setupTask(taskConfiguration);
    when(taskConfiguration.getBoolean(anyString(), anyBoolean())).thenReturn(true);

    List<TaskXO> tasks = component.read();
    assertEquals(1, tasks.size());
    assertEquals(TaskComponent.PLAN_RECONCILIATION_TASK_ID, tasks.get(0).getTypeId());

    tasks = component.read();
    assertEquals("Ok [0s]" + TaskComponent.PLAN_RECONCILIATION_TASK_OK_TEXT, tasks.get(0).getLastRunResult());
  }

  private void setupTask(final TaskConfiguration taskConfiguration) {
    when(taskConfiguration.isVisible()).thenReturn(true);
    when(taskConfiguration.getTypeId()).thenReturn(TaskComponent.PLAN_RECONCILIATION_TASK_ID);

    CurrentState localState = mock(CurrentState.class);
    Schedule schedule = mock(Weekly.class);
    when(localState.getState()).thenReturn(TaskState.WAITING);
    when(taskInfo.getId()).thenReturn("taskId");
    when(taskInfo.getTypeId()).thenReturn(TaskComponent.PLAN_RECONCILIATION_TASK_ID);
    when(taskInfo.getCurrentState()).thenReturn(localState);
    when(taskInfo.getConfiguration()).thenReturn(taskConfiguration);
    when(taskInfo.getSchedule()).thenReturn(schedule);
    when(scheduler.listsTasks()).thenReturn(List.of(taskInfo));

    ExternalTaskState extState = mock(ExternalTaskState.class);
    when(scheduler.toExternalTaskState(taskInfo)).thenReturn(extState);
    when(extState.getState()).thenReturn(TaskState.WAITING);
    when(extState.getLastEndState()).thenReturn(TaskState.OK);
    when(extState.getLastRunStarted()).thenReturn(new Date());
    when(extState.getLastRunDuration()).thenReturn(100L);
  }
}
