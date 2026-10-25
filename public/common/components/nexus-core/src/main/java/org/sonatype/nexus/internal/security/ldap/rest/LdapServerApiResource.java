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
package org.sonatype.nexus.internal.security.ldap.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.internal.security.ldap.LdapConfigurationData;
import org.sonatype.nexus.rest.Resource;
import org.sonatype.nexus.security.authc.ldap.LdapRealm;
import org.sonatype.nexus.security.ldap.LdapConfiguration;
import org.sonatype.nexus.security.ldap.LdapConfigurationStore;
import org.sonatype.nexus.security.ldap.LdapDirectory;
import org.sonatype.nexus.security.ldap.LdapDirectory.LdapUserInfo;
import org.sonatype.nexus.security.realm.RealmManager;

import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.stereotype.Component;

import static com.google.common.base.Preconditions.checkNotNull;

@Component
@Singleton
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(LdapServerApiResource.RESOURCE_PATH)
public class LdapServerApiResource
    extends ComponentSupport
    implements Resource
{
  public static final String RESOURCE_PATH = "/v1/security/ldap";

  private final LdapConfigurationStore configurationStore;

  private final LdapDirectory ldapDirectory;

  private final RealmManager realmManager;

  @Inject
  public LdapServerApiResource(
      final LdapConfigurationStore configurationStore,
      final LdapDirectory ldapDirectory,
      final RealmManager realmManager)
  {
    this.configurationStore = checkNotNull(configurationStore);
    this.ldapDirectory = checkNotNull(ldapDirectory);
    this.realmManager = checkNotNull(realmManager);
  }

  @GET
  @RequiresPermissions("nexus:ldap:read")
  public List<LdapServerXO> list() {
    return configurationStore.list().stream()
        .map(LdapServerXoMapper::toXO)
        .collect(Collectors.toList());
  }

  /**
   * Reads a single server. The React admin UI addresses servers by their (unique) name, so the
   * path segment accepts either the name or the backing capability id.
   */
  @GET
  @Path("/{name}")
  @RequiresPermissions("nexus:ldap:read")
  public LdapServerXO read(@PathParam("name") final String name) {
    return LdapServerXoMapper.toXO(findByKey(name));
  }

  @POST
  @RequiresAuthentication
  @RequiresPermissions("nexus:ldap:create")
  public LdapServerXO create(@NotNull @Valid final LdapServerXO xo) {
    LdapConfiguration configuration = LdapServerXoMapper.toConfiguration(xo);
    LdapConfiguration created = configurationStore.create(configuration);
    try {
      realmManager.enableRealm(LdapRealm.REALM_NAME);
    }
    catch (Exception e) {
      // ignore
    }
    return LdapServerXoMapper.toXO(created);
  }

  @PUT
  @Path("/{id}")
  @RequiresAuthentication
  @RequiresPermissions("nexus:ldap:update")
  public LdapServerXO update(@PathParam("id") final String id, @NotNull @Valid final LdapServerXO xo) {
    LdapConfiguration existing = findExisting(id, xo);
    LdapConfiguration configuration = LdapServerXoMapper.toConfiguration(xo);
    if (configuration instanceof LdapConfigurationData data) {
      data.setId(existing.getId());
    }
    else {
      try {
        configuration.setId(existing.getId());
      }
      catch (Exception e) {
        // ignore
      }
    }
    try {
      configuration.setOrder(existing.getOrder());
    }
    catch (Exception e) {
      // ignore
    }
    configurationStore.update(configuration);
    try {
      realmManager.enableRealm(LdapRealm.REALM_NAME);
    }
    catch (Exception e) {
      // ignore
    }
    return LdapServerXoMapper.toXO(byId(existing.getId()));
  }

  @DELETE
  @Path("/{id}")
  @RequiresAuthentication
  @RequiresPermissions("nexus:ldap:delete")
  public Response delete(@PathParam("id") final String id) {
    configurationStore.delete(findByKey(id).getId());
    return Response.noContent().build();
  }

  @POST
  @Path("/change-order")
  @RequiresAuthentication
  @RequiresPermissions("nexus:ldap:update")
  public Response changeOrder(final List<String> keys) {
    if (keys == null) {
      throw new WebApplicationException("Missing order", Response.Status.BAD_REQUEST);
    }
    // the admin UI orders the list by server name, the store orders it by capability id
    configurationStore.changeOrder(keys.stream()
        .map(this::resolveId)
        .collect(Collectors.toList()));
    return Response.noContent().build();
  }

  @POST
  @Path("/verify-connection")
  @RequiresAuthentication
  @RequiresPermissions("nexus:ldap:read")
  public Response verifyConnection(@NotNull @Valid final LdapServerXO xo) {
    try {
      ldapDirectory.verifyConnection(LdapServerXoMapper.toConfiguration(xo));
      return Response.ok().build();
    }
    catch (Exception e) {
      log.debug("LDAP connection verification failed", e);
      throw new WebApplicationException(e.getMessage(), Response.Status.BAD_REQUEST);
    }
  }

  @POST
  @Path("/verify-login")
  @RequiresAuthentication
  @RequiresPermissions("nexus:ldap:read")
  public Response verifyLogin(@NotNull @Valid final LdapServerXO xo) {
    try {
      if (xo.getAuthUsername() == null || xo.getAuthPassword() == null) {
        throw new IllegalArgumentException("Username and password are required");
      }
      ldapDirectory.authenticate(
          LdapServerXoMapper.toConfiguration(xo), xo.getAuthUsername(), xo.getAuthPassword().toCharArray());
      return Response.ok().build();
    }
    catch (Exception e) {
      log.debug("LDAP login verification failed", e);
      throw new WebApplicationException(e.getMessage(), Response.Status.BAD_REQUEST);
    }
  }

  @POST
  @Path("/verify-user-mapping")
  @RequiresAuthentication
  @RequiresPermissions("nexus:ldap:read")
  public List<LdapUserXO> verifyUserMapping(@NotNull @Valid final LdapServerXO xo) {
    try {
      List<LdapUserXO> users = new ArrayList<>();
      for (LdapUserInfo userInfo : ldapDirectory.verifyUserMapping(LdapServerXoMapper.toConfiguration(xo))) {
        users.add(LdapServerXoMapper.toUserXO(userInfo));
      }
      return users;
    }
    catch (Exception e) {
      log.debug("LDAP user mapping verification failed", e);
      throw new WebApplicationException(e.getMessage(), Response.Status.BAD_REQUEST);
    }
  }

  /**
   * Resolves a server by name or, when no server matches, by its capability id. The admin UI
   * addresses servers by name everywhere (list links, detail route, delete and reorder).
   */
  private LdapConfiguration findByKey(final String key) {
    if (key == null || key.isEmpty()) {
      throw new WebApplicationException("LDAP server not found", Response.Status.NOT_FOUND);
    }
    return configurationStore.list().stream()
        .filter(c -> key.equals(c.getName()) || key.equals(c.getId()))
        .findFirst()
        .orElseThrow(() -> new WebApplicationException("LDAP server not found", Response.Status.NOT_FOUND));
  }

  /**
   * Resolves the server to update. The admin UI puts to the name currently in the form, which is
   * the new name when the server is being renamed, so an unknown path falls back to the id carried
   * in the request body.
   */
  private LdapConfiguration findExisting(final String key, final LdapServerXO xo) {
    try {
      return findByKey(key);
    }
    catch (WebApplicationException notFound) {
      return findByKey(xo.getId());
    }
  }

  private LdapConfiguration byId(final String id) {
    return configurationStore.list().stream()
        .filter(c -> id.equals(c.getId()))
        .findFirst()
        .orElseThrow(() -> new WebApplicationException("LDAP server not found", Response.Status.NOT_FOUND));
  }

  private String resolveId(final String key) {
    return configurationStore.list().stream()
        .filter(c -> key.equals(c.getName()) || key.equals(c.getId()))
        .map(LdapConfiguration::getId)
        .findFirst()
        .orElse(key);
  }
}
