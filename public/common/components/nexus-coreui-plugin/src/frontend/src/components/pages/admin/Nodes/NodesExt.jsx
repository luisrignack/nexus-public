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
import { ExtJS } from "@sonatype/nexus-ui-plugin/src/frontend/src";
import Nodes from "./NodeList";
import FeatureFlags from "../../../../constants/FeatureFlags";
import UIStrings from '../../../../constants/UIStrings';

const { DATASTORE_CLUSTERED_ENABLED } = FeatureFlags;

export default function NodesExt() {
  const shouldShowReactVersion = ExtJS.useState(() =>
    ExtJS.state().getValue(DATASTORE_CLUSTERED_ENABLED)
  );

  if (shouldShowReactVersion) {
    return <Nodes />;
  } else {
    return (
      <ExtJsContainer
        title={UIStrings.NODES.MENU.text}
        icon={UIStrings.NODES.MENU.icon}
        className="nxrm-nodes"
        extView="NX.coreui.view.system.Nodes"
      />
    );
  }
}
