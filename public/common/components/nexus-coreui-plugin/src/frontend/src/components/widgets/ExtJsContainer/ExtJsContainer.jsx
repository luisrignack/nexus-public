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
import React, { useEffect, useRef, useState } from 'react';
import PropTypes from 'prop-types';
import { NxPageMain } from '@sonatype/react-shared-components';

import { useSize } from '../../../hooks/useSize';
import { useExtComponent } from './useExtComponent';
import { useExtJsUnsavedChangesGuard } from '@sonatype/nexus-ui-plugin';
import MaliciousRiskOnDisk from '../riskondisk/MaliciousRiskOnDisk';
import './ExtJsContainer.scss';

export function ExtJsContainer({ className, extView, extParams, showsMaliciousRiskBanner, title, icon }) {
  const iconName = icon ? `x-fa fa-${icon.iconName}` : undefined;
  const extContainerRef = useRef(null);
  const wrapperRef = useRef(null);
  const size = useSize(wrapperRef);
  const extComponent = useExtComponent(extContainerRef, extView, extParams, title, iconName);
  const [maliciousRiskHeight, setMaliciousRiskHeight] = useState(0);

  // Resize the Ext JS component when the wrapper resizes
  useEffect(() => {
    if (extComponent) {
      extComponent.setHeight(Math.floor(size.height - maliciousRiskHeight));
      extComponent.setWidth(size.width);
      extComponent.updateLayout();
    }
  }, [size.height, size.width, maliciousRiskHeight]);

  useExtJsUnsavedChangesGuard(extContainerRef);

  return (
    <NxPageMain ref={wrapperRef} className='nxrm-ext-js-wrapper'>
      {showsMaliciousRiskBanner ? <MaliciousRiskOnDisk onSizeChanged={onMaliciousRiskSizeChanged} /> : null}
      <div className={className} ref={extContainerRef}></div>
    </NxPageMain>
  );

  function onMaliciousRiskSizeChanged(width, height) {
    setMaliciousRiskHeight(height);
  }
}

ExtJsContainer.propTypes = {
  className: PropTypes.string,
  extView: PropTypes.string,
};
