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

import { isVisible } from '@sonatype/nexus-ui-plugin';
import useFilteredRoutes from "../../hooks/useFilteredRoutes";
import { ROUTE_NAMES } from "../../routerConfig/routeNames/routeNames";

export function useDefaultAdminRouteName() {
  const { ADMIN } = ROUTE_NAMES;

  function isRouteVisible(section) {
    const visibleRoutes = useFilteredRoutes(
      (state) =>
        state.name.startsWith(`${section}.`) &&
        !state?.data?.visibilityRequirements?.ignoreForMenuVisibilityCheck &&
        isVisible(state.data.visibilityRequirements)
    );

    return visibleRoutes.length > 0;
  }

  const repoVisible = isRouteVisible(ADMIN.REPOSITORY.DIRECTORY);
  const securityVisible = isRouteVisible(ADMIN.SECURITY.DIRECTORY);
  const systemVisible = isRouteVisible(ADMIN.SYSTEM.DIRECTORY);
  const supportVisible = isRouteVisible(ADMIN.SUPPORT.DIRECTORY);
  const iqVisible = isRouteVisible(ADMIN.IQ.ROOT);

  if (repoVisible) {
    return ADMIN.REPOSITORY.DIRECTORY;
  }
  if (securityVisible) {
    return ADMIN.SECURITY.DIRECTORY;
  }
  if (systemVisible) {
    return ADMIN.SYSTEM.DIRECTORY;
  }
  if (supportVisible) {
    return ADMIN.SUPPORT.DIRECTORY;
  }
  if (iqVisible) {
    return ADMIN.IQ.ROOT;
  }

  return null;
}

