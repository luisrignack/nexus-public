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
import React, { useCallback } from 'react';
import {useMachine} from '@xstate/react';

import {faRedo, faScroll} from '@fortawesome/free-solid-svg-icons';

import {ListMachineUtils} from '@sonatype/nexus-ui-plugin';
import {
  NxButton,
  NxFilterInput,
  NxFontAwesomeIcon,
  NxTable,
  NxTableBody,
  NxTableCell,
  NxTableHead,
  NxTableRow,
} from '@sonatype/react-shared-components';

import {
  ContentBody,
  PageActions,
  PageHeader,
  PageTitle,
  Section,
  SectionToolbar,
  Page
} from '@sonatype/nexus-ui-plugin';

import LoggingConfigurationListMachine from './LoggingConfigurationListMachine';

import UIStrings from '../../../../constants/UIStrings';
import { useRouter } from '@uirouter/react';
import { ROUTE_NAMES } from '../../../../routerConfig/routeNames/routeNames';

export default function LoggingConfigurationList() {
  const router = useRouter();
  const onEdit = useCallback(itemId => router.stateService.go(ROUTE_NAMES.ADMIN.SUPPORT.LOGGING.EDIT, { itemId }));
  const onCreate = useCallback(() => router.stateService.go(ROUTE_NAMES.ADMIN.SUPPORT.LOGGING.CREATE));

  const [current, send] = useMachine(LoggingConfigurationListMachine, {devTools: true});
  const isLoading = current.matches('loading');
  const data = current.context.data;
  const filterText = current.context.filter;
  const error = current.context.error;

  const nameSortDir = ListMachineUtils.getSortDirection('name', current.context);
  const levelSortDir = ListMachineUtils.getSortDirection('level', current.context);

  function filter(value) {
    send({type: 'FILTER', filter: value});
  }

  function reset() {
    send({type: 'RESET'});
  }

  return <Page className="nxrm-logging-configuration">
    <PageHeader>

      <PageTitle
          icon={UIStrings.LOGGING.MENU.icon}
          text={UIStrings.LOGGING.MENU.text}
          description={UIStrings.LOGGING.MENU.description}
      />
      <PageActions>
        <NxButton variant="tertiary" onClick={reset}>
          <NxFontAwesomeIcon icon={faRedo}/>
          <span>{UIStrings.LOGGING.RESET_ALL_BUTTON}</span>
        </NxButton>
        <NxButton variant="primary" onClick={onCreate}>
          <span>{UIStrings.LOGGING.CREATE_BUTTON}</span>
        </NxButton>
      </PageActions>
    </PageHeader>
    <ContentBody>
      <Section className="nxrm-logging-configuration-list">
        <SectionToolbar>
          <div className="nxrm-spacer" />
          <NxFilterInput
              id="filter"
              onChange={filter}
              value={filterText}
              placeholder={UIStrings.LOGGING.FILTER_PLACEHOLDER}/>
        </SectionToolbar>
        <NxTable>
          <NxTableHead>
            <NxTableRow>
              <NxTableCell onClick={() => send({type: 'SORT_BY_NAME'})} isSortable sortDir={nameSortDir}>
                {UIStrings.LOGGING.NAME_LABEL}
              </NxTableCell>
              <NxTableCell onClick={() => send({type: 'SORT_BY_LEVEL'})} isSortable sortDir={levelSortDir}>
                {UIStrings.LOGGING.LEVEL_LABEL}
              </NxTableCell>
              <NxTableCell chevron/>
            </NxTableRow>
          </NxTableHead>
          <NxTableBody isLoading={isLoading} error={error}>
            {data.map(({name, level}) => (
                <NxTableRow key={name} onClick={() => onEdit(name)} isClickable>
                  <NxTableCell>{name}</NxTableCell>
                  <NxTableCell>{level}</NxTableCell>
                  <NxTableCell chevron/>
                </NxTableRow>
            ))}
          </NxTableBody>
        </NxTable>
      </Section>
    </ContentBody>
  </Page>;
}
