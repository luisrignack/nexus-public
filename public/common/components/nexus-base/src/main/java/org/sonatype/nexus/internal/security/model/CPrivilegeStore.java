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

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.sonatype.nexus.datastore.ConfigStoreSupport;
import org.sonatype.nexus.datastore.api.DataSessionSupplier;
import org.sonatype.nexus.security.config.CPrivilege;
import org.sonatype.nexus.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
class CPrivilegeStore
    extends ConfigStoreSupport<CPrivilegeDAO>
{
  @Autowired
  protected CPrivilegeStore(final DataSessionSupplier sessionSupplier) {
    super(sessionSupplier);
  }

  @Transactional
  Iterable<CPrivilegeData> browse() {
    return dao().browse();
  }

  @Transactional
  void create(final CPrivilegeData privilege) {
    dao().create(privilege);
  }

  @Transactional
  boolean delete(final String id) {
    return dao().delete(id);
  }

  @Transactional
  boolean deleteByName(final String name) {
    return dao().deleteByName(name);
  }

  @Transactional
  List<CPrivilege> findByIds(final Set<String> ids) {
    return dao().findByIds(ids);
  }

  @Transactional
  Optional<CPrivilege> read(final String id) {
    return dao().read(id)
        .map(CPrivilege.class::cast);
  }

  @Transactional
  Optional<CPrivilege> readByName(final String name) {
    return dao().readByName(name);
  }

  @Transactional
  boolean update(final CPrivilegeData privilege) {
    return dao().update(privilege);
  }

  @Transactional
  boolean updateByName(final CPrivilegeData privilege) {
    return dao().updateByName(privilege);
  }
}
