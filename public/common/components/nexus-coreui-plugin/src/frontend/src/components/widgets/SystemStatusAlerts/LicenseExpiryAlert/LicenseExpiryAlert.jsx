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
import {ExtJS} from '@sonatype/nexus-ui-plugin';
import SystemNotice from '../SystemNotice';

/**
 * Component that displays a warning banner when the license is about to expire.
 * Uses state from LicenseExpiryWarningStateContributor.
 * 
 */
export default function LicenseExpiryAlert() {
  const licenseExpiryWarning = ExtJS.useState(() => ExtJS.state().getValue('licenseExpiryWarning'));
  const user = ExtJS.useUser();
  const isAdmin = user?.administrator;

  // If no warning state or not enabled, don't show anything
  if (!isAdmin || !licenseExpiryWarning || !licenseExpiryWarning.enabled) {
    return null;
  }
  
  return (
    <SystemNotice
      noticeLevel="warning"
      title="License Expiry Warning"
    >
      {licenseExpiryWarning.message || "Your license will expire soon. Please contact Sonatype to renew your license."}
    </SystemNotice>
  );
}
