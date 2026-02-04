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
package org.sonatype.nexus.internal.security.model;

import java.util.Optional;

import org.sonatype.nexus.datastore.ConfigStoreSupport;
import org.sonatype.nexus.datastore.api.DataSessionSupplier;
import org.sonatype.nexus.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
class CUserRoleMappingStore
    extends ConfigStoreSupport<CUserRoleMappingDAO>
{
  @Autowired
  protected CUserRoleMappingStore(final DataSessionSupplier sessionSupplier) {
    super(sessionSupplier);
  }

  @Transactional
  Iterable<CUserRoleMappingData> browse() {
    return dao().browse();
  }

  @Transactional
  void create(final CUserRoleMappingData mapping) {
    dao().create(mapping);
  }

  @Transactional
  Optional<CUserRoleMappingData> read(final String userId, final String source) {
    return dao().read(userId, source);
  }

  @Transactional
  boolean update(final CUserRoleMappingData mapping) {
    return dao().update(mapping);
  }

  @Transactional
  boolean delete(final String userId, final String source) {
    return dao().delete(userId, source);
  }
}
