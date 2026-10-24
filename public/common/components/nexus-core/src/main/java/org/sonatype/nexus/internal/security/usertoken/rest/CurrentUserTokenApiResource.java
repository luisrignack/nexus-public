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

import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.common.text.Strings2;
import org.sonatype.nexus.common.wonderland.AuthTicketService;
import org.sonatype.nexus.internal.security.usertoken.UserTokenConfiguration;
import org.sonatype.nexus.internal.security.usertoken.UserTokenService;
import org.sonatype.nexus.internal.security.usertoken.UserTokenService.UserToken;
import org.sonatype.nexus.rest.Resource;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.subject.PrincipalCollection;
import org.springframework.stereotype.Component;

import static com.google.common.base.Preconditions.checkNotNull;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;

/**
 * Token operations for the currently authenticated user.
 *
 * <p>Destructive or secret-revealing operations require a fresh authentication ticket, which the UI
 * obtains after asking the user to re-enter their password.</p>
 *
 * @since 3.89
 */
@Component
@Singleton
@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Path("/internal/current-user/user-token")
public class CurrentUserTokenApiResource
    extends ComponentSupport
    implements Resource
{
  private final UserTokenService userTokens;

  private final AuthTicketService authTickets;

  @Inject
  public CurrentUserTokenApiResource(
      final UserTokenService userTokens,
      final AuthTicketService authTickets)
  {
    this.userTokens = checkNotNull(userTokens);
    this.authTickets = checkNotNull(authTickets);
  }

  @GET
  @Path("/attributes")
  @RequiresAuthentication
  @RequiresPermissions("nexus:usertoken-current:read")
  public Response attributes() {
    PrincipalCollection principals = currentPrincipals();
    UserTokenConfiguration configuration = userTokens.getConfiguration();

    Optional<UserToken> token = userTokens.getToken(principals);
    if (token.isPresent()) {
      if (token.get().isExpired()) {
        userTokens.deleteToken(principals);
        return Response.status(Status.BAD_REQUEST)
            .type(TEXT_PLAIN)
            .entity("User Token expired")
            .build();
      }

      Long expiration = token.get().expiresAt() == null
          ? null
          : token.get().expiresAt().toInstant().toEpochMilli();
      return Response.ok(new CurrentUserTokenAttributesXO(configuration, expiration)).build();
    }

    return Response.ok(new CurrentUserTokenAttributesXO(configuration, null)).build();
  }

  @GET
  @RequiresAuthentication
  @RequiresPermissions("nexus:usertoken-current:read")
  public CurrentUserTokenXO getToken(@QueryParam("authToken") final String authToken) {
    requireFreshAuthentication(authToken);
    return new CurrentUserTokenXO(userTokens.getOrCreateToken(currentPrincipals()));
  }

  @POST
  @RequiresAuthentication
  @RequiresPermissions("nexus:usertoken-current:read")
  public CurrentUserTokenXO createToken(@QueryParam("authToken") final String authToken) {
    requireFreshAuthentication(authToken);
    return new CurrentUserTokenXO(userTokens.createToken(currentPrincipals()));
  }

  @DELETE
  @RequiresAuthentication
  @RequiresPermissions("nexus:usertoken-current:read")
  public void resetToken(@QueryParam("authToken") final String authToken) {
    requireFreshAuthentication(authToken);
    userTokens.deleteToken(currentPrincipals());
  }

  private void requireFreshAuthentication(final String authToken) {
    if (authToken == null || authToken.isBlank()) {
      throw new WebApplicationException("Missing authentication ticket", Status.BAD_REQUEST);
    }

    String ticket = authToken;
    try {
      ticket = Strings2.decodeBase64(authToken);
    }
    catch (IllegalArgumentException e) {
      log.trace("Authentication ticket was not base64 encoded", e);
    }

    if (!authTickets.redeemTicket(ticket)) {
      throw new WebApplicationException("Invalid authentication ticket", Status.FORBIDDEN);
    }
  }

  private static PrincipalCollection currentPrincipals() {
    PrincipalCollection principals = SecurityUtils.getSubject().getPrincipals();
    if (principals == null) {
      throw new WebApplicationException("Authentication required", Status.UNAUTHORIZED);
    }
    return principals;
  }
}
