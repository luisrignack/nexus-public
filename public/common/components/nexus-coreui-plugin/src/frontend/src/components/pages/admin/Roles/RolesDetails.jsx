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
import React, {useCallback, useEffect} from 'react';
import {useMachine} from '@xstate/react';

import {
  NxTile,
} from '@sonatype/react-shared-components';
import {
  ContentBody,
  ExtJS,
  PageHeader,
  PageTitle,
  ValidationUtils,
  Page,
} from '@sonatype/nexus-ui-plugin';

import Machine from './RolesFormMachine';
import RolesForm from './RolesForm';
import RolesReadOnly from './RolesReadOnly';

import UIStrings from '../../../../constants/UIStrings';
import {useCurrentStateAndParams, useRouter} from "@uirouter/react";
import {ROUTE_NAMES} from "../../../../routerConfig/routeNames/routeNames";

const {ROLES: {FORM: LABELS}} = UIStrings;
const ADMIN = ROUTE_NAMES.ADMIN;

export default function RolesDetails() {
  const router = useRouter();
  const {state: routerState, params} = useCurrentStateAndParams();
  const onDone = useCallback(() => router.stateService.go(ADMIN.SECURITY.ROLES.LIST), [router]);
  const itemId = params?.itemId;
  const isCreate = routerState.name === ADMIN.SECURITY.ROLES.CREATE;

  useEffect(() => {
    // we should not render edit form if itemId is not provided
    if (!isCreate && !itemId) {
      router.stateService.go(ROUTE_NAMES.MISSING_ROUTE)
    }

  }, [itemId]);

  const hasDeletePermissions = ExtJS.checkPermission('nexus:roles:delete');
  const hasEditPermissions = ExtJS.checkPermission('nexus:roles:update');
  const ldapQueryCharacterLimit = ExtJS.state().getValue('nexus.ldap.mapped.role.query.character.limit');

  const [current, , service] = useMachine(Machine, {
    context: {
      externalRolesRef: null,
      pristineData: {
        id: decodeURIComponent(itemId),
      },
      ldapQueryCharacterLimit: ldapQueryCharacterLimit,
      isCreate,
    },
    actions: {
      onSaveSuccess: onDone,
      onDeleteSuccess: onDone,
    },
    guards: {
      canDelete: () => hasDeletePermissions,
    },
    devTools: true,
  });

  const {data: {readOnly}, pristineData: {id, name}} = current.context;

  const canEdit = hasEditPermissions && !readOnly;
  const showReadOnly = !isCreate && !canEdit;

  return <Page className="nxrm-roles">
    <PageHeader>
      <PageTitle
          text={isCreate ? LABELS.CREATE_TITLE : LABELS.EDIT_TILE(name || '')}
          description={isCreate ? null : LABELS.EDIT_DESCRIPTION}
      />
    </PageHeader>
    <ContentBody className="nxrm-roles-form">
      <NxTile>
        <NxTile.Content>
          {showReadOnly
              ? <RolesReadOnly service={service} onDone={onDone}/>
              : <RolesForm
                  roleId={id}
                  service={service}
                  onDone={onDone}
                  isCreate={isCreate}
              />
          }
        </NxTile.Content>
      </NxTile>
    </ContentBody>
  </Page>;
}
