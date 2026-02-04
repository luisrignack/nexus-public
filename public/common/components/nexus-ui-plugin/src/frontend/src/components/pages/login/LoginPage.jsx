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
import React, { useState } from 'react';
import { ExtJS } from "@sonatype/nexus-ui-plugin";
import { NxTile, NxErrorAlert, NxH2 } from "@sonatype/react-shared-components";
import UIStrings from "../../../constants/UIStrings";
import LoginLayout from "../../layout/LoginLayout";
import AnonymousAccess from "./AnonymousAccess";
import InitialPasswordInfo from "./InitialPasswordInfo";
import LocalLogin from "./LocalLogin";
import SsoLogin from "./SsoLogin";

const { LOGIN_TITLE, LOGIN_SUBTITLE, SSO_DIVIDER_LABEL } = UIStrings;

import "./LoginPage.scss";

const localAuthenticationRealms = [
  "ldapRealmEnabled",
  "userTokenRealmEnabled",
  "localAuthRealmEnabled",
  "crowdRealmEnabled",
];
const ssoAuthenticationRealms = ["samlEnabled", "oauth2Enabled"];

/**
 * Login page component that renders within LoginLayout.
 * Displays a welcome message and login form matching the design specification.
 * @param {Object} logoConfig - Logo configuration passed to LoginLayout
 */
export default function LoginPage({ logoConfig }) {
  const [generalError, setGeneralError] = useState(null);

  const isCloudEnvironment = ExtJS.state().getValue("isCloud", false);
  const showContinueWithoutLogin =
    !!ExtJS.state().getValue("anonymousUsername");

  const showSSOLogin = ssoAuthenticationRealms.some((realm) =>
    ExtJS.state().getValue(realm, false)
  );

  const showLocalLogin =
    !isCloudEnvironment &&
    localAuthenticationRealms.some((realm) =>
      ExtJS.state().getValue(realm, false)
    );
  const adminPasswordFilePath = ExtJS.state().getValue("admin.password.file");
  const onboardingRequired = ExtJS.state().getValue("onboarding.required");

  const showInitialPasswordPathInfo =
    !!adminPasswordFilePath && !isCloudEnvironment && onboardingRequired;

  return (
    <LoginLayout logoConfig={logoConfig}>
      {generalError && (
        <NxErrorAlert onClose={() => setGeneralError(null)}>
          {generalError}
        </NxErrorAlert>
      )}
      <div className="login-page">
        <NxTile className="login-tile" data-testid="login-tile">
          <NxTile.Header>
            <NxTile.HeaderTitle>
              <NxH2>
                {LOGIN_TITLE}
              </NxH2>
            </NxTile.HeaderTitle>
            <NxTile.HeaderSubtitle>{LOGIN_SUBTITLE}</NxTile.HeaderSubtitle>
          </NxTile.Header>
          <NxTile.Content>
            <div className="login-content">
              {showInitialPasswordPathInfo && (
                <InitialPasswordInfo passwordFilePath={adminPasswordFilePath} />
              )}
              {showSSOLogin && (
                <>
                  <SsoLogin />
                  {showLocalLogin && (
                    <div className="login-divider" aria-hidden="true">
                      <span>{SSO_DIVIDER_LABEL}</span>
                    </div>
                  )}
                </>
              )}
              {showLocalLogin && <LocalLogin primaryButton={!showSSOLogin} onError={setGeneralError} />}
              {showContinueWithoutLogin && <AnonymousAccess />}
            </div>
          </NxTile.Content>
        </NxTile>
      </div>
    </LoginLayout>
  );
}
