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

import {NxH2, NxModal, NxButton, NxFooter, NxButtonBar} from '@sonatype/react-shared-components';
import {useRouter} from '@uirouter/react';
import {ExtJS} from '@sonatype/nexus-ui-plugin';

import UIStrings from '../../../../constants/UIStrings';
import {ROUTE_NAMES} from '../../../../routerConfig/routeNames/routeNames';

const ADMIN = ROUTE_NAMES.ADMIN;

const {
  SSL_CERTIFICATES: {
    ADD_FORM: {
      MODAL: {HEADER, CONTENT, VIEW_BUTTON}
    }
  },
  SETTINGS: {CANCEL_BUTTON_LABEL}
} = UIStrings;

export default function SslCertificatesAlreadyExistsModal({certificateId, cancel}) {
  const router = useRouter();
  const handleCancel = () => {
    ExtJS.setDirtyStatus('SslCertificatesAddFormMachine', false);
    cancel();
  };
  const goToCertificate = () => {
    ExtJS.setDirtyStatus('SslCertificatesAddFormMachine', false);
    router.stateService.go(ADMIN.SECURITY.SSLCERTIFICATES.EDIT, {itemId: encodeURIComponent(certificateId)});
  };

  return (
    <NxModal onCancel={cancel} variant="narrow">
      <NxModal.Header>
        <NxH2>{HEADER}</NxH2>
      </NxModal.Header>
      <NxModal.Content>{CONTENT}</NxModal.Content>
      <NxFooter>
        <NxButtonBar>
          <NxButton onClick={handleCancel}>{CANCEL_BUTTON_LABEL}</NxButton>
          <NxButton onClick={goToCertificate} variant="primary">
            {VIEW_BUTTON}
          </NxButton>
        </NxButtonBar>
      </NxFooter>
    </NxModal>
  );
}
