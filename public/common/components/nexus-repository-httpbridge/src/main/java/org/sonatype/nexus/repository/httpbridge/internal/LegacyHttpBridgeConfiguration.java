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
package org.sonatype.nexus.repository.httpbridge.internal;

import org.sonatype.nexus.security.FilterChain;
import org.sonatype.nexus.security.JwtFilter;
import org.sonatype.nexus.security.anonymous.AnonymousFilter;
import org.sonatype.nexus.security.authc.AntiCsrfFilter;
import org.sonatype.nexus.security.authc.NexusAuthenticationFilter;
import org.sonatype.nexus.security.authc.apikey.ApiKeyAuthenticationFilter;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import static org.sonatype.nexus.common.app.FeatureFlags.JWT_ENABLED;
import static org.sonatype.nexus.common.app.FeatureFlags.SESSION_ENABLED;

@Configuration
public class LegacyHttpBridgeConfiguration
{
  /*
   * Bind after core-servlets but before error servlet
   */
  private static final int PRECEDENCE = -0x50000000;

  private static final String LEGACY_CONTENT_MOUNT_POINT = "/content/**";

  private static final String LEGACY_SERVICE_MOUNT_POINT = "/service/local/**";

  @Order(PRECEDENCE)
  @ConditionalOnProperty(value = SESSION_ENABLED, havingValue = "true", matchIfMissing = true)
  @Bean
  public FilterChain legacyContentHttpBridgeFilterChain() {
    return new FilterChain(LEGACY_CONTENT_MOUNT_POINT,
        NexusAuthenticationFilter.NAME,
        ApiKeyAuthenticationFilter.NAME,
        AnonymousFilter.NAME,
        AntiCsrfFilter.NAME);
  }

  @Order(PRECEDENCE)
  @ConditionalOnProperty(value = SESSION_ENABLED, havingValue = "true", matchIfMissing = true)
  @Bean
  public FilterChain legacyServiceHttpBridgeFilterChain() {
    return new FilterChain(LEGACY_SERVICE_MOUNT_POINT,
        NexusAuthenticationFilter.NAME,
        ApiKeyAuthenticationFilter.NAME,
        AnonymousFilter.NAME,
        AntiCsrfFilter.NAME);
  }

  @Order(PRECEDENCE)
  @ConditionalOnProperty(value = JWT_ENABLED, havingValue = "true")
  @Bean
  public FilterChain legacyContentHttpBridgeFilterChainJwt() {
    return new FilterChain(LEGACY_CONTENT_MOUNT_POINT,
        NexusAuthenticationFilter.NAME,
        JwtFilter.NAME,
        ApiKeyAuthenticationFilter.NAME,
        AnonymousFilter.NAME,
        AntiCsrfFilter.NAME);
  }

  @Order(PRECEDENCE)
  @ConditionalOnProperty(value = JWT_ENABLED, havingValue = "true")
  @Bean
  public FilterChain legacyServiceHttpBridgeFilterChainJwt() {
    return new FilterChain(LEGACY_SERVICE_MOUNT_POINT,
        NexusAuthenticationFilter.NAME,
        JwtFilter.NAME,
        ApiKeyAuthenticationFilter.NAME,
        AnonymousFilter.NAME,
        AntiCsrfFilter.NAME);
  }
}
