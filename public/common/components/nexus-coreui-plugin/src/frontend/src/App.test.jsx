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
import { act, render, screen, waitFor, within } from '@testing-library/react';
import { UIRouter } from '@uirouter/react';
import { App } from './App';
import { getRouter } from './routerConfig/routerConfig';
import { ExtJS } from '@sonatype/nexus-ui-plugin';
import { helperFunctions } from './components/widgets/SystemStatusAlerts/CELimits/UsageHelper';
import { ROUTE_NAMES } from './routerConfig/routeNames/routeNames';

const { BROWSE } = ROUTE_NAMES;

// mocking out the Welcome page to avoid having to mock all the various ExtJs functions/state required to render it
jest.mock('./components/pages/user/Welcome/Welcome', () => {
  return () => (<main><h1>Welcome Test Mock</h1></main>);
});

jest.mock('./components/login/CoreUILoginPageWrapper', () => {
  return () => (<main><h1>Login Test Mock</h1></main>);
});

describe('App', () => {
  describe('login layout', () => {
    beforeEach(() => {
      givenExtJSState();
    });

    it('should render login layout', async () => {
      const { router } = await renderComponent();

      // Wait for initial automatic redirect from login to complete
      await waitFor(() => {
        expect(router.globals.transition).toBeNull();
      });

      await assertLoginLayoutRenders();
    });
  });

  describe('standard layout', () => {
    let historySpy;

    beforeEach(() => {
      givenExtJSState();
      givenUser();
      historySpy = jest.spyOn(History.prototype, 'pushState');

      window.location.hash = '';
      window.dirty = [];
    });

    it('should render standard layout', async () => {
      // logged in user (mocked by givenUser()) if try to go to login page will be redirected to welcome page,
      // which is into the standard layout
      renderComponent();
      await assertStandardLayoutRenders();
    });

    describe('UI Branding', () => {
      it('should not render branding header nor footer when not branding is set', async () => {
        renderComponent();
        const brandingHeader = screen.queryByTestId('nxrm-branding-header');
        const brandingFooter = screen.queryByTestId('nxrm-branding-footer');
        expect(brandingHeader).not.toBeInTheDocument();
        expect(brandingFooter).not.toBeInTheDocument();
      });

      it('should not render branding header and footer when both are disabled', async () => {
        givenExtJSState({
          ...getDefaultState(),
          branding: {
            headerEnabled: false,
            headerHtml: '<div>Branding Header</div>',
            footerEnabled: false,
            footerHtml: '<div>Branding Footer</div>',
          },
        });
        renderComponent();
        const brandingHeader = screen.queryByTestId('nxrm-branding-header');
        const brandingFooter = screen.queryByTestId('nxrm-branding-footer');
        expect(brandingHeader).not.toBeInTheDocument();
        expect(brandingFooter).not.toBeInTheDocument();
      });

      it('should render branding header and footer when enabled', async () => {
        givenExtJSState({
          ...getDefaultState(),
          branding: {
            headerEnabled: true,
            headerHtml: '<div>Branding Header</div>',
            footerEnabled: true,
            footerHtml: '<div>Branding Footer</div>',
          },
        });
        renderComponent();
        const brandingHeader = screen.getByTestId('nxrm-branding-header');
        const brandingFooter = screen.getByTestId('nxrm-branding-footer');
        expect(brandingHeader).toBeVisible();
        expect(within(brandingHeader).getByText('Branding Header')).toBeVisible();
        expect(brandingFooter).toBeVisible();
        expect(within(brandingFooter).getByText('Branding Footer')).toBeVisible();
      });

      it('should render branding header but not footer', async () => {
        givenExtJSState({
          ...getDefaultState(),
          branding: {
            headerEnabled: true,
            headerHtml: '<div>Branding Header</div>',
            footerEnabled: false,
            footerHtml: '<div>Branding Footer</div>',
          },
        });
        renderComponent();
        const brandingHeader = screen.getByTestId('nxrm-branding-header');
        const brandingFooter = screen.queryByTestId('nxrm-branding-footer');
        expect(brandingHeader).toBeVisible();
        expect(within(brandingHeader).getByText('Branding Header')).toBeVisible();
        expect(brandingFooter).not.toBeInTheDocument();
      });

      it('should render branding footer but not header', async () => {
        givenExtJSState({
          ...getDefaultState(),
          branding: {
            headerEnabled: false,
            headerHtml: '<div>Branding Header</div>',
            footerEnabled: true,
            footerHtml: '<div>Branding Footer</div>',
          },
        });
        renderComponent();
        const brandingHeader = screen.queryByTestId('nxrm-branding-header');
        const brandingFooter = screen.getByTestId('nxrm-branding-footer');
        expect(brandingHeader).not.toBeInTheDocument();
        expect(brandingFooter).toBeVisible();
        expect(within(brandingFooter).getByText('Branding Footer')).toBeVisible();
      });
    });

    describe('Community Edition Hard Limit Banner', () => {
      // We will just a simple test here to make sure the banner is rendered in the context of the page
      // Full testing the CEHardLimitAlert logic has its own test suite
      it('should render given a community edition is over the limit and an admin user is logged in', async () => {
        const givenGracePeriodEndDate = givenDateNDaysInTheFuture(20);

        givenUseState({
          [helperFunctions.useThrottlingStatusValue]: 'Over limits',
          [helperFunctions.useGracePeriodEndsDate]: givenGracePeriodEndDate,
          [helperFunctions.useDaysUntilGracePeriodEnds]: 12
        });

        await renderComponent();

        await assertStandardLayoutRenders();
        await assertCommunityEditionLimitMessageShowing(
            '20 Days Remaining',
            'This instance of Nexus Repository Community Edition has exceeded its usage limit.');
      });
    });

    describe('Login Prompting', () => {
      it('shows login given user does not have permissions to view a route and is not authenticated', async () => {
        global.NX.Security.hasUser = jest.fn().mockReturnValue(false);

        const { router } = await renderComponent();

        // Wait for initial automatic redirect from login to complete
        await waitFor(() => {
          expect(router.globals.transition).toBeNull();
        });

        await assertLoginLayoutRenders();

        // the transaction should still fail because even though we resolved the login prompt successfully upon
        // re-checking visiblity we'll find the user still does not have permissions
        let errorOnTransition = null;
        try {
          await router.stateService.go('admin.security.users')
        } catch (ex) {
          errorOnTransition = ex.message
        }

        expect(errorOnTransition).toEqual('The transition has been aborted')
      });

      it('does not show for login given user does not have permissions but is already authenticated', async () => {
        global.NX.Security.hasUser.mockReturnValue(true);

        const { router } = await renderComponent();

        // the transaction should still fail because even though we resolved the login prompt successfully upon
        // re-checking visiblity we'll find the user still does not have permissions
        let errorOnTransition = null;
        try {
          await router.stateService.go('admin.security.users')
        } catch (ex) {
          errorOnTransition = ex.message
        }

        expect(errorOnTransition).toEqual('The transition has been aborted')

        await assertMissingRoutePageRendered();
      });
    });

    it('should direct to 404 when page not found', async () => {
      const { router } = await renderComponent();
      await assertStandardLayoutRenders();

      router.urlService.url('some-page-that-does-not-exist', false);

      await assertMissingRoutePageRendered();
    });

    it("history hash pushState is intercepted and ignored", async () => {
      await renderComponent();

      history.pushState({}, '', '#');
      expect(historySpy).not.toHaveBeenCalled();

      history.pushState({}, '', '');
      expect(historySpy).toHaveBeenCalledWith({}, '', '');
    });

    describe('Unsaved Changes Dialog', () => {
      const selectors = {
        cancelButton: () => screen.queryByRole('button', { name: 'Cancel' }),
        continueButton: () => screen.queryByRole('button', { name: 'Continue' }),
        modalTitle: () => screen.queryByRole('heading', { name: 'Unsaved Changes' }),
        modalContent: () => screen.queryByText('The page may contain unsaved changes; continuing will discard them.')
      }

      it('should render the unsaved changes modal when navigating away from a page with unsaved changes', async () => {
        const { router } = await renderComponent();
        await assertStandardLayoutRenders();

        act(() => {
          window.dirty = ['some unsaved changes'];
          router.stateService.go(BROWSE.BROWSE.ROOT);
        });

        expect(selectors.modalTitle()).toBeVisible();
      });

      it('should not render the unsaved changes modal when navigating away from a page without unsaved changes', async () => {
        const { router } = await renderComponent();
        await assertStandardLayoutRenders();

        act(() => {
          router.stateService.go(BROWSE.BROWSE.ROOT);
        });

        expect(selectors.modalTitle()).not.toBeInTheDocument();
      });

      it('should hide the unsaved changes modal when the cancel button is clicked', async () => {
        const { router } = await renderComponent();
        await assertStandardLayoutRenders();

        act(() => {
          window.dirty = ['some unsaved changes'];
          router.stateService.go(BROWSE.BROWSE.ROOT);
        });

        expect(selectors.modalTitle()).toBeVisible();
        expect(selectors.modalContent()).toBeVisible();
        expect(selectors.cancelButton()).toBeVisible();

        act(() => {
          selectors.cancelButton().click();
        });

        expect(selectors.modalTitle()).not.toBeInTheDocument();
        expect(selectors.modalContent()).not.toBeInTheDocument();
        expect(selectors.cancelButton()).not.toBeInTheDocument();
      });

      it('should hide the unsaved changes modal when the continue button is clicked', async () => {
        const { router } = await renderComponent();
        await assertStandardLayoutRenders();

        act(() => {
          window.dirty = ['some unsaved changes'];
          router.stateService.go(BROWSE.BROWSE.ROOT);
        });

        expect(selectors.modalTitle()).toBeVisible();
        expect(selectors.modalContent()).toBeVisible();
        expect(selectors.continueButton()).toBeVisible();

        act(() => {
          selectors.continueButton().click();
        });

        expect(selectors.modalTitle()).not.toBeInTheDocument();
        expect(selectors.modalContent()).not.toBeInTheDocument();
        expect(selectors.continueButton()).not.toBeInTheDocument();
      });
    });
  });

  async function renderComponent() {
    const router = getRouter();

    const renderResult = render(
        <UIRouter router={router}>
          <App />
        </UIRouter>
    );

    return { renderResult, router }
  }

  async function assertStandardLayoutRenders() {
    await assertRendersPageContents();
    await assertRendersGlobalHeader();
    await assertRendersLeftNav();
  }

  async function assertRendersPageContents() {
    const main = await screen.findByRole('main');
    expect(main).toBeVisible();
    expect(within(main).getByRole('heading', 'Welcome Test Mock')).toBeVisible();
  }

  async function assertRendersGlobalHeader() {
    const banner = await screen.findByRole('banner');
    expect(banner).toBeVisible();
  }

  async function assertRendersLeftNav() {
    const sideNav = await screen.findByRole('navigation', 'global sidebar');
    expect(sideNav).toBeVisible();
  }

  async function assertLoginLayoutRenders() {
    expect(await screen.findByRole('heading', { name: 'Login Test Mock' })).toBeVisible();
    expect(screen.queryByRole('banner')).not.toBeInTheDocument();
    expect(screen.queryByRole('navigation', { name: 'global sidebar' })).not.toBeInTheDocument();
  }

  function assertCommunityEditionLimitMessageShowing(title, message) {
    const alert = screen.getByRole('complementary', { name: 'alert system notice'});
    expect(alert).toBeVisible();
    expect(within(alert).getByRole('heading', { name: title })).toBeVisible();
    expect(within(alert).getByText(message, { exact: false })).toBeVisible()
  }

  function givenExtJSState(values = getDefaultState(), edition = 'COMMUNITY') {
    const getValueMock = jest.fn().mockImplementation((key, defaultValue) => {
      return values[key] || defaultValue;
    });

    jest.spyOn(ExtJS, 'state').mockReturnValue({
      getEdition: jest.fn().mockReturnValue(edition),
      getVersionMajorMinor: jest.fn().mockReturnValue('1.2.3-some-version'),
      getVersion: jest.fn().mockReturnValue('1.2.3-some-full-version'),
      getValue: getValueMock,
      getUser: jest.fn()
    });

    global.NX.State.getValue = getValueMock;
  }

  function givenUseState(values = {}) {
    jest.spyOn(ExtJS, 'useState').mockImplementation((key) => values[key]);
  }

  function getDefaultState() {
    return {
      usertoken: { licenseValid: true },
      dbUpgrade: { currentState: null }
    }
  }

  function givenUser(user = { name: 'admin', administrator: true }) {
    jest.spyOn(ExtJS, 'useUser').mockReturnValue(user);
    global.NX.Security.hasUser = jest.fn().mockReturnValue(true);
  }

  function givenDateNDaysInTheFuture(days) {
    const date = new Date()
    date.setDate(date.getDate() + days);

    return date;
  }

  async function assertMissingRoutePageRendered() {
    expect(await screen.findByRole('heading', { name: '404' })).toBeVisible();
    expect(screen.getByRole('heading', {name: 'RESOURCE NOT FOUND'})).toBeInTheDocument();
  }
});
