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
package org.sonatype.nexus.repository.content.browse;

import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.RepositoryTaskSupport;
import org.sonatype.nexus.scheduling.Cancelable;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class TrimBrowseNodesTask
    extends RepositoryTaskSupport
    implements Cancelable
{
  @Autowired
  public TrimBrowseNodesTask() {
  }

  @Override
  public String getMessage() {
    return "Trimming empty browse nodes for " + getRepositoryField();
  }

  @Override
  protected void execute(final Repository repository) {
    repository.optionalFacet(BrowseFacet.class).ifPresent(browseFacet -> {
      log.info("Trimming empty browse nodes for repository: {}", repository.getName());
      browseFacet.trimBrowseNodes();
      log.info("Finished trimming empty browse nodes for repository: {}", repository.getName());
    });
  }

  @Override
  protected boolean appliesTo(final Repository repository) {
    return repository != null && repository.optionalFacet(BrowseFacet.class).isPresent();
  }
}
