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
package org.sonatype.nexus.bootstrap.rapture;

import org.sonatype.nexus.rapture.internal.security.JwtAuthenticationFilter;
import org.sonatype.nexus.rapture.internal.security.SessionAuthenticationFilter;
import org.sonatype.nexus.security.FilterChain;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.sonatype.nexus.common.app.FeatureFlags.JWT_ENABLED;
import static org.sonatype.nexus.common.app.FeatureFlags.SESSION_ENABLED;
import static org.sonatype.nexus.rapture.internal.security.SessionServlet.SESSION_MP;

@Configuration
public class RaptureConfiguration
{
  @ConditionalOnProperty(value = SESSION_ENABLED, havingValue = "true", matchIfMissing = true)
  @Bean
  public FilterChain sessionFilterChain() {
    return new FilterChain(SESSION_MP, SessionAuthenticationFilter.NAME);
  }

  @ConditionalOnProperty(value = JWT_ENABLED, havingValue = "true")
  @Bean
  public FilterChain sessionFilterChain_jwt() {
    return new FilterChain(SESSION_MP, JwtAuthenticationFilter.NAME);
  }

}
