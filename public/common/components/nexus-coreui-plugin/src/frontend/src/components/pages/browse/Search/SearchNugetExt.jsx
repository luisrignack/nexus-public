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

export default function SearchNugetExt() {
  const CRITERIA = UIStrings.SEARCH.NUGET.CRITERIA;

  return (
    <SearchFeatureExt
      title={UIStrings.SEARCH.NUGET.MENU.text}
      icon={UIStrings.SEARCH.NUGET.MENU.icon}
      criterias={[
        {
          id: 'attributes.nuget.id',
          group: CRITERIA.GROUP,
          config: {
            format: 'nuget',
            fieldLabel: CRITERIA.FIELD_LABEL.ID,
            width: 300
          }
        },
        {
          id: 'assets.attributes.nuget.tags',
          group: CRITERIA.GROUP,
          config: {
            format: 'nuget',
            fieldLabel: CRITERIA.FIELD_LABEL.TAGS,
            width: 300
          }
        }
      ]}
      filter={{
        id: 'nuget',
        name: 'NuGet',
        text: UIStrings.SEARCH.NUGET.MENU.text,
        description: UIStrings.SEARCH.NUGET.MENU.description,
        readOnly: true,
        criterias: [
          { id: 'format', value: 'nuget', hidden: true },
          { id: 'attributes.nuget.id' },
          { id: 'assets.attributes.nuget.tags' }
        ]
      }}
    />
  );
}
