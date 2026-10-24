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

import java.io.IOException;
import java.util.Optional;

import javax.annotation.Nonnull;
import jakarta.inject.Singleton;

import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.go.GoCoordinatesHelper;
import org.sonatype.nexus.repository.view.Context;
import org.sonatype.nexus.repository.view.Handler;
import org.sonatype.nexus.repository.view.Request;
import org.sonatype.nexus.repository.view.Response;

import org.springframework.stereotype.Component;

import static org.sonatype.nexus.repository.http.HttpResponses.badRequest;

/**
 * Redirects GET/HEAD for the repository root to the GOPROXY behavior (clients query module paths).
 */
@Component
@Singleton
public class GoRedirectHandler
    implements Handler
{
  @Nonnull
  @Override
  public Response handle(@Nonnull final Context context) {
    return badRequest("Go module proxy repository. Use GOPROXY to access modules.");
  }
}
