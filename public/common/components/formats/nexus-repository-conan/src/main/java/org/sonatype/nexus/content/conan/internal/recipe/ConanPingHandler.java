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
package org.sonatype.nexus.content.conan.internal.recipe;

import javax.annotation.Nonnull;
import jakarta.inject.Singleton;

import org.sonatype.nexus.repository.conan.ConanCoordinates;
import org.sonatype.nexus.repository.view.Context;
import org.sonatype.nexus.repository.view.Handler;
import org.sonatype.nexus.repository.view.Response;

import org.springframework.stereotype.Component;

/**
 * Advertises the Conan server version on {@code ping} requests.
 *
 * <p>The handler never short circuits: it lets the rest of the chain answer the ping (the hosted content handler
 * replies locally, a proxy fetches the ping from its remote) and only decorates successful responses with
 * {@code X-Conan-Server-Version}, which is how Conan clients decide whether the server speaks the v2 protocol.</p>
 */
@Component
@Singleton
public class ConanPingHandler
    implements Handler
{
  public static final String SERVER_VERSION_HEADER = "X-Conan-Server-Version";

  public static final String SERVER_VERSION = "2.0";

  private static final String[] PING_PATHS = {"ping", "v1/ping", "v2/ping"};

  @Nonnull
  @Override
  public Response handle(@Nonnull final Context context) throws Exception {
    if (!isPing(context.getRequest().getPath())) {
      return context.proceed();
    }

    Response response = context.proceed();
    if (response.getStatus().isSuccessful()) {
      response.getHeaders().set(SERVER_VERSION_HEADER, SERVER_VERSION);
    }
    return response;
  }

  /**
   * @return {@code true} for {@code /ping}, {@code /v1/ping} and {@code /v2/ping}
   */
  public static boolean isPing(final String rawPath) {
    String path = ConanCoordinates.normalize(rawPath);
    for (final String ping : PING_PATHS) {
      if (ping.equals(path)) {
        return true;
      }
    }
    return false;
  }
}
