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
import { LoginPage } from '@sonatype/nexus-ui-plugin';

import proLogo from "../../../../art/logos/logo-pro-edition-header.svg";
import proDarkLogo from "../../../../art/logos/logo-pro-edition-header-dark.svg";
import ceLogo from "../../../../art/logos/logo-community-edition-header.svg";
import ceDarkLogo from "../../../../art/logos/logo-community-edition-header-dark.svg";
import coreLogo from "../../../../art/logos/logo-core-edition-header.svg";
import coreDarkLogo from "../../../../art/logos/logo-core-edition-header-dark.svg";

const logoConfig = {
  proLight: proLogo,
  proDark: proDarkLogo,
  ceLight: ceLogo,
  ceDark: ceDarkLogo,
  coreLight: coreLogo,
  coreDark: coreDarkLogo
};

/**
 * Core UI login page wrapper with all edition logos configured.
 */
export default function CoreUILoginPageWrapper() {
  return <LoginPage logoConfig={logoConfig} />;
}
