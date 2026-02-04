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
import React, { useCallback, useEffect } from 'react';
import {useMachine} from '@xstate/react';

import {
  NxTile,
  NxLoadWrapper,
  NxFooter,
  NxButtonBar,
  NxButton,
  NxH2,
  NxTooltip,
} from '@sonatype/react-shared-components';

import {faIdCardAlt} from '@fortawesome/free-solid-svg-icons';

import {
  ContentBody,
  PageHeader,
  PageTitle,
  Page
} from '@sonatype/nexus-ui-plugin';

import UIStrings from '../../../../constants/UIStrings';

import SslCertificatesDetails from './SslCertificatesDetails';
import Machine from './SslCertificatesDetailsFormMachine';

import { ROUTE_NAMES } from '../../../../routerConfig/routeNames/routeNames';
import {canDeleteCertificate} from './SslCertificatesHelper';
import { useCurrentStateAndParams, useRouter } from '@uirouter/react';

const ADMIN = ROUTE_NAMES.ADMIN;
const {
  SSL_CERTIFICATES: {FORM: LABELS},
} = UIStrings;

export default function SslCertificatesDetailsForm() {
  const router = useRouter();
  const {state: routerState, params} = useCurrentStateAndParams();
  const onDone = useCallback(() => router.stateService.go(ADMIN.SECURITY.SSLCERTIFICATES.LIST));
  const itemId = params?.itemId;
  const canDelete = canDeleteCertificate();

  const [state, send] = useMachine(Machine, {
    context: {
      pristineData: {
        id: decodeURIComponent(itemId),
      },
    },
    actions: {
      onSaveSuccess: onDone,
      onDeleteSuccess: onDone,
    },
    devTools: true,
  });

  useEffect(() => {
    // we should not render edit form if itemId is not provided
    if (routerState.name === ADMIN.SECURITY.SSLCERTIFICATES.EDIT && !itemId) {
      router.stateService.go(ROUTE_NAMES.MISSING_ROUTE);
    }
  }, [itemId]);

  const {data = {}, loadError} = state.context;
  const isLoading = state.matches('loading');

  const retry = () => send({type: 'RETRY'});

  const confirmDelete = () => {
    if (canDelete) {
      send({type: 'CONFIRM_DELETE'});
    }
  };

  return (
    <Page className="nxrm-ssl-certificate">
      <PageHeader>
        <PageTitle
          icon={faIdCardAlt}
          text={LABELS.DETAILS_TITLE(data.subjectCommonName || '')}
          description={LABELS.DETAILS_DESCRIPTION}
        />
      </PageHeader>
      <ContentBody className="nxrm-ssl-certificate-form">
        <NxTile>
          <NxTile.Content>
            <NxLoadWrapper
              loading={isLoading}
              error={loadError}
              retryHandler={retry}
            >
              <NxH2>{LABELS.SECTIONS.CERTIFICATE}</NxH2>
              <SslCertificatesDetails data={data} />
              <NxFooter>
                <NxButtonBar>
                  <NxButton type="button" onClick={onDone}>
                    {UIStrings.SETTINGS.CANCEL_BUTTON_LABEL}
                  </NxButton>
                  <NxTooltip title={!canDelete && UIStrings.PERMISSION_ERROR}>
                    <NxButton
                        type="button"
                        variant="primary"
                        onClick={confirmDelete}
                        className={!canDelete ? 'disabled' : ''}
                    >
                      {LABELS.BUTTONS.DELETE}
                    </NxButton>
                  </NxTooltip>
                </NxButtonBar>
              </NxFooter>
            </NxLoadWrapper>
          </NxTile.Content>
        </NxTile>
      </ContentBody>
    </Page>
  );
}
