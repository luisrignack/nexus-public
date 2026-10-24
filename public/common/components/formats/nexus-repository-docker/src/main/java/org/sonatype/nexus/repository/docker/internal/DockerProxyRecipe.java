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

import javax.annotation.Nonnull;
import javax.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import org.sonatype.nexus.common.upgrade.AvailabilityVersion;
import org.sonatype.nexus.repository.Format;
import org.sonatype.nexus.repository.RecipeSupport;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.Type;
import org.sonatype.nexus.repository.httpclient.HttpClientFacet;
import org.sonatype.nexus.repository.types.ProxyType;
import org.sonatype.nexus.repository.view.ConfigurableViewFacet;
import org.sonatype.nexus.repository.view.Router;
import org.sonatype.nexus.repository.view.ViewFacet;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sonatype.nexus.repository.http.HttpHandlers.notFound;

/**
 * Docker proxy repository recipe.
 *
 * <p>The registry API itself is implemented by {@link DockerRegistryServlet}, which resolves the repository by the
 * first path segment ({@code /v2/<repository>/...}). This recipe persists the repository configuration and attaches
 * the facets needed by the REST API and by {@link DockerProxyFacet}.</p>
 */
@AvailabilityVersion(from = "1.0")
@Component
@Qualifier(DockerProxyRecipe.NAME)
@Priority(Integer.MAX_VALUE)
@Order(Ordered.HIGHEST_PRECEDENCE)
@Singleton
public class DockerProxyRecipe
    extends RecipeSupport
{
  public static final String NAME = "docker-proxy";

  private final Provider<DockerSecurityFacet> securityFacet;

  private final Provider<ConfigurableViewFacet> viewFacet;

  private final Provider<HttpClientFacet> httpClientFacet;

  private final Provider<DockerProxyFacetImpl> proxyFacet;

  @Inject
  public DockerProxyRecipe(
      @Qualifier(ProxyType.NAME) final Type type,
      @Qualifier(DockerFormat.NAME) final Format format,
      final Provider<DockerSecurityFacet> securityFacet,
      final Provider<ConfigurableViewFacet> viewFacet,
      final Provider<HttpClientFacet> httpClientFacet,
      final Provider<DockerProxyFacetImpl> proxyFacet)
  {
    super(type, format);
    this.securityFacet = checkNotNull(securityFacet);
    this.viewFacet = checkNotNull(viewFacet);
    this.httpClientFacet = checkNotNull(httpClientFacet);
    this.proxyFacet = checkNotNull(proxyFacet);
  }

  @Override
  public void apply(@Nonnull final Repository repository) throws Exception {
    repository.attach(securityFacet.get());
    repository.attach(configure(viewFacet.get()));
    repository.attach(httpClientFacet.get());
    repository.attach(proxyFacet.get());
  }

  private ViewFacet configure(final ConfigurableViewFacet facet) {
    Router.Builder builder = new Router.Builder();
    // The Docker registry API is served by DockerRegistryServlet, not by /repository/<name>.
    builder.defaultHandlers(notFound());
    facet.configure(builder.create());
    return facet;
  }
}
