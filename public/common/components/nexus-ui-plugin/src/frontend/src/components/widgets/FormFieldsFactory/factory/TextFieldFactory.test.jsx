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
import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import TextFieldFactory from './TextFieldFactory';
import {SUPPORTED_FIELD_TYPES} from '../FormFieldsFactoryConstants';

describe('TextFieldFactory', () => {
  describe('metadata', () => {
    it('exports supported field types', () => {
      expect(TextFieldFactory.types).toBe(SUPPORTED_FIELD_TYPES.TEXT);
    });

    it('exports a component', () => {
      expect(TextFieldFactory.component).toBeDefined();
      expect(typeof TextFieldFactory.component).toBe('function');
    });
  });

  describe('Field component', () => {
    const Field = TextFieldFactory.component;
    const mockOnChange = jest.fn();

    const defaultProps = {
      id: 'testField',
      dynamicProps: {
        type: 'textfield'
      },
      current: {
        context: {
          data: {testField: 'test value'},
          pristineData: {testField: ''}
        }
      },
      onChange: mockOnChange
    };

    beforeEach(() => {
      mockOnChange.mockClear();
    });

    it('renders a text input field', () => {
      render(<Field {...defaultProps} />);

      const input = screen.getByRole('textbox');
      expect(input).toBeInTheDocument();
      expect(input).toHaveValue('test value');
    });

    it('renders a password field when type is password', () => {
      const props = {
        ...defaultProps,
        dynamicProps: {
          type: 'password'
        }
      };

      render(<Field {...props} />);

      const input = screen.getByDisplayValue('test value');
      expect(input).toHaveAttribute('type', 'password');
    });

    it('renders disabled field when disabled prop is true', () => {
      const props = {
        ...defaultProps,
        dynamicProps: {
          type: 'textfield',
          disabled: true
        }
      };

      render(<Field {...props} />);

      const input = screen.getByRole('textbox');
      expect(input).toBeDisabled();
    });

    it('renders read-only value as plain text when readOnly is true', () => {
      const props = {
        ...defaultProps,
        dynamicProps: {
          type: 'textfield',
          readOnly: true
        }
      };

      const {container} = render(<Field {...props} />);

      expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
      expect(container.textContent).toBe('test value');
    });

    it('calls onChange when input value changes', async () => {
      render(<Field {...defaultProps} />);

      const input = screen.getByRole('textbox');
      await userEvent.type(input, 'new value');

      expect(mockOnChange).toHaveBeenCalled();
      expect(mockOnChange).toHaveBeenCalledWith('testField', expect.any(String));
    });

    it('handles empty string value', () => {
      const props = {
        ...defaultProps,
        current: {
          context: {
            data: {testField: ''},
            pristineData: {testField: ''}
          }
        }
      };

      render(<Field {...props} />);

      const input = screen.getByRole('textbox');
      expect(input).toHaveValue('');
    });
  });
});
