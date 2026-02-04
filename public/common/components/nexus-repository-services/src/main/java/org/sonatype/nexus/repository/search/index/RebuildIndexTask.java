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
package org.sonatype.nexus.repository.search.index;

import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.RepositoryTaskSupport;
import org.sonatype.nexus.scheduling.Cancelable;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Internal task to rebuild index of given repository.
 *
 * @since 3.0
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class RebuildIndexTask
    extends RepositoryTaskSupport
    implements Cancelable
{
  @Override
  protected void execute(final Repository repository) {
    try {
      repository.facet(SearchIndexFacet.class).rebuildIndex();
    }
    catch (Exception e) {
      log.error("Could not perform rebuild index for repo {}, {}", repository.getName(), e.getMessage());
    }
  }

  @Override
  protected boolean appliesTo(final Repository repository) {
    return repository.optionalFacet(SearchIndexFacet.class).isPresent();
  }

  @Override
  public String getMessage() {
    return "Rebuilding search index of " + getRepositoryField();
  }
}
