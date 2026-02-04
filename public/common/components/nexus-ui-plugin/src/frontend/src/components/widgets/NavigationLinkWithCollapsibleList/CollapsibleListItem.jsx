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

import PropTypes from 'prop-types';
import React from 'react';
import { NxTextLink } from '@sonatype/react-shared-components';

const CollapsibleListItem = ({ text, href, isSelected, ...props }) => {
  return (
    <li className="nxrm-navigation-expandable-link__expandable-list__item">
      <NxTextLink
        href={href}
        className={`nxrm-navigation-expandable-link__expandable-list__navigation-link ${isSelected ? 'selected' : ''}`}
        {...props}
      >
        {text}
      </NxTextLink>
    </li>
  );
};

CollapsibleListItem.propTypes = {
  href: PropTypes.string.isRequired,
  isSelected: PropTypes.bool,
  props: PropTypes.any,
  text: PropTypes.oneOfType([PropTypes.string, PropTypes.element]).isRequired
};

export default CollapsibleListItem;
