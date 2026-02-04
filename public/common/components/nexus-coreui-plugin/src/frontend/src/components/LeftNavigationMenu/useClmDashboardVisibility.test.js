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

import { renderHook } from '@testing-library/react';
import { useClmDashboardVisibility } from './useClmDashboardVisibility';

// Mock the ExtJS dependencies
jest.mock('@sonatype/nexus-ui-plugin', () => ({
  ExtJS: {
    state: jest.fn(),
    useUser: jest.fn()
  }
}));

import { ExtJS } from '@sonatype/nexus-ui-plugin';

describe('useClmDashboardVisibility', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('should return showDashboard: true when all conditions are met', () => {
    ExtJS.useUser.mockReturnValue({ id: 'testuser' });
    ExtJS.state.mockReturnValue({
      getValue: jest.fn().mockReturnValue({
        enabled: true,
        showLink: true,
        url: 'http://iq-server.example.com'
      })
    });

    const { result } = renderHook(() => useClmDashboardVisibility());

    expect(result.current).toEqual({
      showDashboard: true,
      url: 'http://iq-server.example.com'
    });
  });

  it('should return showDashboard: false when user is not authenticated', () => {
    ExtJS.useUser.mockReturnValue(null);
    ExtJS.state.mockReturnValue({
      getValue: jest.fn().mockReturnValue({
        enabled: true,
        showLink: true,
        url: 'http://iq-server.example.com'
      })
    });

    const { result } = renderHook(() => useClmDashboardVisibility());

    expect(result.current).toEqual({
      showDashboard: false,
      url: 'http://iq-server.example.com'
    });
  });

  it('should return showDashboard: false when CLM is not enabled', () => {
    ExtJS.useUser.mockReturnValue({ id: 'testuser' });
    ExtJS.state.mockReturnValue({
      getValue: jest.fn().mockReturnValue({
        enabled: false,
        showLink: true,
        url: 'http://iq-server.example.com'
      })
    });

    const { result } = renderHook(() => useClmDashboardVisibility());

    expect(result.current).toEqual({
      showDashboard: false,
      url: 'http://iq-server.example.com'
    });
  });

  it('should return showDashboard: false when showLink is false', () => {
    ExtJS.useUser.mockReturnValue({ id: 'testuser' });
    ExtJS.state.mockReturnValue({
      getValue: jest.fn().mockReturnValue({
        enabled: true,
        showLink: false,
        url: 'http://iq-server.example.com'
      })
    });

    const { result } = renderHook(() => useClmDashboardVisibility());

    expect(result.current).toEqual({
      showDashboard: false,
      url: 'http://iq-server.example.com'
    });
  });

  it('should return showDashboard: false when url is falsy', () => {
    ExtJS.useUser.mockReturnValue({ id: 'testuser' });
    
    // Test multiple falsy url values
    const falsyUrls = [null, undefined, ''];
    
    falsyUrls.forEach(url => {
      ExtJS.state.mockReturnValue({
        getValue: jest.fn().mockReturnValue({
          enabled: true,
          showLink: true,
          url
        })
      });

      const { result } = renderHook(() => useClmDashboardVisibility());

      expect(result.current).toEqual({
        showDashboard: false,
        url
      });
    });
  });

  it('should handle null or undefined CLM config gracefully', () => {
    ExtJS.useUser.mockReturnValue({ id: 'testuser' });
    
    [null, undefined].forEach(clmConfig => {
      ExtJS.state.mockReturnValue({
        getValue: jest.fn().mockReturnValue(clmConfig)
      });

      const { result } = renderHook(() => useClmDashboardVisibility());

      expect(result.current).toEqual({
        showDashboard: false,
        url: undefined
      });
    });
  });

  it('should return URL even when showDashboard is false', () => {
    ExtJS.useUser.mockReturnValue(null); // Not authenticated
    ExtJS.state.mockReturnValue({
      getValue: jest.fn().mockReturnValue({
        enabled: true,
        showLink: true,
        url: 'http://iq-server.example.com'
      })
    });

    const { result } = renderHook(() => useClmDashboardVisibility());

    expect(result.current.showDashboard).toBe(false);
    expect(result.current.url).toBe('http://iq-server.example.com');
  });

  it('should handle boolean edge cases for CLM fields', () => {
    ExtJS.useUser.mockReturnValue({ id: 'testuser' });
    
    // Test falsy values that should result in showDashboard: false
    const falsyValues = [0, false];
    
    falsyValues.forEach(value => {
      ExtJS.state.mockReturnValue({
        getValue: jest.fn().mockReturnValue({
          enabled: value,
          showLink: true,
          url: 'http://iq-server.example.com'
        })
      });

      const { result } = renderHook(() => useClmDashboardVisibility());

      expect(result.current.showDashboard).toBe(false);
    });
  });
});
