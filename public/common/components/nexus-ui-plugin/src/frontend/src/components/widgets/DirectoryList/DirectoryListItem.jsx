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
import { useSref, useRouter } from '@uirouter/react';
import { NxList } from '@sonatype/react-shared-components';
import { useIsVisible } from '../../../interface/NavigationUtils';
import classNames from 'classnames';

export default function DirectoryListItem({ text, description, routeName, params, className, ...props }) {
  const routerState = useRouter().stateRegistry.get(routeName);
  const isVisible = useIsVisible(routerState.data.visibilityRequirements);
  const { href } = useSref(routeName, params);

  if (!isVisible) {
    return null;
  }

  return (
    <NxList.LinkItem href={href} className={classNames('nxrm-directory-list-item', className)} {...props}>
      <NxList.Text>{text}</NxList.Text>
      <NxList.Subtext>{description}</NxList.Subtext>
    </NxList.LinkItem>
  );
}
