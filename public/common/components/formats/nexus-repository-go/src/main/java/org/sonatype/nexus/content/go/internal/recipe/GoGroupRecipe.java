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
package org.sonatype.nexus.content.go.internal.recipe;

import javax.annotation.Nonnull;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import org.sonatype.nexus.common.upgrade.AvailabilityVersion;
import org.sonatype.nexus.repository.Format;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.Type;
import org.sonatype.nexus.repository.group.GroupFacet;
import org.sonatype.nexus.repository.http.HttpHandlers;
import org.sonatype.nexus.repository.go.internal.GoFormat;
import org.sonatype.nexus.repository.types.GroupType;
import org.sonatype.nexus.repository.view.ConfigurableViewFacet;
import org.sonatype.nexus.repository.view.Route;
import org.sonatype.nexus.repository.view.Router;
import org.sonatype.nexus.repository.view.ViewFacet;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * PyPI group repository recipe.
 */
@AvailabilityVersion(from = "1.0")
@Component
@Qualifier(GoGroupRecipe.NAME)
@Singleton
public class GoGroupRecipe
    extends GoRecipeSupport
{
  public static final String NAME = "go-group";

  private final Provider<GroupFacet> groupFacet;

  private final GoGroupHandler groupHandler;

  @Inject
  public GoGroupRecipe(
      @Qualifier(GroupType.NAME) final Type type,
      @Qualifier(GoFormat.NAME) final Format format,
      @Qualifier("default") final Provider<GroupFacet> groupFacet,
      final GoGroupHandler groupHandler)
  {
    super(type, format);
    this.groupFacet = checkNotNull(groupFacet);
    this.groupHandler = checkNotNull(groupHandler);
  }

  @Override
  public void apply(@Nonnull final Repository repository) throws Exception {
    repository.attach(securityFacet.get());
    repository.attach(configure(viewFacet.get()));
    repository.attach(groupFacet.get());
    repository.attach(contentFacet.get());
    repository.attach(browseFacet.get());
  }

  private ViewFacet configure(final ConfigurableViewFacet viewFacet) {
    Router.Builder builder = new Router.Builder();

    builder.route(new Route.Builder()
        .matcher(PATH_MATCHER)
        .handler(timingHandler)
        .handler(securityHandler)
        .handler(exceptionHandler)
        .handler(handlerContributor)
        .handler(contentHeadersHandler)
        .handler(groupHandler)
        .create());

    builder.defaultHandlers(HttpHandlers.notFound());

    viewFacet.configure(builder.create());

    return viewFacet;
  }
}
