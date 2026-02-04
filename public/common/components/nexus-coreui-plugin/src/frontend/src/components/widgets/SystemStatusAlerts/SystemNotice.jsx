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
import { NxErrorAlert, NxH3, NxInfoAlert, NxSuccessAlert, NxWarningAlert } from '@sonatype/react-shared-components';

/**
 *
 * @param title
 * @param noticeLevel info | warning | error | success
 * @param children
 * @param className
 * @param onClose
 * @returns {React.JSX.Element|null}
 * @constructor
 */
export default function SystemNotice({
  title,
  noticeLevel,
  children,
  additionalAlertClassNames,
  nonDismissable,
  onClose
}) {
  const [isOpen, setIsOpen] = useState(true);
  const isTitle = !!title;

  if (isClosed()) {
    return null;
  }

  const Alert = getWrapperComponentFromNoticeLevel(noticeLevel);

  return (
      <div
          className="nxrm-system-notice nx-system-notice nx-system-notice--alert nxrm-system-notice"
          role="complementary"
          aria-label="alert system notice"
      >
        <Alert className={additionalAlertClassNames} onClose={nonDismissable ? undefined : onInternalClose}>
          { isTitle && <NxH3>{title}</NxH3> }
          {children}
        </Alert>
      </div>);

  function onInternalClose() {
    setIsOpen(false);

    if (onClose) {
      onClose();
    }
  }

  function isClosed() {
    return !isOpen
  }

  function getWrapperComponentFromNoticeLevel(noticeLevel) {
    if (noticeLevel === 'warning') {
      return NxWarningAlert;
    } else if (noticeLevel === 'error') {
      return NxErrorAlert;
    } else if (noticeLevel === 'info') {
      return NxInfoAlert;
    } else if (noticeLevel === 'success') {
      return NxSuccessAlert;
    } else {
      return NxInfoAlert;
    }
  }
}
