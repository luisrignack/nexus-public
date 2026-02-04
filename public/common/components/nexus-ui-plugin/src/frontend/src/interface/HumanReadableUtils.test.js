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
import HumanReadableUtils from './HumanReadableUtils';

describe('HumanReadableUtils', () => {
  describe('bytesToString', () => {
    it('returns "Unavailable" for a negative byte value', () => {
      expect(HumanReadableUtils.bytesToString(-1)).toBe('Unavailable');
    });

    it('formats small byte values correctly with default format (SI)', () => {
      expect(HumanReadableUtils.bytesToString(2)).toBe('2.00 Bytes');
    });

    it('formats kilobytes correctly with default format (SI)', () => {
      // 2000 bytes → 2.00 kB (SI)
      expect(HumanReadableUtils.bytesToString(2000)).toBe('2.00 kB');
    });

    it('formats megabytes correctly with default format (SI)', () => {
      // 1048576 bytes → 1.05 MB (SI)
      expect(HumanReadableUtils.bytesToString(1048576)).toBe('1.05 MB');
    });

    it('formats large values correctly with default format (SI)', () => {
      // 2 GB in bytes → 2,147,483,648 bytes
      expect(HumanReadableUtils.bytesToString(2147483648)).toBe('2.15 GB');
    });

    it('formats kilobytes correctly with specific format (JEDEC)', () => {
      // 2000 bytes → ~1.95 KB (JEDEC)
      expect(HumanReadableUtils.bytesToString(2000, 'jedec')).toBe('1.95 KB');
    });

    it('formats megabytes correctly with specific format (JEDEC)', () => {
      // 1_048_576 bytes → 1.00 MB (JEDEC)
      expect(HumanReadableUtils.bytesToString(1048576, 'jedec')).toBe('1.00 MB');
    });

    it('formats large values correctly with specific format (JEDEC)', () => {
      // 2,147,483,648 bytes → 2.00 GB (JEDEC)
      expect(HumanReadableUtils.bytesToString(2147483648, 'jedec')).toBe('2.00 GB');
    });
  });
});
