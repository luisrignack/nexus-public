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

import { useEffect } from "react";

/*
 * This hook prevents the pushState method from being called with a hash (#) as the URL.
 * it's is to prevent a bug caused by zenscroll combined with the hash before the path in the URL.
 * 
 * Related spike https://sonatype.atlassian.net/browse/NEXUS-46710?focusedCommentId=762520
 */
export default function usePreventPushStateOnHash() {
  useEffect(() => {
    if (!window?.history) {
      return;
    }

    history.pushState = function () {
      console.debug("Intercepting pushState", arguments);
      if (arguments?.[2] !== "#") {
        History.prototype.pushState.apply(history, arguments);
      }
    };

    return () => {
      history.pushState = History.prototype.pushState;
    };
  }, []);
}
