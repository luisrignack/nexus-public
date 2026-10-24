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
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.internal.security.usertoken.UserTokenConfiguration;
import org.sonatype.nexus.internal.security.usertoken.UserTokenService;
import org.sonatype.nexus.rest.Resource;

import org.apache.shiro.authz.annotation.RequiresPermissions;

import static com.google.common.base.Preconditions.checkNotNull;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

/**
 * Administration of the user token feature.
 *
 * @since 3.89
 */
@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
public abstract class UserTokenApiResource
    extends ComponentSupport
    implements Resource
{
  private final UserTokenService userTokens;

  @Inject
  protected UserTokenApiResource(final UserTokenService userTokens) {
    this.userTokens = checkNotNull(userTokens);
  }

  @GET
  @RequiresPermissions("nexus:usertoken-settings:read")
  public UserTokenConfigurationXO getConfiguration() {
    return new UserTokenConfigurationXO(userTokens.getConfiguration());
  }

  @PUT
  @RequiresPermissions("nexus:usertoken-settings:update")
  public UserTokenConfigurationXO updateConfiguration(final UserTokenConfigurationXO configuration) {
    if (configuration == null) {
      throw new WebApplicationException("Missing user token configuration", Status.BAD_REQUEST);
    }
    if (configuration.getExpirationDays() < 1 || configuration.getExpirationDays() > 999) {
      throw new WebApplicationException(
          "User token expiry must be between 1 and 999 days", Status.BAD_REQUEST);
    }

    UserTokenConfiguration updated = userTokens.setConfiguration(configuration.toConfiguration());
    return new UserTokenConfigurationXO(updated);
  }

  @DELETE
  @RequiresPermissions("nexus:usertoken-users:delete")
  public void resetAll() {
    userTokens.deleteAllTokens();
  }
}
