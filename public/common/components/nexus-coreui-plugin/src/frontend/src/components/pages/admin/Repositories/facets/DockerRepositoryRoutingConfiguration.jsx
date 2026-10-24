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

import {NxH2, NxInfoAlert} from '@sonatype/react-shared-components';

import UIStrings from '../../../../../constants/UIStrings';

const {ROUTING} = UIStrings.REPOSITORIES.EDITOR.DOCKER;

/**
 * Explains how Docker repositories of this build are addressed.
 *
 * Unlike Nexus Pro, this fork does not create a dedicated Jetty connector per repository. Every Docker repository
 * is served by the shared registry endpoint and selected by the first path segment after /v2/.
 */
export default function DockerRepositoryRoutingConfiguration({parentMachine}) {
  const [state] = parentMachine;
  const name = state.context.data.name || '<repository>';

  return (
    <>
      <NxH2>{ROUTING.CAPTION}</NxH2>
      <NxInfoAlert>{ROUTING.HELP}</NxInfoAlert>
      <p className="nxrm-docker-connectors-help">
        {ROUTING.PULL_LABEL} <code>{ROUTING.PULL_TEMPLATE(name)}</code>
      </p>
    </>
  );
}
