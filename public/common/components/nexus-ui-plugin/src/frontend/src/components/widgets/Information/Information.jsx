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
import PropTypes from 'prop-types';
import { NxDescriptionList } from '@sonatype/react-shared-components';

export default function Information({ information }) {
  // Insert a zero-width space (\u200B) after each period to allow word-breaking at periods.
  const ZERO_WIDTH_SPACE = '.\u200B';

  return (
    <NxDescriptionList>
      {Object.entries(information).map(([name, value]) => {
        const breakingName = name.replace(/\./g, ZERO_WIDTH_SPACE);
        return (
          <NxDescriptionList.Item key={name}>
            <NxDescriptionList.Term className="break-on-period">{breakingName}</NxDescriptionList.Term>
            <NxDescriptionList.Description>{String(value)}</NxDescriptionList.Description>
          </NxDescriptionList.Item>
        );
      })}
    </NxDescriptionList>
  );
}

Information.propTypes = {
  information: PropTypes.object.isRequired
};
