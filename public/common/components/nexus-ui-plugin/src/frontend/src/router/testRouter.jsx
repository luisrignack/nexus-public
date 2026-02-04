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

import {UIView} from "@uirouter/react";
import {createRouter} from "./createRouter";
import React from "react";

export function getTestRouter() {
  const initialRoute = 'browse.welcome';
  const menuRoutes = [
    {
      name: 'login',
      url: '/login',
      component: () => <div>login page</div>,
      data: { visibilityRequirements: {} }
    },
    {
      name: 'browse',
      url: 'browse',
      component: UIView,
      data: {
        visibilityRequirements: {
          ignoreForMenuVisibilityCheck: true
        },
        title: 'Browse'
      }
    },
    {
      name: 'browse.welcome',
      url: '/welcome',
      component: () => <div>Welcome</div>,
      data: {
        visibilityRequirements: {},
        title: 'Dashboard'
      }
    },
    {
      name: 'browse.search',
      url: '/search',
      component: UIView,
      abstract: true,
      data: {
        // make sure we don't inherit from BROWSE
        visibilityRequirements: {},
        title: 'Search'
      }
    },
    {
      name: 'browse.search.generic',
      url: '/generic:keyword',
      component: () => <div>Generic Search</div>,
      params: {
        keyword: {
          value: null,
          raw: true,
          dynamic: true
        }
      },
      data: {
        visibilityRequirements: {
          permissions: ['nexus:search:read']
        },
        title: 'Search'
      }
    }
  ];
  const missingRoute = {
    name: 'missing.route',
    url: 404,
    component: () => <div>Missing Route</div>,
    data: {
      visibilityRequirements: {}
    }
  };

  return createRouter({ initialRoute, menuRoutes, missingRoute });
}
