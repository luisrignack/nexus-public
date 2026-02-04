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

export default function SearchRubygemsExt() {
  const CRITERIA = UIStrings.SEARCH.RUBYGEMS.CRITERIA;

  return (
    <SearchFeatureExt
      title={UIStrings.SEARCH.RUBYGEMS.MENU.text}
      icon={UIStrings.SEARCH.RUBYGEMS.MENU.icon}
      criterias={[
        {
          id: 'assets.attributes.rubygems.platform',
          group: CRITERIA.GROUP,
          config: {
            format: 'rubygems',
            fieldLabel: CRITERIA.FIELD_LABEL.PLATFORM,
            width: 250
          }
        },
        {
          id: 'assets.attributes.rubygems.summary',
          group: CRITERIA.GROUP,
          config: {
            format: 'rubygems',
            fieldLabel: CRITERIA.FIELD_LABEL.SUMMARY,
            width: 250
          }
        },
        {
          id: 'assets.attributes.rubygems.description',
          group: CRITERIA.GROUP,
          config: {
            format: 'rubygems',
            fieldLabel: CRITERIA.FIELD_LABEL.DESCRIPTION,
            width: 250
          }
        }
      ]}
      filter={{
        id: 'rubygems',
        name: 'Rubygems',
        text: UIStrings.SEARCH.RUBYGEMS.MENU.text,
        description: UIStrings.SEARCH.RUBYGEMS.MENU.description,
        readOnly: true,
        criterias: [
          { id: 'format', value: 'rubygems', hidden: true },
          { id: 'name.raw' },
          { id: 'version' },
          { id: 'assets.attributes.rubygems.platform' },
          { id: 'assets.attributes.rubygems.summary' },
          { id: 'assets.attributes.rubygems.description' }
        ]
      }}
    />
  );
}
