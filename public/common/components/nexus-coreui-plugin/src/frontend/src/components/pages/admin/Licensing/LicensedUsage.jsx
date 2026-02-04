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
import {
  NxReadOnly,
  NxGrid,
  NxH2,
  NxTile,
} from '@sonatype/react-shared-components';
import {
  ReadOnlyField,
  scrollToUsageCenter
} from '@sonatype/nexus-ui-plugin';

import UIStrings from '../../../../constants/UIStrings';

const {LICENSING: {DETAILS: LABELS}, LICENSING} = UIStrings;

export default function LicensedUsage({maxRepoRequests, maxRepoComponents}) {
  return (
      <NxTile>
        <NxTile.Content>
            <NxH2>{LICENSING.SECTIONS.USAGE}</NxH2>
            <NxGrid.Row>
              <NxGrid.Column className="nx-grid-col--25">
                <NxReadOnly>
                  <ReadOnlyField label={LABELS.REQUESTS_PER_MONTH.LABEL} value={maxRepoRequests}/>
                  <ReadOnlyField label={LABELS.TOTAL_COMPONENTS.LABEL} value={maxRepoComponents}/>
                </NxReadOnly>
              </NxGrid.Column>
              <NxGrid.Column>
                <NxReadOnly>
                  <NxReadOnly.Item>
                    <ReadOnlyField value={LABELS.USAGE_DESCRIPTION.LABEL(scrollToUsageCenter)}/>
                  </NxReadOnly.Item>
                </NxReadOnly>
              </NxGrid.Column>
            </NxGrid.Row>
        </NxTile.Content>
      </NxTile>
  );
}
