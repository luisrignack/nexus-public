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
package org.sonatype.nexus.internal.security.usertoken.rest;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javax.ws.rs.DELETE;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.internal.security.usertoken.UserTokenService;
import org.sonatype.nexus.rest.Resource;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.stereotype.Component;

import static com.google.common.base.Preconditions.checkNotNull;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

/**
 * Resets a specific user's token from the user administration screen.
 *
 * @since 3.89
 */
@Component
@Singleton
@Produces(APPLICATION_JSON)
@Path("/v1/security/users")
public class UserTokenResetApiResource
    extends ComponentSupport
    implements Resource
{
  private final UserTokenService userTokens;

  @Inject
  public UserTokenResetApiResource(final UserTokenService userTokens) {
    this.userTokens = checkNotNull(userTokens);
  }

  @DELETE
  @Path("/{userId}/{source}/user-token-reset")
  @RequiresPermissions("nexus:usertoken-settings:update")
  public void resetUserToken(
      @PathParam("userId") final String userId,
      @PathParam("source") final String source)
  {
    log.debug("Resetting user token for {} ({})", userId, source);
    userTokens.deleteTokensForUser(userId);
  }
}
