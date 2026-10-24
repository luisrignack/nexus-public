/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark. The
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.repository.docker.internal;

import org.sonatype.nexus.security.FilterChain;
import org.sonatype.nexus.security.JwtFilter;
import org.sonatype.nexus.security.anonymous.AnonymousFilter;
import org.sonatype.nexus.security.authc.AntiCsrfFilter;
import org.sonatype.nexus.security.authc.NexusAuthenticationFilter;
import org.sonatype.nexus.security.authc.apikey.ApiKeyAuthenticationFilter;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.sonatype.nexus.common.app.FeatureFlags.JWT_ENABLED;
import static org.sonatype.nexus.common.app.FeatureFlags.SESSION_ENABLED;

/**
 * Security chains for the Docker registry API.
 *
 * <p>{@code /v2/**} is not under {@code /repository/**}, so it does not inherit the repository filter chain. Without
 * these chains Shiro never processes the {@code Authorization: Basic} header and the proxy would be usable without
 * credentials even when anonymous access is disabled. The chains authenticate Basic/API-key credentials and let
 * {@link DockerRegistryService} decide whether the request may proceed.</p>
 */
@Configuration
public class DockerConfiguration
{
  public static final String MOUNT_POINT = "/v2";

  public static final String MOUNT_POINT_WILDCARD = MOUNT_POINT + "/**";

  @ConditionalOnProperty(value = SESSION_ENABLED, havingValue = "true", matchIfMissing = true)
  @Bean
  public FilterChain dockerFilterChain() {
    return new FilterChain(MOUNT_POINT_WILDCARD,
        NexusAuthenticationFilter.NAME,
        ApiKeyAuthenticationFilter.NAME,
        AnonymousFilter.NAME,
        AntiCsrfFilter.NAME);
  }

  @ConditionalOnProperty(value = SESSION_ENABLED, havingValue = "true", matchIfMissing = true)
  @Bean
  public FilterChain dockerRootFilterChain() {
    return new FilterChain(MOUNT_POINT,
        NexusAuthenticationFilter.NAME,
        ApiKeyAuthenticationFilter.NAME,
        AnonymousFilter.NAME,
        AntiCsrfFilter.NAME);
  }

  @ConditionalOnProperty(value = JWT_ENABLED, havingValue = "true")
  @Bean
  public FilterChain dockerFilterChainJwt() {
    return new FilterChain(MOUNT_POINT_WILDCARD,
        NexusAuthenticationFilter.NAME,
        JwtFilter.NAME,
        ApiKeyAuthenticationFilter.NAME,
        AnonymousFilter.NAME,
        AntiCsrfFilter.NAME);
  }
}
