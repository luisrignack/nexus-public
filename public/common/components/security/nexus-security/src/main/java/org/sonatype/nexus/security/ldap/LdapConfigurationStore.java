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
package org.sonatype.nexus.security.ldap;

import java.util.List;

/**
 * Store of the LDAP server configurations, ordered by the connection/authentication attempt order.
 *
 * <p>The implementation lives in {@code nexus-core} and is injected lazily by the
 * {@link org.sonatype.nexus.security.authc.ldap.LdapRealm}, the same way the OIDC realm consumes its
 * configuration store, to avoid the {@code SecuritySystem} construction cycle.</p>
 *
 * @since 3.89
 */
public interface LdapConfigurationStore
{
  /**
   * All configured servers sorted by {@link LdapConfiguration#getOrder()}.
   */
  List<LdapConfiguration> list();

  /**
   * All enabled servers sorted by {@link LdapConfiguration#getOrder()}; only these take part in
   * authentication.
   */
  List<LdapConfiguration> listActive();

  /**
   * Creates a new server configuration and returns it with its id and order assigned.
   */
  LdapConfiguration create(LdapConfiguration configuration);

  /**
   * Updates an existing server configuration.
   */
  void update(LdapConfiguration configuration);

  /**
   * Deletes a server configuration; returns {@code true} when the realm was disabled because no
   * active server is left.
   */
  boolean delete(String id);

  /**
   * Reorders the servers: the given ids are assigned orders 1..n in the listed sequence.
   */
  void changeOrder(List<String> ids);

  /**
   * Number of enabled servers, used by the capability to decide whether the realm stays enabled.
   */
  int activeCount();
}
