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
import { render, screen } from '@testing-library/react';
import CoreUILoginPageWrapper from './CoreUILoginPageWrapper';

jest.mock('@sonatype/nexus-ui-plugin', () => ({
  LoginPage: ({ logoConfig }) => (
    <div data-testid="login-page" data-logo-config={JSON.stringify(logoConfig)}>
      LoginPage
    </div>
  )
}));

describe('CoreUILoginPageWrapper', () => {
  it('renders LoginPage with all edition logos', () => {
    render(<CoreUILoginPageWrapper />);

    const loginPage = screen.getByTestId('login-page');
    expect(loginPage).toBeInTheDocument();

    const logoConfig = JSON.parse(loginPage.getAttribute('data-logo-config'));
    expect(logoConfig).toEqual({
      ceDark: 'path/to/asset.png',
      ceLight: 'path/to/asset.png',
      coreDark: 'path/to/asset.png',
      coreLight: 'path/to/asset.png',
      proDark: 'path/to/asset.png',
      proLight: 'path/to/asset.png'
    });
  });
});
