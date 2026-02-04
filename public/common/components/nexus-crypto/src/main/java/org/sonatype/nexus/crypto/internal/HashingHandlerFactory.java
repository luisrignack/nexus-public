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

import org.sonatype.nexus.crypto.HashingHandler;
import org.sonatype.nexus.crypto.internal.error.CipherException;

/**
 * Factory for {@link HashingHandler}s, for secrets encryption stored in the PHC string format.
 * <p>
 * for further info check
 * <a href="https://github.com/P-H-C/phc-string-format/blob/master/phc-sf-spec.md">PHC String format</a>
 */
public interface HashingHandlerFactory
{
  HashingHandler create(String encryptedSecret) throws CipherException;

  HashingHandler create(String algorithmIdentifier, byte[] salt) throws CipherException;

  HashingHandler create(String algorithmIdentifier, byte[] salt, Integer iterations) throws CipherException;
}
