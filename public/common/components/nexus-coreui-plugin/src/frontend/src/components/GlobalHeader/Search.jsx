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
import { useCurrentStateAndParams } from '@uirouter/react';
import { NxFilterInput } from '@sonatype/react-shared-components';
import {ExtJS, useIsVisible, handleExtJsUnsavedChanges} from '@sonatype/nexus-ui-plugin';
import { useRouter } from '@uirouter/react';
import {ROUTE_NAMES} from "../../routerConfig/routeNames/routeNames";

export default function Search() {
  const [searchValue, setSearchValue] = useState("");
  const router = useRouter();
  const { state} = useCurrentStateAndParams();

  const searchRoute = router.stateRegistry.get(ROUTE_NAMES.BROWSE.SEARCH.GENERIC);
  const isVisible = useIsVisible(searchRoute?.data?.visibilityRequirements);

  if (!isVisible) {
    return null;
  }

  return (<NxFilterInput
      placeholder="Search components"
      value={searchValue}
      onChange={setSearchValue}
      searchIcon
      onKeyPress={onSearchKeyPress}
      inputAttributes={{
        title: "Search components"
      }}
      data-analytics-id="nxrm-global-header-search-input"
  />);

  async function onSearchKeyPress(key) {
    if (key === 'Enter') {
      // Try to get the ExtJS Menu controller
      const menuCtrl =
        window.Ext && Ext.getApplication && Ext.getApplication().getController
          ? Ext.getApplication().getController('Menu')
          : null;
      handleExtJsUnsavedChanges(menuCtrl, async () => {
        if (state.name !== ROUTE_NAMES.BROWSE.SEARCH.GENERIC) {
          await router.stateService.go(ROUTE_NAMES.BROWSE.SEARCH.GENERIC, { keyword: `=keyword=${encodeURIComponent(searchValue)}` });
        } else {
          ExtJS.search(searchValue);
        }
      });
    }
  }
}

