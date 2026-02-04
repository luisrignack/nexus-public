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
import useSideNavbarCollapsedState from './useSideNavbarCollapsedState';
import { act, renderHook } from '@testing-library/react';

// Setup transition capture
let transitionSuccessCallback = null;

jest.mock('@uirouter/react', () => ({
  useTransitionHook: (hookName, criteria, callback) => {
    if (hookName === 'onSuccess') {
      transitionSuccessCallback = callback;
    }
  }
}));

// Create fake transition objects
const createMockTransition = (from, to) => ({
  from: () => ({ name: from }),
  to: () => ({ name: to })
});

describe('useSideNavbarCollapsedState', () => {
  beforeEach(() => {
    transitionSuccessCallback = null;
  });

  const setup = (initialOpen = true) => {
    return renderHook(() => useSideNavbarCollapsedState(initialOpen));
  };

  const fireTransition = (from, to) => {
    act(() => {
      transitionSuccessCallback(createMockTransition(from, to));
    });
  };

  it('should initialize correctly', () => {
    const { result } = setup(true);
    const [isOpen] = result.current;
    expect(isOpen).toBe(true);
  });

  it('should toggle open state with onToggleClick', () => {
    const { result } = setup(true);
    const [, onToggleClick] = result.current;

    act(() => {
      onToggleClick();
    });

    const [isOpen] = result.current;
    expect(isOpen).toBe(false);
  });

  it('should collapse when navigating into admin', () => {
    const { result } = setup(true);

    fireTransition('home', 'admin.directory.users');

    const [isOpen] = result.current;
    expect(isOpen).toBe(false);
  });

  it('should restore saved open state when navigating out of admin', () => {
    const { result } = setup(true);

    fireTransition('home', 'admin.directory.users'); // into admin

    let [isOpen] = result.current;
    expect(isOpen).toBe(false);

    fireTransition('admin.directory.users', 'home'); // out of admin

    [isOpen] = result.current;
    expect(isOpen).toBe(true);
  });

  it('should not change when navigating inside admin', () => {
    const { result } = setup(true);

    fireTransition('admin.directory.users', 'admin.directory.groups');

    const [isOpen] = result.current;
    expect(isOpen).toBe(true);
  });

  it('should not change when navigating outside admin', () => {
    const { result } = setup(true);

    fireTransition('home', 'dashboard');

    const [isOpen] = result.current;
    expect(isOpen).toBe(true);
  });
});
