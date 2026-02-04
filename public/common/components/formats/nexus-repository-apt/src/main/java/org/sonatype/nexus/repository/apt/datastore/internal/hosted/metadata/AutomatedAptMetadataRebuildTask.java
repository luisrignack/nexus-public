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
package org.sonatype.nexus.repository.apt.datastore.internal.hosted.metadata;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.RepositoryTaskSupport;
import org.sonatype.nexus.repository.apt.AptFormat;
import org.sonatype.nexus.repository.types.HostedType;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Automated APT metadata rebuild task.
 * This task is triggered automatically by the metadata scheduler when repository changes occur.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class AutomatedAptMetadataRebuildTask
    extends RepositoryTaskSupport
{
  public AutomatedAptMetadataRebuildTask() {
    super(false);
  }

  @Override
  public String getMessage() {
    return "Automated APT metadata rebuild";
  }

  @Override
  protected void execute(final Repository repository) {
    log.info("Starting automated APT metadata rebuild for repository {}", repository.getName());

    AptHostedMetadataFacet facet = repository.facet(AptHostedMetadataFacet.class);

    try {
      long start = System.currentTimeMillis();

      facet.rebuildMetadata();

      long duration = System.currentTimeMillis() - start;
      log.info("Completed APT metadata rebuild for repository {} in {} ms", repository.getName(), duration);
    }
    catch (IOException e) {
      log.error("Failed to rebuild APT metadata for repository {}", repository.getName(), e);
      throw new UncheckedIOException(e);
    }
  }

  @Override
  protected boolean appliesTo(final Repository repository) {
    return (repository.getType() instanceof HostedType) &&
        AptFormat.NAME.equals(repository.getFormat().getValue());
  }
}
