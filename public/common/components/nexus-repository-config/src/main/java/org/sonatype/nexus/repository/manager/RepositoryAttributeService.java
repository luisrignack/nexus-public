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
package org.sonatype.nexus.repository.manager;

import javax.annotation.Nullable;

import org.sonatype.nexus.repository.Repository;

/**
 * Service interface for setting and getting repository attributes.
 * This interface allows modules to depend on attribute functionality
 * without creating circular dependencies.
 *
 * @since 3.84
 */
public interface RepositoryAttributeService
{
  /**
   * Sets a repository attribute using the best available method
   * (ContentFacet, AttributeStorage service, etc.)
   *
   * @param repository the repository (as Object to avoid dependencies)
   * @param key the attribute key
   * @param value the attribute value
   * @return true if the attribute was successfully set
   */
  boolean setRepositoryAttribute(final Repository repository, final String key, final Object value);

  /**
   * Gets a repository attribute using the best available method.
   *
   * @param repository the repository (as Object to avoid dependencies)
   * @param key the attribute key
   * @param defaultValue the default value if attribute is not found
   * @return the attribute value or default value
   */
  @Nullable
  String getRepositoryAttribute(final Repository repository, final String key, final @Nullable String defaultValue);
}
