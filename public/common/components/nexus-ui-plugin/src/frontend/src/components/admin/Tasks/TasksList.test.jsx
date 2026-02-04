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
import React from 'react';
import Axios from 'axios';
import {render, screen, waitFor, waitForElementToBeRemoved} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {sort, prop, descend, ascend, compose, toLower} from 'ramda';

import ExtJS from '../../../interface/ExtJS';
import APIConstants from '../../../constants/APIConstants';
import TestUtils from '../../../interface/TestUtils';
import UIStrings from '../../../constants/UIStrings';

import TasksStrings from '../../../constants/admin/TasksStrings';
import TasksList from './TasksList';
import {TASKS} from './Tasks.testdata';

jest.mock('axios', () => ({
  get: jest.fn()
}));

jest.mock('../../../interface/ExtJS', () => ({
  __esModule: true,
  default: {
    checkPermission: jest.fn().mockReturnValue(true),
  }
}));

const XSS_STRING = TestUtils.XSS_STRING;
const {TASKS: {LIST: LABELS}} = TasksStrings;
const {REST: {PUBLIC: {TASKS: TASKS_URL}}, SORT_DIRECTIONS: {DESC, ASC}} = APIConstants;

const selectors = {
  ...TestUtils.selectors,
  ...TestUtils.tableSelectors,
  emptyMessage: () => screen.getByText(LABELS.EMPTY_LIST),
  filter: () => screen.queryByPlaceholderText(UIStrings.FILTER),
  createButton: () => screen.getByText(LABELS.CREATE_BUTTON),
  sectionTitle: () => screen.getByText(LABELS.SECTION_TITLE),
};

const mockTasksApiResponse = (tasks) => ({
  data: {
    items: tasks.map(task => ({
      id: task.id,
      name: task.name,
      type: task.typeId,
      message: task.typeName,
      currentState: task.statusDescription?.toUpperCase() || '',
      schedule: task.schedule?.toLowerCase() || '',
      nextRun: task.nextRun || '',
      lastRun: task.lastRun || '',
      lastRunResult: task.lastRunResult || '',
    }))
  }
});

const FIELDS = {
  NAME: 'name',
  TYPE: 'typeName',
  STATUS: 'statusDescription',
  SCHEDULE: 'schedule',
  NEXT_RUN: 'nextRun',
  LAST_RUN: 'lastRun',
  LAST_RESULT: 'lastRunResult',
};

const sortTasks = (field, order = ASC) => sort((order === ASC ? ascend : descend)(compose(toLower, prop(field))), TASKS);

