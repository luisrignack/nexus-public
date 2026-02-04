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
class CRoleStore
    extends ConfigStoreSupport<CRoleDAO>
{
  @Autowired
  protected CRoleStore(final DataSessionSupplier sessionSupplier) {
    super(sessionSupplier);
  }

  @Transactional
  Iterable<CRoleData> browse() {
    return dao().browse();
  }

  @Transactional
  void create(final CRoleData role) {
    dao().create(role);
  }

  @Transactional
  boolean delete(final String id) {
    return dao().delete(id);
  }

  @Transactional
  Optional<CRoleData> read(final String id) {
    return dao().read(id);
  }

  @Transactional
  Iterable<CRoleData> readByIds(final java.util.Collection<String> ids) {
    if (ids == null || ids.isEmpty()) {
      return java.util.Collections.emptyList();
    }
    return dao().readByIds(ids);
  }

  @Transactional
  boolean update(final CRoleData roleData) {
    return dao().update(roleData);
  }
}
