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

import { ExtJsContainer } from "../../../widgets/ExtJsContainer/ExtJsContainer";
import React from "react";
import { ExtJS } from '@sonatype/nexus-ui-plugin/src/frontend/src';
import Repositories from "./Repositories";
import UIStrings from '../../../../constants/UIStrings';

export default function RepositoriesExt() {
   const shouldShowReactVersion = ExtJS.useState(() => ExtJS.state().getValue('nexus.react.repositories'));

   if (shouldShowReactVersion) {
       return <Repositories />
   } else {
       return <ExtJsContainer
           title={UIStrings.REPOSITORIES.MENU.text}
           icon={UIStrings.REPOSITORIES.MENU.icon}
           className="nx-feature-content"
           extView="NX.coreui.view.repository.RepositoryFeature"
       />;
   }
}
