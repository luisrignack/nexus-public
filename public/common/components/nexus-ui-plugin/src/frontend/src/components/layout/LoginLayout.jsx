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
import { NxGlobalHeader2, NxPageMain } from "@sonatype/react-shared-components";
import { ExtJS } from '@sonatype/nexus-ui-plugin';

import './LoginLayout.scss';

/**
 * Minimal layout for login page with only branding/logo in header.
 * No navigation menu or other header components.
 */
export default function LoginLayout({ children, logoConfig }) {
  const COMMUNITY = "COMMUNITY";
  const PRO = "PRO";

  const edition = ExtJS.useState(() => ExtJS.state().getEdition());
  const contextPath = ExtJS.useState(() => ExtJS.state().getValue('nexus-context-path', ''));

  function getLogoProps() {
    return {
      lightPath: getLogo(),
      darkPath: getDarkLogo(),
      altText: `Sonatype Nexus Repository ${getEditionText()}`
    };
  }

  function getLogo() {
    return edition === COMMUNITY ? (logoConfig?.ceLight || logoConfig?.proLight)
        : edition === PRO ? logoConfig?.proLight
        : (logoConfig?.coreLight || logoConfig?.proLight); // Core or fallback to pro
  }

  function getDarkLogo() {
    return edition === COMMUNITY ? (logoConfig?.ceDark || logoConfig?.proDark)
        : edition === PRO ? logoConfig?.proDark
        : (logoConfig?.coreDark || logoConfig?.proDark); // Core or fallback to pro
  }

  function getEditionText() {
    return edition === COMMUNITY ? "Community"
        : edition === PRO ? "Professional"
        : "Core";
  }

  return (
    <>
      <NxGlobalHeader2
        homeHref={contextPath || "/"}
        logoProps={getLogoProps()}
        className="login-header"
      >
      </NxGlobalHeader2>
      <NxPageMain className="nxrm-login-page-main">
        {children}
      </NxPageMain>
    </>
  );
}
