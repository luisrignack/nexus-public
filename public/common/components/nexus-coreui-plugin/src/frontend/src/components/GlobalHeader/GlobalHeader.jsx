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

import React from "react";

import {
  NxButton,
  NxFontAwesomeIcon,
  NxGlobalHeader2,
} from "@sonatype/react-shared-components";
import { faSync } from "@fortawesome/free-solid-svg-icons";
import { ExtJS, handleExtJsUnsavedChanges } from '@sonatype/nexus-ui-plugin';
import proLogo from "../../../../art/logos/logo-pro-edition-header.svg";
import proDarkLogo from "../../../../art/logos/logo-pro-edition-header-dark.svg";
import ceLogo from "../../../../art/logos/logo-community-edition-header.svg";
import ceDarkLogo from "../../../../art/logos/logo-community-edition-header-dark.svg";
import coreLogo from "../../../../art/logos/logo-core-edition-header.svg";
import coreDarkLogo from "../../../../art/logos/logo-core-edition-header-dark.svg";
import { useRouter } from '@uirouter/react';

import './Globalheader.scss'
import HelpMenu from './HelpMenu';
import LoginAndUserButton from './LogInAndUserProfileMenu';
import Search from './Search';
import SystemStatus from './SystemStatus';
import {ThemeSelector} from "@sonatype/nexus-ui-plugin/src/frontend/src";
import { refreshReactPage } from '../../routerConfig/routerUtils';

export default function GlobalHeader() {
  const COMMUNITY = "COMMUNITY";
  const PRO = "PRO";

  const router = useRouter();
  const edition = ExtJS.useState(() => ExtJS.state().getEdition());

  const refreshTitle = "Refresh";

  const showThemeSelector = window.location.search.includes('showThemeSelector');

  const contextPath = ExtJS.useState(() => ExtJS.state().getValue('nexus-context-path', ''));

  return (
      <NxGlobalHeader2
          homeHref={ contextPath || "/"}
          logoProps={getLogoProps()}
          className="nxrm-global-header"
      >
        <Search />

        <SystemStatus />

        <NxButton
            title={refreshTitle}
            aria-label={refreshTitle}
            variant="icon-only"
            onClick={onRefreshClick}
            data-analytics-id="nxrm-global-header-refresh-button"
        >
          <NxFontAwesomeIcon icon={faSync} />
        </NxButton>

        <HelpMenu />

        <LoginAndUserButton />

        {showThemeSelector && <ThemeSelector />}
      </NxGlobalHeader2>);

  function onRefreshClick() {
    // Try to get the ExtJS Menu controller
    const menuCtrl =
      window.Ext && Ext.getApplication && Ext.getApplication().getController
        ? Ext.getApplication().getController('Menu')
        : null;

    handleExtJsUnsavedChanges(menuCtrl, () => {
      if (ExtJS.isExtJsRendered()) {
        ExtJS.refresh();
      } else {
        refreshReactPage(router);
      }
    });
  }

  function getLogoProps() {
    return {
      lightPath: getLogo(),
      darkPath: getDarkLogo(), // Needs to be replaced with a true dark mode logo before we can enable dark mode
      altText: `Sonatype Nexus Repository ${getEditionText()}`
    }
  }

  function getLogo() {
    return edition === COMMUNITY ? ceLogo
        : edition === PRO ? proLogo
        : coreLogo; // Core or catch all for unknown
  }

  function getDarkLogo() {
    return edition === COMMUNITY ? ceDarkLogo
        : edition === PRO ? proDarkLogo
            : coreDarkLogo; // Core or catch all for unknown
  }

  function getEditionText() {
    return edition === COMMUNITY ? "Community"
        : edition === PRO ? "Professional"
        : "Core";
  }
}
