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

import SearchFeatureExt from './SearchFeatureExt';
import UIStrings from '../../../../constants/UIStrings';

export default function SearchYumExt() {
  const CRITERIA = UIStrings.SEARCH.YUM.CRITERIA;

  return (
    <SearchFeatureExt
      title={UIStrings.SEARCH.YUM.MENU.text}
      icon={UIStrings.SEARCH.YUM.MENU.icon}
      criterias={[
        {
          id: 'assets.attributes.yum.architecture',
          group: CRITERIA.GROUP,
          config: {
            format: 'yum',
            fieldLabel: CRITERIA.FIELD_LABEL.ARCHITECTURE,
            width: 250
          }
        },
        {
          id: 'attributes.yum.name',
          group: CRITERIA.GROUP,
          config: {
            format: 'yum',
            fieldLabel: CRITERIA.FIELD_LABEL.NAME,
            width: 250
          }
        }
      ]}
      filter={{
        id: 'yum',
        name: 'Yum',
        text: UIStrings.SEARCH.YUM.MENU.text,
        description: UIStrings.SEARCH.YUM.MENU.description,
        readOnly: true,
        criterias: [
          { id: 'format', value: 'yum', hidden: true },
          { id: 'attributes.yum.name' },
          { id: 'version' },
          { id: 'assets.attributes.yum.architecture' }
        ]
      }}
    />
  );
}
