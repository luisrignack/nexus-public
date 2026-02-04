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
import { UIRouter } from '@uirouter/react';
import { getTestRouter } from '././../../../router/testRouter';
import { LeftNavigationMenuCollapsibleItem } from './LeftNavigationMenuCollapsibleItem';
import { faLink } from '@fortawesome/free-solid-svg-icons';

// This is used by the API view, it's not really something we need to
// test here, but importing it trips up jest, it's simplest to just bypass it
// with a mock
jest.mock('swagger-ui-react', () => {
  return jest.fn().mockReturnValue(null);
});

describe('LeftNavigationMenuCollapsibleItem', () => {
  const Application = global.NX.app.Application;
  const Permissions = global.NX.Permissions;
  let router;

  beforeEach(() => {
    router = getTestRouter();
    givenActiveBundles();
    givenNoPermissions();
  });

  it('renders link using default optional props', () => {
    renderComponent({ name: 'browse.welcome', text: 'Dashboard', icon: { faLink } });
    const allLinks = screen.getAllByRole('link');
    expect(allLinks.length).toEqual(1);

    assertLinkVisible('Dashboard', 'http://localhost/#browse/welcome');
  });

  it('renders link as selected when current router state is the same as provided name prop', async () => {
    await router.stateService.go('browse.welcome');
    renderComponent({ name: 'browse.welcome', text: 'Dashboard', icon: { faLink } });
    assertLinkVisible('Dashboard', 'http://localhost/#browse/welcome', true);
  });

  it('renders link as selected when router state is not the same but matches selectedState prop', async () => {
    givenPermissions({ 'nexus:search:read': true });
    await router.stateService.go('browse.search.generic');
    renderComponent({ name: 'browse.welcome', selectedState: 'browse', text: 'Dashboard', icon: { faLink } });
    assertLinkVisible('Dashboard', 'http://localhost/#browse/welcome', true);
  });

  it('renders link as not selected when router state does not match and selectedState prop is not provided', async () => {
    givenPermissions({ 'nexus:search:read': true });
    await router.stateService.go('browse.search.generic');
    renderComponent({ name: 'browse.welcome', text: 'Dashboard', icon: { faLink } });
    assertLinkVisible('Dashboard', 'http://localhost/#browse/welcome');
  });

  it('renders link as selected with provided router params', async () => {
    givenPermissions({ 'nexus:search:read': true });
    await router.stateService.go('browse.search.generic');
    renderComponent({
      name: 'browse.search.generic',
      params: { keyword: ':foo' },
      text: 'Search',
      icon: { faLink }
    });
    assertLinkVisible('Search', 'http://localhost/#browse/search/generic:foo', true);
  });

  it('does not render link if visibility requirements are not met', () => {
    renderComponent({ name: 'browse.search.generic' });
    assertLinkNotVisible();
  });

  function renderComponent(props) {
    return render(
      <UIRouter router={router}>
        <LeftNavigationMenuCollapsibleItem {...props} />
      </UIRouter>
    );
  }

  function givenActiveBundles(activeBundleState = getDefaultActiveBundleState()) {
    Application.bundleActive.mockImplementation((key) => activeBundleState[key] ?? false);
  }

  function getDefaultActiveBundleState() {
    return {
      'nexus-coreui-plugin': true,
    };
  }

  function givenNoPermissions() {
    givenPermissions({});
  }

  function givenPermissions(permissionLookup) {
    Permissions.check.mockImplementation((key) => {
      return permissionLookup[key] ?? false;
    });
  }

  function assertLinkVisible(name, path, isSelected = false) {
    const allLinks = screen.getAllByRole('link');
    expect(allLinks.length).toEqual(1);

    const link = screen.getByRole('link', { name });
    expect(link).toBeVisible();
    expect(link.href).toBe(path);
    if (isSelected) {
      expect(link).toHaveClass('selected');
    } else {
      expect(link).not.toHaveClass('selected');
    }

    return link;
  }

  function assertLinkNotVisible() {
    const link = screen.queryByRole('link');
    expect(link).not.toBeInTheDocument();
  }
});
