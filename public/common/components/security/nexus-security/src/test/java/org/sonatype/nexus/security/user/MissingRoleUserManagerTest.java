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
package org.sonatype.nexus.security.user;

import java.util.HashSet;
import java.util.Set;

import org.sonatype.nexus.security.AbstractSecurityTest;
import org.sonatype.nexus.security.SecuritySystem;
import org.sonatype.nexus.security.config.PreconfiguredSecurityConfigurationSource;
import org.sonatype.nexus.security.config.SecurityConfigurationSource;
import org.sonatype.nexus.security.role.RoleIdentifier;
import org.sonatype.nexus.security.user.MissingRoleUserManagerTest.MissingRoleUserManagerTestConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Import(MissingRoleUserManagerTestConfiguration.class)
public class MissingRoleUserManagerTest
    extends AbstractSecurityTest
{
  static class MissingRoleUserManagerTestConfiguration
  {
    @Qualifier("default")
    @Primary
    @Bean
    SecurityConfigurationSource securityConfigurationSource() {
      return new PreconfiguredSecurityConfigurationSource(MissingRoleUserManagerTestSecurity.securityModel());
    }
  }

  @Test
  void testInvalidRoleMapping() throws Exception {
    SecuritySystem userManager = getSecuritySystem();

    User user = userManager.getUser("jcoder");
    assertNotNull(user);

    Set<String> roleIds = new HashSet<String>();
    for (RoleIdentifier role : user.getRoles()) {
      assertNotNull(role, "User has null role.");
      roleIds.add(role.getRoleId());
    }
    assertFalse(roleIds.contains("INVALID-ROLE-BLA-BLA"));
  }
}
