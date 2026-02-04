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
import {ApiUtils} from './ApiUtils';

describe('ApiUtils', () => {
  describe('unwrapError', () => {
    it('handles object with message', () => {
      const response = { data: { message: 'Test error message' } };

      const result = ApiUtils.unwrapError(response);

      expect(result).toEqual('Test error message');
    });

    it('handles object with error', () => {
      const response = { data: { error: 'Error from server' } };

      const result = ApiUtils.unwrapError(response);

      expect(result).toEqual('Error from server');
    });

    it('handles object with statusText', () => {
      const response = { statusText: 'Not Found' };

      const result = ApiUtils.unwrapError(response);

      expect(result).toEqual('Not Found');
    });

    it('handles object with status', () => {
      const response = { status: 404 };

      const result = ApiUtils.unwrapError(response);

      expect(result).toEqual('HTTP Error: 404');
    });

    it('handles completely unexpected object', () => {
      // Mock console.error to suppress output in test
      const consoleSpy = jest.spyOn(console, 'error').mockImplementation(() => {});
      const response = { foo: 'bar' };

      const result = ApiUtils.unwrapError(response);

      expect(result).toEqual('Unknown error occurred');
      expect(consoleSpy).toHaveBeenCalledWith('Unexpected response:', response);
      consoleSpy.mockRestore();
    });
  });
});
