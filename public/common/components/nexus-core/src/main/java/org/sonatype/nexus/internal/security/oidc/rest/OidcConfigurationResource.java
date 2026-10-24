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
package org.sonatype.nexus.internal.security.oidc.rest;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.internal.security.oidc.OidcConfigurationStoreImpl;
import org.sonatype.nexus.rest.Resource;
import org.sonatype.nexus.security.oidc.OidcConfiguration;
import org.sonatype.nexus.security.oidc.OidcConfigurationStore;

import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.stereotype.Component;

import static com.google.common.base.Preconditions.checkNotNull;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

/**
 * Administration of the OAuth2/OIDC single sign-on configuration, backing the CoreUI screen at
 * {@code /admin/security/oauth2}.
 *
 * <p>Because the admin form has no separate "enabled" toggle, a successful save of a fully specified
 * configuration activates the feature; the capability activation in turn installs the OAuth2 realm.</p>
 *
 * @since 3.89
 */
@Component
@Singleton
@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Path(OidcConfigurationResource.RESOURCE_PATH)
public class OidcConfigurationResource
    extends ComponentSupport
    implements Resource
{
  static final String RESOURCE_PATH = "internal/ui/oauth2";

  private final OidcConfigurationStore store;

  @Inject
  public OidcConfigurationResource(final OidcConfigurationStore store) {
    this.store = checkNotNull(store);
  }

  @GET
  @RequiresPermissions("nexus:oidc-settings:read")
  public OidcConfigurationXO read() {
    OidcConfiguration configuration = store.load();
    if (configuration == null) {
      configuration = store.newEntity();
    }
    return OidcConfigurationXO.from(configuration);
  }

  @PUT
  @RequiresAuthentication
  @RequiresPermissions("nexus:oidc-settings:update")
  public OidcConfigurationXO update(@NotNull @Valid final OidcConfigurationXO configurationXO) {
    if (configurationXO == null) {
      throw new IllegalArgumentException("Missing OAuth2/OIDC configuration");
    }

    OidcConfiguration incoming = configurationXO.toConfiguration();
    // The admin form always submits a complete configuration, so saving turns the feature on. An
    // explicitly disabled payload (from API callers) is honoured.
    incoming.setEnabled(configurationXO.isEnabled() || !isBlank(incoming.getClientId()));

    validate(incoming);

    store.save(incoming);
    return OidcConfigurationXO.from(store.load());
  }

  private static void validate(final OidcConfiguration configuration) {
    if (!configuration.isEnabled()) {
      return;
    }
    require(configuration.getClientId(), "clientId");
    require(configuration.getIdpAuthorizationUrl(), "idpAuthorizationUrl");
    require(configuration.getIdpTokenUrl(), "idpTokenUrl");
    require(configuration.getIdpJwksUrl(), "idpJwksUrl");
    require(configuration.getUsernameClaim(), "usernameClaim");
  }

  private static void require(final String value, final String field) {
    if (isBlank(value)) {
      throw new IllegalArgumentException("Field '" + field + "' is required when OAuth2/OIDC is enabled");
    }
  }

  private static boolean isBlank(final String value) {
    return value == null || value.isBlank();
  }
}
