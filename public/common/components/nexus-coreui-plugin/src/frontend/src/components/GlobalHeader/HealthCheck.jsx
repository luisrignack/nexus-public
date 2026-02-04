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
import { ExtJS } from '@sonatype/nexus-ui-plugin';
import { faCircle, faBell } from "@fortawesome/free-solid-svg-icons";
import { NxButton, NxFontAwesomeIcon } from '@sonatype/react-shared-components';

// TODO: Properly format the Bell+Circle state and write tests https://sonatype.atlassian.net/browse/NEXUS-46297
export default function HealthCheck() {
  const healthChecksFailed = ExtJS.useState(() => ExtJS.state().getValue('health_checks_failed', false));

  return (
      <NxButton title="System Status" aria-label="System Status" variant="icon-only">

        {healthChecksFailed
            ? (<span className="fa-layers fa-fw">
              <NxFontAwesomeIcon icon={faBell}/>
              <NxFontAwesomeIcon icon={faCircle} transform="shrink-8 up-3 right-3" style={{color: 'red'}}/>
              </span>)
            : <NxFontAwesomeIcon icon={faBell}/>
        }

      </NxButton>
  )
}
