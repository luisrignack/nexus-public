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
import { render } from '@testing-library/react';
import { faCaretUp, faCaretDown, faMinus } from '@fortawesome/free-solid-svg-icons';
import ChangeIcon from './ChangeIcon';

describe('ChangeIcon', () => {
    it('renders the minus icon when value is "N/A"', () => {
        const { container } = render(<ChangeIcon value="N/A" />);
        expect(container.querySelector('svg')).toBeTruthy();
        expect(container.querySelector('svg').getAttribute('data-icon')).toBe(faMinus.iconName);
    });

    it('renders the caret up icon when value is greater than 0', () => {
        const { container } = render(<ChangeIcon value={10} />);
        expect(container.querySelector('svg')).toBeTruthy();
        expect(container.querySelector('svg').getAttribute('data-icon')).toBe(faCaretUp.iconName);
    });

    it('renders the caret down icon when value is less than 0', () => {
        const { container } = render(<ChangeIcon value={-5} />);
        expect(container.querySelector('svg')).toBeTruthy();
        expect(container.querySelector('svg').getAttribute('data-icon')).toBe(faCaretDown.iconName);
    });

    it('renders nothing when value is null', () => {
        const { container } = render(<ChangeIcon value={null} />);
        expect(container.querySelector('svg')).toBeFalsy();
    });

    it('renders nothing when value is 0', () => {
        const { container } = render(<ChangeIcon value={0} />);
        expect(container.querySelector('svg')).toBeFalsy();
    });
});
