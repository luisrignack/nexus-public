/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Open Source Version is distributed with Sencha Ext JS pursuant to a FLOSS Exception agreed upon
 * between Sonatype, Inc. and Sencha Inc. Sencha Ext JS is licensed under GPL v3 and cannot be redistributed as part of a
 * closed source work.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */

/**
 * Refreshes the current React page, handling URL parameter encoding
 * @param {Object} router - UI-Router instance from useRouter()
 */
export function refreshReactPage(router) {
  if (!router) {
    console.error('refreshReactPage: router is required');
    return;
  }

  const currentHash = window.location.hash;
  const colonIndex = currentHash.indexOf(':');

  // If URL has parameters after ':', re-encode them and reload via router
  if (colonIndex > 0) {
    const currentState = router.stateService.current;
    const currentParams = router.stateService.params;
    const param = currentHash.substring(colonIndex + 1);
    const encodedParam = encodeURIComponent(decodeURIComponent(param));

    // Find the parameter name from current route params (e.g., itemId, keyword, etc.)
    const paramName = Object.keys(currentParams).find(key => currentParams[key] && key !== '#');

    if (paramName) {
      router.stateService.go(currentState.name, {
        [paramName]: `:${encodedParam}`
      }, {
        reload: true,
        inherit: false,
        location: 'replace'
      });
      return;
    }
  }
  router.stateService.reload();
}
