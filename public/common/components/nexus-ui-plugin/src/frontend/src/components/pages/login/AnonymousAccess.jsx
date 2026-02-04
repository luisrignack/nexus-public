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
import { NxButton, NxFontAwesomeIcon } from '@sonatype/react-shared-components';
import { faArrowRight } from '@fortawesome/free-solid-svg-icons';
import { useRouter } from '@uirouter/react';
import UIStrings from '../../../constants/UIStrings';
import './AnonymousAccess.scss';
import { RouteNames } from "../../../constants/RouteNames";

const { CONTINUE_WITHOUT_LOGIN } = UIStrings;

/**
 * Button component that allows users to continue without logging in when anonymous access is enabled.
 */
export default function AnonymousAccess() {
  const router = useRouter();

  const handleContinueWithoutLogin = () => {
    try {
      const returnTo = router.globals.params.returnTo;
      if (returnTo) {
       const originalReturnTo = atob(returnTo);
        // `router.urlService.url` does set and navigate to the returnTo url
        router.urlService.url(originalReturnTo);
      } else {
        router.stateService.go('browse.welcome');
      }
    } catch (ex) {
      console.warn('redirection unsuccessful: ', ex);
      router.stateService.go(RouteNames.MISSING_ROUTE);
    }
  };

  return (
    <>
      <hr className="nx-divider" />
      <NxButton
        variant="secondary"
        className="continue-without-login-button"
        onClick={handleContinueWithoutLogin}
        data-testid="continue-without-login-button"
        data-analytics-id="nxrm-login-anonymous"
      >
        <span>{CONTINUE_WITHOUT_LOGIN}</span>
        <NxFontAwesomeIcon icon={faArrowRight} />
      </NxButton>
    </>
  );
}


