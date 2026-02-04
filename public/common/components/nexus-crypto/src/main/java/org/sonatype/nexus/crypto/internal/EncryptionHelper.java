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
package org.sonatype.nexus.crypto.internal;

import com.fasterxml.jackson.core.Base64Variants;

public final class EncryptionHelper
{
  private EncryptionHelper() {
    // empty
  }

  public static final String KEY_ITERATION_PHC = "key_iteration";

  public static final String KEY_LEN_PHC = "key_len";

  public static final String IV = "iv";

  public static String toBase64(final byte[] value) {
    return Base64Variants.getDefaultVariant().encode(value);
  }

  public static byte[] fromBase64(final String encoded) {
    return Base64Variants.getDefaultVariant().decode(encoded);
  }
}
