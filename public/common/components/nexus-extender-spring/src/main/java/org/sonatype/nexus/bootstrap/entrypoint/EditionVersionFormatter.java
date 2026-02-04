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
package org.sonatype.nexus.bootstrap.entrypoint;

import java.util.Optional;

import javax.annotation.Nullable;

import org.sonatype.nexus.bootstrap.entrypoint.edition.NexusEditionSelector;
import org.sonatype.nexus.common.app.ApplicationVersion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for formatting edition and version information.
 */
public final class EditionVersionFormatter
{
  private static final Logger log = LoggerFactory.getLogger(EditionVersionFormatter.class);

  private EditionVersionFormatter() {
    // utility class
  }

  /**
   * Formats the edition ID and version for display.
   *
   * @param nexusEditionSelector the edition selector
   * @param applicationVersion the application version (may be null)
   * @return formatted string in the form "editionId" or "editionId/version"
   */
  public static String formatEditionAndVersion(
      final NexusEditionSelector nexusEditionSelector,
      @Nullable final ApplicationVersion applicationVersion)
  {
    String editionId = Optional.ofNullable(nexusEditionSelector.getCurrent())
        .map(edition -> edition.getId())
        .orElseGet(() -> {
          log.warn("Unable to determine edition ID, using 'unknown' as fallback");
          return "unknown";
        });
    return applicationVersion != null
        ? editionId + "/" + applicationVersion.getVersion()
        : editionId;
  }
}
