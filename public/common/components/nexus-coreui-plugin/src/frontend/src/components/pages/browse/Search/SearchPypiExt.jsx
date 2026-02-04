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

export default function SearchPypiExt() {
  const CRITERIA = UIStrings.SEARCH.PYPI.CRITERIA;

  return (
    <SearchFeatureExt
      title={UIStrings.SEARCH.PYPI.MENU.text}
      icon={UIStrings.SEARCH.PYPI.MENU.icon}
      criterias={[
        {
          id: 'assets.attributes.pypi.classifiers',
          group: CRITERIA.GROUP,
          config: {
            format: 'pypi',
            fieldLabel: CRITERIA.FIELD_LABEL.CLASSIFIERS,
            width: 250
          }
        },
        {
          id: 'assets.attributes.pypi.description',
          group: CRITERIA.GROUP,
          config: {
            format: 'pypi',
            fieldLabel: CRITERIA.FIELD_LABEL.DESCRIPTION,
            width: 250
          }
        },
        {
          id: 'assets.attributes.pypi.keywords',
          group: CRITERIA.GROUP,
          config: {
            format: 'pypi',
            fieldLabel: CRITERIA.FIELD_LABEL.KEYWORDS,
            width: 250
          }
        },
        {
          id: 'assets.attributes.pypi.summary',
          group: CRITERIA.GROUP,
          config: {
            format: 'pypi',
            fieldLabel: CRITERIA.FIELD_LABEL.SUMMARY,
            width: 250
          }
        }
      ]}
      filter={{
        id: 'pypi',
        name: 'pypi',
        text: UIStrings.SEARCH.PYPI.MENU.text,
        description: UIStrings.SEARCH.PYPI.MENU.description,
        readOnly: true,
        criterias: [
          { id: 'format', value: 'pypi', hidden: true },
          { id: 'assets.attributes.pypi.classifiers' },
          { id: 'assets.attributes.pypi.description' },
          { id: 'assets.attributes.pypi.keywords' },
          { id: 'assets.attributes.pypi.summary' }
        ]
      }}
    />
  );
}
