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
import { useIsActive, useRouter, useSref } from '@uirouter/react';
import { useIsVisible } from '../interface/NavigationUtils';

/**
 * @param name - route sate name
 * @param selectedState - optional - the state to use as the selected state, if different from the name parameter
 * @param params - optional - url parameters
 */
export function useNavigationLinkState(name, selectedState, params) {
  const router = useRouter();
  const state = router.stateRegistry.get(name);

  // If the route doesn't exist, hide the menu item and log an error
  if (!state) {
    console.error(`LeftNavigationMenuItem -> route "${name}" does not exist. Menu item will be hidden.`);
    return { isVisible: false, href: null, isSelected: false, icon: null };
  }

  const data = state?.data || {};
  const { text, icon, visibilityRequirements } = data;

  const isSelected = useIsActive(selectedState || name);

  const { href } = useSref(name, params);

  const isVisible = useIsVisible(visibilityRequirements);

  return { isVisible, href, isSelected, text, icon };
}
