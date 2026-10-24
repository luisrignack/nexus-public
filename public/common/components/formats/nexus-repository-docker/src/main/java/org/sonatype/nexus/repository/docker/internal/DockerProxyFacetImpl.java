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
package org.sonatype.nexus.repository.docker.internal;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import javax.annotation.Nullable;

import jakarta.inject.Inject;

import org.sonatype.nexus.common.collect.NestedAttributesMap;
import org.sonatype.nexus.crypto.secrets.Secret;
import org.sonatype.nexus.crypto.secrets.SecretsService;
import org.sonatype.nexus.repository.FacetSupport;
import org.sonatype.nexus.repository.config.Configuration;
import org.sonatype.nexus.repository.docker.internal.DockerRegistryConfig.Upstream;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Stores the Docker-specific configuration of a proxy repository and resolves it to a
 * {@link DockerRegistryConfig.Upstream} for {@link DockerRegistryService}.
 *
 * <p>The upstream registry is taken from {@code dockerProxy.indexUrl} when present, and falls back to the generic
 * {@code proxy.remoteUrl} otherwise. When the index type is {@code HUB} (or the URL points at Docker Hub) the
 * configured global {@code dockerhub} mirror is used so Docker Hub official images keep working in networks where
 * {@code docker.io} is not directly reachable.</p>
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DockerProxyFacetImpl
    extends FacetSupport
    implements DockerProxyFacet
{
  static final String HUB = "HUB";

  private final DockerRegistryConfig registryConfig;

  private final SecretsService secretsService;

  private volatile Upstream upstream;

  private volatile boolean resolved;

  @Inject
  public DockerProxyFacetImpl(final DockerRegistryConfig registryConfig, final SecretsService secretsService) {
    this.registryConfig = checkNotNull(registryConfig);
    this.secretsService = checkNotNull(secretsService);
  }

  @Override
  @Nullable
  public Upstream upstream() {
    Upstream value = upstream;
    if (!resolved) {
      synchronized (this) {
        value = upstream;
        if (!resolved) {
          value = resolveUpstream();
          upstream = value;
          resolved = true;
        }
      }
    }
    return value;
  }

  @Override
  public boolean isCacheForeignLayers() {
    return Boolean.TRUE.equals(attributes().get("cacheForeignLayers", Boolean.class));
  }

  @Override
  protected void doConfigure(final Configuration configuration) {
    synchronized (this) {
      upstream = null;
      resolved = false;
    }
  }

  private Upstream resolveUpstream() {
    Configuration configuration = getRepository().getConfiguration();
    NestedAttributesMap dockerProxy = configuration.attributes("dockerProxy");
    NestedAttributesMap proxy = configuration.attributes("proxy");

    String indexType = trim(dockerProxy.get("indexType", String.class));
    String indexUrl = trim(dockerProxy.get("indexUrl", String.class));
    String remoteUrl = trim(proxy.get("remoteUrl", String.class));
    String baseUrl = indexUrl != null ? indexUrl : remoteUrl;

    boolean hub = HUB.equalsIgnoreCase(indexType) || isDockerHub(baseUrl);
    if (hub) {
      Upstream mirror = registryConfig.getUpstreams().get("dockerhub");
      if (mirror != null) {
        baseUrl = mirror.baseUrl();
      }
      else if (baseUrl == null || isDockerHub(baseUrl)) {
        baseUrl = "https://registry-1.docker.io";
      }
    }

    if (baseUrl == null) {
      log.debug("Docker repository {} has no upstream URL configured", getRepository().getName());
      return null;
    }

    return new Upstream(getRepository().getName(), normalize(baseUrl), authorization(configuration), hub);
  }

  private NestedAttributesMap attributes() {
    return getRepository().getConfiguration().attributes("dockerProxy");
  }

  @Nullable
  private String authorization(final Configuration configuration) {
    NestedAttributesMap authentication = configuration.attributes("httpclient").child("authentication");
    String type = trim(authentication.get("type", String.class));
    if (type == null) {
      return null;
    }

    if ("username".equalsIgnoreCase(type)) {
      String username = trim(authentication.get("username", String.class));
      String password = decrypt(authentication.get("password"));
      if (username != null && password != null) {
        String token = username + ':' + password;
        return "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
      }
    }
    else if ("bearerToken".equalsIgnoreCase(type) || "bearer".equalsIgnoreCase(type)) {
      String token = decrypt(authentication.get("bearerToken"));
      if (token == null) {
        token = decrypt(authentication.get("bearerTokenId"));
      }
      if (token != null) {
        return "Bearer " + token;
      }
    }
    return null;
  }

  @Nullable
  private String decrypt(final Object value) {
    if (!(value instanceof String) || ((String) value).trim().isEmpty()) {
      return null;
    }
    String candidate = ((String) value).trim();
    try {
      Secret secret = secretsService.from(candidate);
      char[] decrypted = secret.decrypt();
      if (decrypted != null) {
        return new String(decrypted);
      }
    }
    catch (Exception e) {
      log.trace("Docker repository {} uses a plain-text credential", getRepository().getName());
    }
    return candidate;
  }

  static String normalize(final String url) {
    String value = url.trim();
    while (value.endsWith("/")) {
      value = value.substring(0, value.length() - 1);
    }
    if (value.toLowerCase(Locale.ROOT).endsWith("/v2")) {
      value = value.substring(0, value.length() - 3);
      while (value.endsWith("/")) {
        value = value.substring(0, value.length() - 1);
      }
    }
    return value;
  }

  static boolean isDockerHub(final String url) {
    if (url == null || url.trim().isEmpty()) {
      return false;
    }
    try {
      String value = url.contains("://") ? url : "https://" + url;
      String host = URI.create(value).getHost();
      if (host == null) {
        return false;
      }
      String normalized = host.toLowerCase(Locale.ROOT);
      return "docker.io".equals(normalized) || "index.docker.io".equals(normalized)
          || "registry-1.docker.io".equals(normalized) || normalized.contains("dockerhub")
          || normalized.contains("daocloud.io");
    }
    catch (IllegalArgumentException e) {
      return false;
    }
  }

  @Nullable
  private static String trim(final String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
