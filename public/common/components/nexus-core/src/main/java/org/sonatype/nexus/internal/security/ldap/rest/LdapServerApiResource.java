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
        .map(this::toXO)
        .collect(Collectors.toList());
  }

  @POST
  @RequiresAuthentication
  @RequiresPermissions("nexus:ldap:create")
  public LdapServerXO create(@NotNull @Valid final LdapServerXO xo) {
    LdapConfiguration configuration = toConfiguration(xo);
    LdapConfiguration created = configurationStore.create(configuration);
    try {
      realmManager.enableRealm(LdapRealm.REALM_NAME);
    }
    catch (Exception e) {
      // ignore
    }
    return toXO(created);
  }

  @PUT
  @Path("/{id}")
  @RequiresAuthentication
  @RequiresPermissions("nexus:ldap:update")
  public LdapServerXO update(@PathParam("id") final String id, @NotNull @Valid final LdapServerXO xo) {
    LdapConfiguration existing = findById(id);
    LdapConfiguration configuration = toConfiguration(xo);
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
    return toXO(findById(id));
  }

  @DELETE
  @Path("/{id}")
  @RequiresAuthentication
  @RequiresPermissions("nexus:ldap:delete")
  public Response delete(@PathParam("id") final String id) {
    configurationStore.delete(id);
    return Response.noContent().build();
  }

  @POST
  @Path("/change-order")
  @RequiresAuthentication
  @RequiresPermissions("nexus:ldap:update")
  public Response changeOrder(final List<String> ids) {
    if (ids == null) {
      throw new WebApplicationException("Missing order", Response.Status.BAD_REQUEST);
    }
    configurationStore.changeOrder(ids);
    return Response.noContent().build();
  }

  @POST
  @Path("/verify-connection")
  @RequiresAuthentication
  @RequiresPermissions("nexus:ldap:read")
  public Response verifyConnection(@NotNull @Valid final LdapServerXO xo) {
    try {
      ldapDirectory.verifyConnection(toConfiguration(xo));
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
      ldapDirectory.authenticate(toConfiguration(xo), xo.getAuthUsername(), xo.getAuthPassword().toCharArray());
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
      for (LdapUserInfo userInfo : ldapDirectory.verifyUserMapping(toConfiguration(xo))) {
        users.add(new LdapUserXO(userInfo.getUsername(), userInfo.getRealName(), userInfo.getEmailAddress(),
            userInfo.getGroups()));
      }
      return users;
    }
    catch (Exception e) {
      log.debug("LDAP user mapping verification failed", e);
      throw new WebApplicationException(e.getMessage(), Response.Status.BAD_REQUEST);
    }
  }

  private LdapConfiguration findById(final String id) {
    return configurationStore.list().stream()
        .filter(c -> id.equals(c.getId()))
        .findFirst()
        .orElseThrow(() -> new WebApplicationException("LDAP server not found", Response.Status.NOT_FOUND));
  }

  private LdapConfiguration toConfiguration(final LdapServerXO xo) {
    LdapConfigurationData data = new LdapConfigurationData();
    data.setId(xo.getId());
    data.setOrder(xo.getOrder());
    data.setName(xo.getName());
    data.setProtocol(xo.getProtocol() != null ? xo.getProtocol() : "LDAP");
    data.setUseTrustStore(xo.getUseTrustStore() != null && xo.getUseTrustStore());
    data.setHost(xo.getHost());
    data.setPort(xo.getPort() != null ? xo.getPort() : 389);
    data.setSearchBase(xo.getSearchBase());
    data.setAuthScheme(xo.getAuthScheme() != null ? xo.getAuthScheme() : "NONE");
    data.setAuthRealm(xo.getAuthRealm());
    data.setAuthUsername(xo.getAuthUsername());
    data.setAuthPassword(xo.getAuthPassword());
    data.setConnectionTimeoutSeconds(xo.getConnectionTimeoutSeconds() != null ? xo.getConnectionTimeoutSeconds() : 30);
    data.setConnectionRetryDelaySeconds(xo.getConnectionRetryDelaySeconds() != null ? xo.getConnectionRetryDelaySeconds() : 30);
    data.setMaxIncidentsCount(xo.getMaxIncidentsCount() != null ? xo.getMaxIncidentsCount() : 30);
    data.setUserBaseDn(xo.getUserBaseDn());
    data.setUserSubtree(xo.getUserSubtree() == null || xo.getUserSubtree());
    data.setUserObjectClass(xo.getUserObjectClass());
    data.setUserLdapFilter(xo.getUserLdapFilter());
    data.setUserIdAttribute(xo.getUserIdAttribute());
    data.setUserRealNameAttribute(xo.getUserRealNameAttribute());
    data.setUserEmailAddressAttribute(xo.getUserEmailAddressAttribute());
    data.setUserPasswordAttribute(xo.getUserPasswordAttribute());
    data.setLdapGroupsAsRoles(xo.getLdapGroupsAsRoles() != null && xo.getLdapGroupsAsRoles());
    data.setGroupType(xo.getGroupType() != null ? xo.getGroupType() : "DYNAMIC");
    data.setGroupBaseDn(xo.getGroupBaseDn());
    data.setGroupSubtree(xo.getGroupSubtree() == null || xo.getGroupSubtree());
    data.setGroupObjectClass(xo.getGroupObjectClass());
    data.setGroupIdAttribute(xo.getGroupIdAttribute());
    data.setGroupMemberAttribute(xo.getGroupMemberAttribute());
    data.setGroupMemberFormat(xo.getGroupMemberFormat());
    data.setUserMemberOfAttribute(xo.getUserMemberOfAttribute());
    return data;
  }

  private LdapServerXO toXO(final LdapConfiguration configuration) {
    LdapServerXO xo = new LdapServerXO();
    xo.setId(configuration.getId());
    xo.setOrder(configuration.getOrder());
    xo.setName(configuration.getName());
    xo.setProtocol(configuration.getProtocol());
    xo.setUseTrustStore(configuration.isUseTrustStore());
    xo.setHost(configuration.getHost());
    xo.setPort(configuration.getPort());
    xo.setSearchBase(configuration.getSearchBase());
    xo.setAuthScheme(configuration.getAuthScheme());
    xo.setAuthRealm(configuration.getAuthRealm());
    xo.setAuthUsername(configuration.getAuthUsername());
    xo.setAuthPassword(org.sonatype.nexus.internal.security.ldap.LdapConfigurationStoreImpl.PASSWORD_PLACEHOLDER);
    xo.setConnectionTimeoutSeconds(configuration.getConnectionTimeoutSeconds());
    xo.setConnectionRetryDelaySeconds(configuration.getConnectionRetryDelaySeconds());
    xo.setMaxIncidentsCount(configuration.getMaxIncidentsCount());
    xo.setUserBaseDn(configuration.getUserBaseDn());
    xo.setUserSubtree(configuration.isUserSubtree());
    xo.setUserObjectClass(configuration.getUserObjectClass());
    xo.setUserLdapFilter(configuration.getUserLdapFilter());
    xo.setUserIdAttribute(configuration.getUserIdAttribute());
    xo.setUserRealNameAttribute(configuration.getUserRealNameAttribute());
    xo.setUserEmailAddressAttribute(configuration.getUserEmailAddressAttribute());
    xo.setUserPasswordAttribute(configuration.getUserPasswordAttribute());
    xo.setLdapGroupsAsRoles(configuration.isLdapGroupsAsRoles());
    xo.setGroupType(configuration.getGroupType());
    xo.setGroupBaseDn(configuration.getGroupBaseDn());
    xo.setGroupSubtree(configuration.isGroupSubtree());
    xo.setGroupObjectClass(configuration.getGroupObjectClass());
    xo.setGroupIdAttribute(configuration.getGroupIdAttribute());
    xo.setGroupMemberAttribute(configuration.getGroupMemberAttribute());
    xo.setGroupMemberFormat(configuration.getGroupMemberFormat());
    xo.setUserMemberOfAttribute(configuration.getUserMemberOfAttribute());
    return xo;
  }
}
