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
import {UIRouterReact, hashLocationPlugin, servicesPlugin} from '@uirouter/react';
import ExtJS from '../interface/ExtJS';
import {isVisible} from '../interface/NavigationUtils';
import {showUnsavedChangesModal} from './unsavedChangesDialog';
import {RouteNames} from "../constants/RouteNames";

/*
  This is where we register all of the client side routes. Routing is handled using
  UIRouter. For more info see https://github.com/ui-router/react.

  See also private/developer-documentation/frontend/client-side-routing.md
 */
export function createRouter({initialRoute, menuRoutes, missingRoute}) {
  const router = new UIRouterReact();
  router.plugin(servicesPlugin);
  router.plugin(hashLocationPlugin);
  router.urlService.rules.initial({state: initialRoute});

  // validate permissions and configuration on each route request
  router.transitionService.onBefore({}, async (transition) => {
    const redirectTo404 = () => {
      transition.abort();
      router.stateService.go(missingRoute.name);
    };

    const stateTo = transition.to();
    const stateFrom = transition.from();
    console.debug(`evaluating transition from ${stateFrom.name} to ${stateTo.name}`);

    if (stateTo.name === RouteNames.LOGIN && ExtJS.hasUser()) {
      console.debug('User is already authenticated, redirecting away from login page');
      transition.abort();
      router.stateService.go(RouteNames.WELCOME);
      return;
    }

    if (!isVisible(stateTo.data?.visibilityRequirements)) {
      if (!ExtJS.hasUser()) {
        transition.abort();
        const isAnonymousAccessEnabled = !!ExtJS.state().getValue('anonymousUsername');
        if (isAnonymousAccessEnabled && stateFrom.name === RouteNames.LOGIN) {
          console.warn('state is not visible for navigation after login, redirecting to 404');
          redirectTo404();
          return;
        }

        console.debug('Redirecting to login page with return URL');
        // Keep original requested URL and then encode to Base64
        const url = router.urlService.url();
        const returnTo = btoa(`#${url}`);
        router.stateService.go(RouteNames.LOGIN, {returnTo});
      } else {
        console.warn('state is not visible for navigation, aborting transition', stateTo.name);
        redirectTo404();
      }
    }
  });

  // show the unsaved changes dialog when navigating away from a page with unsaved changes
  router.transitionService.onBefore(
      {},
      async () => {
        const hasUnsavedChanges = window.dirty && window.dirty.length > 0;
        if (hasUnsavedChanges) {
          const confirm = await showUnsavedChangesModal();
          if (!confirm) {
            return false;
          }
          window.dirty = [];
        }

        return true;
      },
      {priority: 1000}
  ); // this hook given a high priority to ensure it runs first

  // validate permissions and configuration on each route request
  router.transitionService.onSuccess({}, async (transition) => {
    const customTitle = ExtJS.state().getValue('uiSettings')?.title || 'Nexus Repository Manager';
    const {title: currentPageTitle} = transition.to().data;
    const title = currentPageTitle ? `${currentPageTitle} - ${customTitle}` : customTitle;
    document.title = title;
  });

  menuRoutes.forEach((route) => router.stateRegistry.register(route));

  // explicitely register the missing route
  router.stateRegistry.register(missingRoute);
  // send any unrecognized routes to the 404 page
  router.urlService.rules.otherwise((matchValue, urlParts, router) => {
    console.warn('url not recognized', matchValue, urlParts);
    router.stateService.go(missingRoute.name, {}, {location: false});
  });

  return router;
}
