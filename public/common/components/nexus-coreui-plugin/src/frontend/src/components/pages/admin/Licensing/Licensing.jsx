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
import React, { useState } from 'react';
import {useMachine} from '@xstate/react';

import {faWallet} from '@fortawesome/free-solid-svg-icons';

import {
  ContentBody,
  ExtJS,
  Page,
  PageHeader,
  PageTitle
} from '@sonatype/nexus-ui-plugin';

import LicenseDetails from './LicenseDetails';
import InstallLicense from './InstallLicense';

import Machine from './LicenseMachine';

import UIStrings from '../../../../constants/UIStrings';

import './Licensing.scss';
import LicensedUsage from "./LicensedUsage";

import { NxTabs, NxTabList, NxTab, NxTabPanel } from '@sonatype/react-shared-components';
import {HistoricalUsage, historicalUsageColumns} from '@sonatype/nexus-ui-plugin';

const {LICENSING: {MENU}} = UIStrings;

export default function Licensing() {
  const [state, , service] = useMachine(Machine, {devTools: true});

  const {data, loadError} = state.context;
  const showDetails = !loadError && data.contactCompany;
  const showLicensedUsage = !loadError && data?.maxRepoRequests && data?.maxRepoComponents;
  const [activeTabId, setActiveTabId] = useState(0);
  const canViewHistoricalUsage = ExtJS.checkPermission('nexus:metrics:read');
  const requiredColumns = [
    historicalUsageColumns.metricDateMonth,
    historicalUsageColumns.peakComponents,
    historicalUsageColumns.percentageChangeComponent,
    historicalUsageColumns.totalRequests,
    historicalUsageColumns.percentageChangeRequests,
    historicalUsageColumns.totalEgress,
    historicalUsageColumns.peakStorage
  ];

  return <Page>
    <PageHeader>
      <PageTitle
          icon={faWallet}
          text={MENU.text}
          description={MENU.description}
      />
    </PageHeader>
    <ContentBody className="nxrm-licensing">
    <NxTabs activeTab={activeTabId} onTabSelect={setActiveTabId}>
          <NxTabList aria-label="Tabs in a tile with no header">
            <NxTab data-analytics-id="license-tab">License</NxTab>
            {canViewHistoricalUsage && <NxTab data-analytics-id="usage-tab">Usage</NxTab>}
          </NxTabList>
          <NxTabPanel>
            {showDetails && <LicenseDetails service={service}/>}
            {showLicensedUsage && <LicensedUsage maxRepoRequests={data.maxRepoRequests.toLocaleString()} maxRepoComponents={data.maxRepoComponents.toLocaleString()}/>}
            <InstallLicense service={service}/>
          </NxTabPanel>
          {canViewHistoricalUsage &&
              <NxTabPanel>
                <HistoricalUsage columns={requiredColumns}/>
              </NxTabPanel>}
        </NxTabs>
    </ContentBody>
  </Page>;
}
