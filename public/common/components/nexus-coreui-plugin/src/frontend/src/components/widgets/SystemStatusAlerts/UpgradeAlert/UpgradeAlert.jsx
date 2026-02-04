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

import React, {useState} from 'react';
import PropTypes from "prop-types";
import axios from 'axios';

import {
  NxButton,
  NxLoadingSpinner, NxTextLink,
} from '@sonatype/react-shared-components';
import {ExtJS} from '@sonatype/nexus-ui-plugin';

import UIStrings from '../../../../constants/UIStrings';
import {UpgradeAlertFunctions} from './UpgradeAlertHelper';

import UpgradeTriggerModal from './UpgradeTriggerModal';
import SystemNotice from '../SystemNotice';

const {UPGRADE_ALERT: {PENDING, PROGRESS, ERROR, COMPLETE, WARN}} = UIStrings;

export default function UpgradeAlert({onClose}) {
  const hasUser = ExtJS.useState(UpgradeAlertFunctions.hasUser);
  const state = ExtJS.useState(UpgradeAlertFunctions.currentState);
  const message = ExtJS.useState(UpgradeAlertFunctions.message);
  const hasPermission = ExtJS.usePermission(UpgradeAlertFunctions.checkPermissions);
  const [showModal, setShowModal] = useState(false);

  if (!hasUser || !hasPermission) {
    return null;
  }

  switch (state) {
    case 'needsUpgrade':
      return (
        <SystemNotice noticeLevel="info" nonDismissable={true} title={PENDING.LABEL}>
          <div className="nxrm-upgrade-alert-needs-upgrade">
              <div>{PENDING.TEXT}</div>

              <NxButton
                  data-analytics-id="nxrm-upgrade-alert-finalize-upgrade-btn"
                  variant="primary"
                  onClick={() => setShowModal(true)}
              >
                {PENDING.FINALIZE_BUTTON}
              </NxButton>
          </div>

          <UpgradeTriggerModal showModal={showModal} setShowModal={setShowModal} />
        </SystemNotice>);
    case 'versionMismatch':
      return (
          <SystemNotice title={WARN.LABEL} noticeLevel="warning" onClose={dismissAlert}>
            {WARN.TEXT}
          </SystemNotice>);
    case 'nexusUpgradeInProgress':
      return (
        <SystemNotice noticeLevel="info" nonDismissable={true}>
          <NxLoadingSpinner><strong>{PROGRESS.LABEL}</strong></NxLoadingSpinner>
        </SystemNotice>);
    case 'nexusUpgradeError':
      return (
          <SystemNotice noticeLevel="error" title={ERROR.LABEL} onClose={onClose}>
            {message} {ERROR.TEXT}{" "}
            <NxTextLink external href={ERROR.CONTACT_SUPPORT.HREF}>
              {ERROR.CONTACT_SUPPORT.TEXT}
            </NxTextLink> for assistance.
          </SystemNotice>);
    case 'nexusUpgradeComplete':
      return (
          <SystemNotice
              noticeLevel="success"
              title={COMPLETE.LABEL}
              onClose={dismissAlert}
          >
            {COMPLETE.TEXT}
          </SystemNotice>);
    default:
      return null;
  }

  function dismissAlert() {
    axios.delete('service/rest/v1/clustered/upgrade-database-schema')
  }
}

UpgradeAlert.propTypes = {
  onClose: PropTypes.func
}
