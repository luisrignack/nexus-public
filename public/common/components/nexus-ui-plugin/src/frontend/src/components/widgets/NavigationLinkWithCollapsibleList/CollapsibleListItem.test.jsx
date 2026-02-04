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
import CollapsibleListItem from './CollapsibleListItem';

describe('CollapsibleListItem', () => {
  let renderComponent;
  beforeAll(() => {
    const minimalProps = { text: 'base text', href: 'base href' };
    renderComponent = (props) => render(<CollapsibleListItem {...minimalProps} {...props}></CollapsibleListItem>);
  });

  it('renders the link', () => {
    renderComponent();
    const link = screen.getByRole('link', { name: 'base text' });
    expect(link).toBeVisible();
    expect(link).toHaveAttribute('href', 'base href');
    expect(link).not.toHaveClass('selected');
  });

  it('renders the link with selected styles', () => {
    renderComponent({ isSelected: true });
    const link = screen.getByRole('link', { name: 'base text' });
    expect(link).toHaveClass('selected');
  });
});
