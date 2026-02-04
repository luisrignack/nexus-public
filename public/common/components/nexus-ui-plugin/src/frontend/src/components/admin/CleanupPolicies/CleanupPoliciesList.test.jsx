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
import axios from 'axios';
import {waitForElementToBeRemoved} from '@testing-library/react';
import TestUtils from '../../../interface/TestUtils';
import {render, screen, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import CleanupPoliciesList from './CleanupPoliciesList';

jest.mock('axios', () => ({
  get: jest.fn()
}));

const mockRouterGo = jest.fn();

jest.mock('@uirouter/react', () => ({
  ...jest.requireActual('@uirouter/react'),
  useRouter: () => ({
    stateService: {
      go: mockRouterGo,
    }
  })
}));

function renderComponent() {
  return render(<CleanupPoliciesList />);
}

const NUM_HEADERS = 1;
const NAME = 0;
const FORMAT = 1;

beforeEach(() => {
  mockRouterGo.mockClear();
});
const NOTES = 2;

const selectors = {
  ...TestUtils.selectors,
  bodyRows: () => screen.getAllByRole('row').slice(NUM_HEADERS),
  tableHeader: (text) => screen.getByText(text, {selector: 'thead *'}),
  cleanupPoliciesName: (row) => within(selectors.bodyRows()[row]).getAllByRole('cell')[NAME],
  cleanupPoliciesFormat: (row) => within(selectors.bodyRows()[row]).getAllByRole('cell')[FORMAT],
  cleanupPoliciesNotes: (row) => within(selectors.bodyRows()[row]).getAllByRole('cell')[NOTES],
  filter: () => screen.getByPlaceholderText('Filter')
};

describe('CleanupPoliciesList', function() {
  const {
    tableHeader,
    filter,
    cleanupPoliciesName,
    cleanupPoliciesFormat,
    cleanupPoliciesNotes,
    queryLoadingMask
  } = selectors;

  const rows = [
    {
      name: 'cleanup',
      format: 'testformat cleanup',
      notes: 'cleanup-description'
    },{
      name: 'test',
      format: 'testformat test',
      notes: 'notes'
    }
  ];

  beforeEach(() => {
    axios.get.mockImplementation((url) => {
      if (url === 'service/rest/internal/cleanup-policies') {
        return Promise.resolve({data: rows});
      }
      else if (url === 'service/rest/internal/cleanup-policies/criteria/formats') {
        return Promise.resolve({
          data: [{
            'id' : 'testformat',
            'name' : 'Test Format',
            'availableCriteria' : ['lastBlobUpdated', 'lastDownloaded', 'isPrerelease', 'regex']
          }]
        });
      }
    });
  });

  it('renders the resolved data', async function() {
    renderComponent();

    await waitForElementToBeRemoved(queryLoadingMask());

    expect(cleanupPoliciesName(0)).toHaveTextContent('cleanup');
    expect(cleanupPoliciesFormat(0)).toHaveTextContent('testformat cleanup');
    expect(cleanupPoliciesNotes(0)).toHaveTextContent('cleanup-description');

    expect(cleanupPoliciesName(1)).toHaveTextContent('test');
    expect(cleanupPoliciesFormat(1)).toHaveTextContent('testformat test');
    expect(cleanupPoliciesNotes(1)).toHaveTextContent('notes');
  });

  it('sorts the rows by name', async function () {
    renderComponent();

    await waitForElementToBeRemoved(queryLoadingMask());

    expect(cleanupPoliciesName(0)).toHaveTextContent('cleanup');
    expect(cleanupPoliciesName(1)).toHaveTextContent('test');

    userEvent.click(tableHeader('Name'));

    expect(cleanupPoliciesName(0)).toHaveTextContent('test');
    expect(cleanupPoliciesName(1)).toHaveTextContent('cleanup');
  });

  it('sorts the rows by format', async function () {
    renderComponent();

    await waitForElementToBeRemoved(queryLoadingMask());

    userEvent.click(tableHeader('Format'));

    expect(cleanupPoliciesFormat(0)).toHaveTextContent('testformat cleanup');
    expect(cleanupPoliciesFormat(1)).toHaveTextContent('testformat test');

    userEvent.click(tableHeader('Format'));

    expect(cleanupPoliciesFormat(0)).toHaveTextContent('testformat test');
    expect(cleanupPoliciesFormat(1)).toHaveTextContent('testformat cleanup');
  });

  it('sorts the rows by notes', async function () {
    renderComponent();

    await waitForElementToBeRemoved(queryLoadingMask());

    userEvent.click(tableHeader('Description'));

    expect(cleanupPoliciesNotes(0)).toHaveTextContent('cleanup-description');
    expect(cleanupPoliciesNotes(1)).toHaveTextContent('notes');

    userEvent.click(tableHeader('Description'));

    expect(cleanupPoliciesNotes(0)).toHaveTextContent('notes');
    expect(cleanupPoliciesNotes(1)).toHaveTextContent('cleanup-description');
  });

  it('filters by name', async function() {
    renderComponent();

    await waitForElementToBeRemoved(queryLoadingMask());

    await TestUtils.changeField(filter, 'cleanup');

    expect(selectors.bodyRows().length).toBe(1);
    expect(cleanupPoliciesName(0)).toHaveTextContent('cleanup');
    expect(cleanupPoliciesFormat(0)).toHaveTextContent('testformat cleanup');
    expect(cleanupPoliciesNotes(0)).toHaveTextContent('cleanup-description');
  });

  it('navigates to create route when Create button is clicked', async function() {
    renderComponent();

    await waitForElementToBeRemoved(queryLoadingMask());

    const createButton = screen.getByRole('button', { name: 'Create Cleanup Policy' });
    await userEvent.click(createButton);

    expect(mockRouterGo).toHaveBeenCalledWith('admin.repository.cleanuppolicies.create');
  });

  it('navigates to edit route when row is clicked', async function() {
    renderComponent();

    await waitForElementToBeRemoved(queryLoadingMask());

    const firstRow = selectors.bodyRows()[0];
    await userEvent.click(firstRow);

    expect(mockRouterGo).toHaveBeenCalledWith('admin.repository.cleanuppolicies.edit', { itemId: 'cleanup' });
  });
});
