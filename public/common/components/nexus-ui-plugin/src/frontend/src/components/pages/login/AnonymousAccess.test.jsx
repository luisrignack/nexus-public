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
import { render, screen, fireEvent } from '@testing-library/react';
import AnonymousAccess from './AnonymousAccess';
import UIStrings from '../../../constants/UIStrings';

const mockRouter = {
  globals: { params: {} },
  urlService: { url: jest.fn() },
  stateService: { go: jest.fn() }
};

jest.mock('@uirouter/react', () => ({
  useRouter: () => mockRouter
}));


describe('AnonymousAccess', () => {
  beforeEach(() => {
    mockRouter.globals.params = {};
    mockRouter.urlService.url.mockReset();
    mockRouter.stateService.go.mockReset();

    jest.clearAllMocks();
  });

  describe('rendering', () => {
    it('renders the button with correct text', () => {
      render(<AnonymousAccess />);

      expect(screen.getByText(UIStrings.CONTINUE_WITHOUT_LOGIN)).toBeInTheDocument();
    });

    it('renders a divider line', () => {
      const { container } = render(<AnonymousAccess />);

      const divider = container.querySelector('hr.nx-divider');
      expect(divider).toBeInTheDocument();
    });

    it('renders an arrow icon', () => {
      const { container } = render(<AnonymousAccess />);

      const icon = container.querySelector('svg');
      expect(icon).toBeInTheDocument();
    });
  });

  describe('button properties', () => {
    it('has correct attributes', () => {
      render(<AnonymousAccess />);
      const button = screen.getByTestId('continue-without-login-button');

      expect(button).toHaveAttribute('data-analytics-id', 'nxrm-login-anonymous');
      expect(button).toHaveClass('continue-without-login-button');
    });

    it('has secondary variant', () => {
      render(<AnonymousAccess />);
      const button = screen.getByTestId('continue-without-login-button');

      // NxButton with variant="secondary" adds specific classes
      expect(button).toHaveClass('nx-btn--secondary');
    });
  });

  describe('button interaction', () => {
    it('navigates to browse.welcome when clicked and no returnTo param', () => {
      render(<AnonymousAccess />);
      const button = screen.getByTestId('continue-without-login-button');

      fireEvent.click(button);

      expect(mockRouter.stateService.go).toHaveBeenCalledWith('browse.welcome');
      expect(mockRouter.urlService.url).not.toHaveBeenCalled();
    });

    it('navigates to returnTo url when clicked and returnTo param exists', () => {
      const returnToUrl = '/some/path';
      mockRouter.globals.params.returnTo = btoa(returnToUrl);
      render(<AnonymousAccess />);
      const button = screen.getByTestId('continue-without-login-button');

      fireEvent.click(button);

      expect(mockRouter.urlService.url).toHaveBeenCalledWith(returnToUrl);
      expect(mockRouter.stateService.go).not.toHaveBeenCalled();
    });

    it('navigates to missing route when returnTo decoding fails', () => {
      mockRouter.globals.params.returnTo = 'invalid-base64';
      render(<AnonymousAccess />);
      const button = screen.getByTestId('continue-without-login-button');

      fireEvent.click(button);

      expect(mockRouter.stateService.go).toHaveBeenCalledWith('missing_route');
      expect(mockRouter.urlService.url).not.toHaveBeenCalled();
    });

    it('falls back to missing route when urlService navigation throws', () => {
      mockRouter.globals.params.returnTo = btoa('/some/path');
      mockRouter.urlService.url.mockImplementation(() => {
        throw new Error('navigation failed');
      });

      render(<AnonymousAccess />);
      const button = screen.getByTestId('continue-without-login-button');

      fireEvent.click(button);

      expect(mockRouter.stateService.go).toHaveBeenCalledWith('missing_route');
      expect(mockRouter.urlService.url).toHaveBeenCalled();
    });
  });

  describe('accessibility', () => {
    it('is keyboard accessible', () => {
      render(<AnonymousAccess />);
      const button = screen.getByTestId('continue-without-login-button');

      expect(button).not.toHaveAttribute('disabled');
      expect(button.tagName).toBe('BUTTON');
    });

    it('can be focused with keyboard', () => {
      render(<AnonymousAccess />);
      const button = screen.getByTestId('continue-without-login-button');

      button.focus();
      expect(button).toHaveFocus();
    });
  });

  describe('analytics', () => {
    it('has analytics tracking attribute', () => {
      render(<AnonymousAccess />);
      const button = screen.getByTestId('continue-without-login-button');

      expect(button).toHaveAttribute('data-analytics-id', 'nxrm-login-anonymous');
    });
  });

  describe('visual structure', () => {
    it('renders divider before button', () => {
      const { container } = render(<AnonymousAccess />);

      const divider = container.querySelector('hr.nx-divider');
      const button = container.querySelector('button');

      expect(divider).toBeInTheDocument();
      expect(button).toBeInTheDocument();

      // Verify divider appears before button in DOM
      expect(divider.compareDocumentPosition(button) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    });

    it('contains button text and icon', () => {
      const { container } = render(<AnonymousAccess />);

      expect(screen.getByText(UIStrings.CONTINUE_WITHOUT_LOGIN)).toBeInTheDocument();
      expect(container.querySelector('svg')).toBeInTheDocument();
    });
  });
});
