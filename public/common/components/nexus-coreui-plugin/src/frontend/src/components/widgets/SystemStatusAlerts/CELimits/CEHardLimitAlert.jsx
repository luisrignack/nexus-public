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
import {useMachine} from '@xstate/react';
import PropTypes from "prop-types";

import {ExtJS} from '@sonatype/nexus-ui-plugin';
import {
  NxTextLink,
} from '@sonatype/react-shared-components';
import CEHardLimitBannersMachine from './CEHardLimitBannersMachine';

import {helperFunctions} from './UsageHelper';

const {
  useGracePeriodEndDate,
  useViewPurchaseALicenseUrl,
  useThrottlingStatus,
  useDaysUntilGracePeriodEnds
} = helperFunctions;

import UIStrings from '../../../../constants/UIStrings';
import SystemNotice from '../SystemNotice';

const {
  WELCOME: {
    USAGE: {
      HEADER,
      BANNERS
    }
  }
} = UIStrings;

export default function CEHardLimitAlert() {
  const [state, send] = useMachine(CEHardLimitBannersMachine, {
    devTools: true
  });

  const user = ExtJS.useUser();
  const isAdmin = user?.administrator;
  const isHa = ExtJS.state().getValue('nexus.datastore.clustered.enabled');
  const isCommunityEdition = ExtJS.state().getEdition() === 'COMMUNITY';

  const gracePeriodEndDate = useGracePeriodEndDate();
  const throttlingStatus = useThrottlingStatus();
  const daysUntilGracePeriodEnds = useDaysUntilGracePeriodEnds();

  const {isUnderEndGraceDismissed} = state.context;

  if (isHa || !isCommunityEdition) {
    return null;
  }

  if (isAdmin) {
    return <>
      {throttlingStatus === 'NEAR_LIMITS_NEVER_IN_GRACE' &&
          <SystemNotice noticeLevel="warning" additionalAlertClassNames="ce-banner-near-limit-never-in-grace-period">
            {BANNERS.NEAR_LIMITS} <ContactLinks scrollToUsageCenter={scrollToUsageCenter}/>
          </SystemNotice>
      }
      {throttlingStatus === 'OVER_LIMITS_IN_GRACE' &&
          <SystemNotice
              title={BANNERS.OVER_LIMIT_IN_GRACE_TITLE(daysUntilGracePeriodEnds)}
              noticeLevel="error"
              className="ce-banner-over-limit-in-grace-period"
          >
              {BANNERS.OVER_LIMIT_IN_GRACE(gracePeriodEndDate)}{" "}
              <ContactLinks scrollToUsageCenter={scrollToUsageCenter}/>
          </SystemNotice>
      }
      {throttlingStatus === 'OVER_LIMITS_GRACE_PERIOD_ENDED' &&
          <SystemNotice
              title={HEADER.OVER_LIMITS.TITLE}
              noticeLevel="error"
              additionalAlertClassNames="ce-banner-over-limit-grace-period-ended"
          >
            {BANNERS.OVER_LIMIT_END_GRACE} <ContactLinks scrollToUsageCenter={scrollToUsageCenter}/>
          </SystemNotice>
      }
      {throttlingStatus === 'BELOW_LIMITS_GRACE_PERIOD_ENDED' && !isUnderEndGraceDismissed &&
          <SystemNotice
              noticeLevel="warning"
              additionalAlertClassNames="ce-banner-under-limit-grace-period-ended"
              onClose={() => dismissBelowLimitOutofGrace()}
          >
            {BANNERS.BELOW_LIMIT_END_GRACE} <ContactLinks scrollToUsageCenter={scrollToUsageCenter}/>
          </SystemNotice>
      }
    </>;
  } else if (!isAdmin) {
    return <>
      {throttlingStatus === 'NON_ADMIN_OVER_LIMITS_GRACE_PERIOD_ENDED' &&
          <SystemNotice additionalAlertClassNames="ce-banner-over-limit-non-admin" noticeLevel="error" >
            {BANNERS.THROTTLING_NON_ADMIN} <NonAdminContactLink />.
          </SystemNotice>
      }
      {throttlingStatus === 'NEAR_LIMITS_NON_ADMIN' &&
          <SystemNotice additionalAlertClassNames="ce-banner-nearing-limit-non-admin" noticeLevel="warning" >
            {BANNERS.NEARING_NON_ADMIN} <NonAdminContactLink />.
          </SystemNotice>
      }
    </>;
  } else {
    return null;
  }

  function scrollToUsageCenter() {
    const targetPath = '#browse/welcome';
    const targetElementId = 'nxrm-usage-center';

    function scrollToElement() {
      const usageCenterElement = document.getElementById(targetElementId);
      if (usageCenterElement) {
        usageCenterElement.scrollIntoView({ behavior: 'smooth' });
      }
    }

    if (window.location.hash !== targetPath) {
      window.location.hash = targetPath;
      setTimeout(scrollToElement, 200);
    } else {
      scrollToElement();
    }
  }

  function dismissBelowLimitOutofGrace() {
    send({type: 'DISMISS'});
  }
}


CEHardLimitAlert.propTypes = {
  onClose: PropTypes.func
};

function ContactLinks({ scrollToUsageCenter }) {
  return <>
    <button
        data-analytics-id="nxrm-ce-hard-limit-banner-scroll-to-usage"
        className="nx-text-link review-usage-link" onClick={scrollToUsageCenter}
    >
      Review your usage
    </button> or{" "}
    <NxTextLink
      external
      data-analytics-id="nxrm-ce-hard-limit-banner-usage-view-pricing-link"
      className="usage-view-pricing-link" href={useViewPurchaseALicenseUrl()}
    >
      purchase a license to remove limits.
    </NxTextLink>
  </>
}

function NonAdminContactLink() {
  const params = {
    utm_medium: 'product',
    utm_source: 'nexus_repo_community',
    utm_campaign: 'repo_community_usage'
  };
  const learnLink =
      `http://links.sonatype.com/products/nxrm3/learn-about-community-edition?${new URLSearchParams(params).toString()}`;

  return <>
    <NxTextLink
        external
        data-analytics-id="nxrm-ce-hard-limit-banner-learn-more-link"
        className="ce-learn-more-link"
        href={learnLink}
    >
      Learn about Nexus Repository Community Edition
    </NxTextLink>
  </>
}