describe('TasksList', function() {

  const renderAndWaitForLoad = async () => {
    render(<TasksList/>);
    await waitForElementToBeRemoved(selectors.queryLoadingMask());
  }

  beforeEach(() => {
    Axios.get.mockResolvedValue(mockTasksApiResponse(TASKS));
  });

  it('renders the resolved empty data', async function() {
    Axios.get.mockResolvedValue(mockTasksApiResponse([]));
    const {createButton, emptyMessage} = selectors;

    await renderAndWaitForLoad();

    expect(createButton()).not.toHaveClass('disabled');
    expect(emptyMessage()).toBeInTheDocument();
  });

  it('renders the resolved data', async function() {
    await renderAndWaitForLoad();

    await waitFor(() => expect(Axios.get).toHaveBeenCalledWith(`/${TASKS_URL}`));

    const tasks = sortTasks(FIELDS.TYPE, DESC);

    expect(selectors.sectionTitle()).toBeInTheDocument();

    TestUtils.expectTableHeaders(Object.values(LABELS.COLUMNS));
    TestUtils.expectTableRows(tasks, Object.values(FIELDS));
  });

  it('renders the resolved data with XSS', async function() {
    const XSS_INPUT = [{
      id: TASKS[0].id,
      typeId: XSS_STRING,
      name: XSS_STRING,
      typeName: XSS_STRING,
      statusDescription: XSS_STRING,
      schedule: XSS_STRING,
      nextRun: XSS_STRING,
      lastRun: XSS_STRING,
      lastRunResult: XSS_STRING,
    }];

    const transformedStatus = 'Xss!<img src="/static/rapture/resources/icons/x16/user.png" onload="alert(0)">';
    const transformedSchedule = 'Xss!<img src="/static/rapture/resources/icons/x16/user.png" onload="alert(0)">';
    
    const XSS_EXPECTED = [{
      name: XSS_STRING,
      typeName: XSS_STRING,
      statusDescription: transformedStatus,
      schedule: transformedSchedule,
      nextRun: XSS_STRING,
      lastRun: XSS_STRING,
      lastRunResult: XSS_STRING,
    }];

    Axios.get.mockResolvedValue(mockTasksApiResponse(XSS_INPUT));

    await renderAndWaitForLoad();

    TestUtils.expectTableHeaders(Object.values(LABELS.COLUMNS));
    TestUtils.expectTableRows(XSS_EXPECTED, Object.values(FIELDS));
  });

  it('renders an error message', async function() {
    const message = 'Error Message!';
    const {tableAlert} = selectors;
    Axios.get.mockRejectedValue({message});

    await renderAndWaitForLoad();

    expect(tableAlert()).toHaveTextContent(message);
  });

  it('sorts the rows by every columns', async function () {
    const {headerCell} = selectors;
    await renderAndWaitForLoad();

    let tasks = sortTasks(FIELDS.TYPE, DESC);
    TestUtils.expectProperRowsOrder(tasks);

    userEvent.click(headerCell(LABELS.COLUMNS.TYPE));
    tasks = sortTasks(FIELDS.TYPE);
    TestUtils.expectProperRowsOrder(tasks);

    userEvent.click(headerCell(LABELS.COLUMNS.NAME));
    tasks = sortTasks(FIELDS.NAME);
    TestUtils.expectProperRowsOrder(tasks);

    userEvent.click(headerCell(LABELS.COLUMNS.STATUS));
    tasks = sortTasks(FIELDS.STATUS);
    TestUtils.expectProperRowsOrder(tasks);

    userEvent.click(headerCell(LABELS.COLUMNS.SCHEDULE));
    userEvent.click(headerCell(LABELS.COLUMNS.SCHEDULE));
    tasks = sortTasks(FIELDS.SCHEDULE, DESC);
    TestUtils.expectProperRowsOrder(tasks);
  });

  it('filters by every columns', async function() {
    const {filter} = selectors;

    await renderAndWaitForLoad();

    await TestUtils.expectProperFilteredItemsCount(filter, '', TASKS.length);
    await TestUtils.expectProperFilteredItemsCount(filter, 'tag', 1);
    await TestUtils.expectProperFilteredItemsCount(filter, 'de', 1);
    await TestUtils.expectProperFilteredItemsCount(filter, 'script', 1);
    await TestUtils.expectProperFilteredItemsCount(filter, 'ser', 1);
    await TestUtils.expectProperFilteredItemsCount(filter, 'cleanup s', 1);
    await TestUtils.expectProperFilteredItemsCount(filter, 'blob', 1);
  });

  it('disables the create button when not enough permissions', async function() {
    const {createButton} = selectors;
    ExtJS.checkPermission.mockReturnValue(false);

    await renderAndWaitForLoad();

    expect(createButton()).toHaveClass('disabled');
  });

  it('transforms cron schedule to Advanced', async function() {
    const cronTask = [{
      id: 'test-cron-task',
      name: 'Test Cron Task',
      typeId: 'test.task',
      typeName: 'Test Task Type',
      statusDescription: 'Waiting',
      schedule: 'Advanced',
      nextRun: '2023-03-18T01:00:00.000+01:00',
      lastRun: '2023-03-17T01:00:00.000+01:00',
      lastRunResult: 'Ok',
    }];

    Axios.get.mockResolvedValue(mockTasksApiResponse(cronTask));

    await renderAndWaitForLoad();

    expect(screen.getByText('Advanced')).toBeInTheDocument();
  });
});
