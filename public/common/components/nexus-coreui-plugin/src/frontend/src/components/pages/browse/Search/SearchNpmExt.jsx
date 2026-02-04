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

export default function SearchNpmExt() {
  const CRITERIA = UIStrings.SEARCH.NPM.CRITERIA;

  return (
    <SearchFeatureExt
      title={UIStrings.SEARCH.NPM.MENU.text}
      icon={UIStrings.SEARCH.NPM.MENU.icon}
      criterias={[
        {
          id: 'group',
          group: CRITERIA.GROUP,
          config: {
            format: 'npm',
            fieldLabel: CRITERIA.FIELD_LABEL.SCOPE,
            width: 250
          }
        },
        {
          id: 'assets.attributes.npm.author',
          group: CRITERIA.GROUP,
          config: {
            format: 'npm',
            fieldLabel: CRITERIA.FIELD_LABEL.AUTHOR,
            width: 250
          }
        },
        {
          id: 'assets.attributes.npm.description',
          group: CRITERIA.GROUP,
          config: {
            format: 'npm',
            fieldLabel: CRITERIA.FIELD_LABEL.DESCRIPTION,
            width: 250
          }
        },
        {
          id: 'assets.attributes.npm.keywords',
          group: CRITERIA.GROUP,
          config: {
            format: 'npm',
            fieldLabel: CRITERIA.FIELD_LABEL.KEYWORDS,
            width: 250
          }
        },
        {
          id: 'assets.attributes.npm.license',
          group: CRITERIA.GROUP,
          config: {
            format: 'npm',
            fieldLabel: CRITERIA.FIELD_LABEL.LICENSE,
            width: 250
          }
        }
      ]}
      filter={{
        id: 'npm',
        name: 'npm',
        text: UIStrings.SEARCH.NPM.MENU.text,
        description: UIStrings.SEARCH.NPM.MENU.description,
        readOnly: true,
        criterias: [
          { id: 'format', value: 'npm', hidden: true },
          { id: 'group' },
          { id: 'name.raw' },
          { id: 'version' },
          { id: 'assets.attributes.npm.author' },
          { id: 'assets.attributes.npm.description' },
          { id: 'assets.attributes.npm.keywords' },
          { id: 'assets.attributes.npm.license' }
        ]
      }}
    />
  );
}
