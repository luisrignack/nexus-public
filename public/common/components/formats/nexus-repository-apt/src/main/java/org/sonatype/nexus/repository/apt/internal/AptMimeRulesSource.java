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
package org.sonatype.nexus.repository.apt.internal;

import javax.annotation.Nullable;

import org.sonatype.nexus.mime.MimeRule;
import org.sonatype.nexus.mime.MimeRulesSource;
import org.sonatype.nexus.repository.apt.AptFormat;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@Qualifier(AptFormat.NAME)
public class AptMimeRulesSource
    implements MimeRulesSource
{
  private static final MimeRule SIGNATURE_RULE = new MimeRule(true, AptMimeTypes.SIGNATURE);

  @Nullable
  @Override
  public MimeRule getRuleForName(final String name) {
    // GPG signatures should always be served as application/pgp-signature
    if (name.endsWith(".gpg")) {
      return SIGNATURE_RULE;
    }

    return null;
  }
}
