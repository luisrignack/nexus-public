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
package org.sonatype.nexus.bootstrap.security;

import java.util.List;

import org.sonatype.nexus.audit.AuditRecorder;
import org.sonatype.nexus.common.app.WebFilterPriority;
import org.sonatype.nexus.security.JwtHelper;
import org.sonatype.nexus.security.JwtSecurityFilter;
import org.sonatype.nexus.security.SecurityFilter;
import org.sonatype.nexus.security.authc.NexusAuthenticationFilter;
import org.sonatype.nexus.security.authc.apikey.ApiKeyAuthenticationFilter;
import org.sonatype.nexus.security.authc.apikey.ApiKeyExtractor;
import org.sonatype.nexus.security.jwt.JwtSessionRevocationService;

import org.apache.shiro.web.filter.mgt.FilterChainResolver;
import org.apache.shiro.web.mgt.WebSecurityManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import static org.sonatype.nexus.common.app.FeatureFlags.JWT_ENABLED;
import static org.sonatype.nexus.common.app.FeatureFlags.SESSION_ENABLED;

@Configuration
public class SecurityConfiguration
{
  @Order(WebFilterPriority.AUTHENTICATION)
  @ConditionalOnProperty(SESSION_ENABLED)
  @Bean
  public SecurityFilter securityFilter(
      final WebSecurityManager webSecurityManager,
      final FilterChainResolver filterChainResolver)
  {
    return new SecurityFilter(webSecurityManager, filterChainResolver);
  }

  @Order(WebFilterPriority.AUTHENTICATION)
  @ConditionalOnProperty(JWT_ENABLED)
  @Bean
  public JwtSecurityFilter jwtSecurityFilter(
      final WebSecurityManager webSecurityManager,
      final FilterChainResolver filterChainResolver,
      final JwtHelper jwtHelper,
      final JwtSessionRevocationService jwtSessionRevocationService,
      final AuditRecorder auditRecorder)
  {
    return new JwtSecurityFilter(webSecurityManager, filterChainResolver, jwtHelper,
        jwtSessionRevocationService, auditRecorder);
  }

  @Order(WebFilterPriority.AUTHENTICATION)
  @Bean
  public ApiKeyAuthenticationFilter apiKeyAuthenticationFilter(final List<ApiKeyExtractor> apiKeysList) {
    return new ApiKeyAuthenticationFilter(apiKeysList);
  }

  @Order(WebFilterPriority.AUTHENTICATION)
  @Bean
  public NexusAuthenticationFilter nexusAuthenticationFilter() {
    return new NexusAuthenticationFilter();
  }
}
