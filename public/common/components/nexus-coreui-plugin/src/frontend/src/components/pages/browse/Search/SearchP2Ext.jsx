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

export default function SearchP2Ext() {
  const CRITERIA = UIStrings.SEARCH.P2.CRITERIA;

  return (
    <SearchFeatureExt
      title={UIStrings.SEARCH.P2.MENU.text}
      icon={UIStrings.SEARCH.P2.MENU.icon}
      criterias={[
        {
          id: 'attributes.p2.pluginName',
          group: CRITERIA.GROUP,
          config: {
            format: 'p2',
            fieldLabel: CRITERIA.FIELD_LABEL.PLUGIN_NAME,
            width: 250
          }
        }
      ]}
      filter={{
        id: 'p2',
        name: 'p2',
        text: UIStrings.SEARCH.P2.MENU.text,
        description: UIStrings.SEARCH.P2.MENU.description,
        readOnly: true,
        criterias: [
          {id: 'format', value: 'p2', hidden: true},
          {id: 'attributes.p2.pluginName'},
          {id: 'name.raw'}
        ]
      }}
    />
  );
}
