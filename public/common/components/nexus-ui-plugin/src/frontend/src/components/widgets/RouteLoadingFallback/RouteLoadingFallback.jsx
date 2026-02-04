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
import React, { useState, useEffect } from 'react';
import { NxPageMain, NxLoadingSpinner } from '@sonatype/react-shared-components';

import './RouteLoadingFallback.scss';

/**
 * Loading fallback component displayed while route chunks are loading.
 *
 * Uses a delayed display strategy to prevent "flash of loading" on fast route transitions:
 * - For loads < 300ms: Shows nothing (feels instant)
 * - For loads > 300ms: Shows spinner (provides feedback)
 *
 * This creates a better perceived performance by avoiding jarring spinner flashes
 * on quick navigation while still providing feedback for genuinely slow loads.
 */
export default function RouteLoadingFallback() {
  const [show, setShow] = useState(false);

  useEffect(() => {
    // Delay showing the spinner to avoid flash on fast loads
    const timer = setTimeout(() => {
      setShow(true);
    }, 300);

    // Cleanup: Cancel timer if component unmounts before 300ms
    return () => {
      clearTimeout(timer);
    };
  }, []); // Empty deps - only run once on mount

  // Show nothing during the initial 300ms delay
  if (!show) {
    return null;
  }

  // After 300ms, show the centered loading spinner
  return (
    <NxPageMain>
      <div className="nxrm-route-loading">
        <NxLoadingSpinner />
      </div>
    </NxPageMain>
  );
}
