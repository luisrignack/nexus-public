/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. All other trademarks
 * of their respective owners.
 */
package org.sonatype.nexus.coreui.ldap;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import org.sonatype.nexus.internal.security.ldap.rest.LdapServerXoMapper;
import org.sonatype.nexus.internal.security.ldap.rest.LdapServerXO;
import org.sonatype.nexus.internal.security.ldap.rest.LdapUserXO;
import org.sonatype.nexus.extdirect.DirectComponentSupport;
import org.sonatype.nexus.extdirect.model.Response;
import org.sonatype.nexus.extdirect.model.Responses;
import org.sonatype.nexus.security.ldap.LdapConfiguration;
import org.sonatype.nexus.security.ldap.LdapConfigurationStore;
import org.sonatype.nexus.security.ldap.LdapDirectory;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.softwarementors.extjs.djn.config.annotations.DirectAction;
import com.softwarementors.extjs.djn.config.annotations.DirectMethod;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static org.sonatype.nexus.internal.security.ldap.LdapConfigurationStoreImpl.PASSWORD_PLACEHOLDER;

/**
 * Ext.Direct provider {@code ldap_LdapServer}, backing the LDAP admin screens.
 *
 * <p>Coverage is limited to the calls the admin UI cannot express over REST: the schema templates
 * of the "User and Group" step and the three connection checks (verify connection, verify user
 * mapping, verify login) plus the cache flush. Everything else (list, read, create, update,
 * delete, change order) goes through {@code /service/rest/v1/security/ldap}.</p>
 *
 * @since 3.89
 */
@Component
@Singleton
@DirectAction(action = "ldap_LdapServer")
public class LdapServerComponent
    extends DirectComponentSupport
{
  private static final Logger log = LoggerFactory.getLogger(LdapServerComponent.class);

  private final LdapDirectory ldapDirectory;

  private final LdapConfigurationStore configurationStore;

  @Inject
  public LdapServerComponent(final LdapDirectory ldapDirectory, final LdapConfigurationStore configurationStore) {
    this.ldapDirectory = ldapDirectory;
    this.configurationStore = configurationStore;
  }

  /**
   * The schema templates offered when creating a server.
   */
  @DirectMethod
  @Timed
  @ExceptionMetered
  @RequiresPermissions("nexus:ldap:read")
  public List<LdapSchemaTemplateXO> readTemplates() {
    return LdapSchemaTemplateXO.templates();
  }

  /**
   * Checks that Nexus can reach the directory and bind with the configured service account.
   *
   * <p>The form sends the password placeholder back when the server was loaded from storage; in
   * that case the stored bind secret is used instead.</p>
   */
  @DirectMethod
  @Timed
  @ExceptionMetered
  @RequiresAuthentication
  @RequiresPermissions("nexus:ldap:read")
  public Response<?> verifyConnection(final LdapServerXO xo) {
    try {
      ldapDirectory.verifyConnection(configurationOf(xo));
      return Responses.success();
    }
    catch (Exception e) {
      log.debug("LDAP connection verification failed", e);
      return Responses.error(message(e));
    }
  }

  /**
   * Resolves the users the configured mapping would produce, without logging anyone in.
   */
  @DirectMethod
  @Timed
  @ExceptionMetered
  @RequiresAuthentication
  @RequiresPermissions("nexus:ldap:read")
  public Response<?> verifyUserMapping(final LdapServerXO xo) {
    try {
      List<LdapUserXO> users = new ArrayList<>();
      for (LdapDirectory.LdapUserInfo userInfo : ldapDirectory.verifyUserMapping(configurationOf(xo))) {
        users.add(LdapServerXoMapper.toUserXO(userInfo));
      }
      return Responses.success(users);
    }
    catch (Exception e) {
      log.debug("LDAP user mapping verification failed", e);
      return Responses.error(message(e));
    }
  }

  /**
   * Binds to the directory as the given user. The credentials arrive base64 encoded (the UI uses
   * {@code btoa}) and are sent separately from the server configuration.
   */
  @DirectMethod
  @Timed
  @ExceptionMetered
  @RequiresAuthentication
  @RequiresPermissions("nexus:ldap:read")
  public Response<?> verifyLogin(final LdapServerXO xo, final String encodedUsername, final String encodedPassword) {
    String username = decode(encodedUsername);
    String password = decode(encodedPassword);
    if (username == null || password == null) {
      return Responses.error("Username and password are required");
    }

    try {
      ldapDirectory.authenticate(configurationOf(xo), username, password.toCharArray());
      return Responses.success();
    }
    catch (Exception e) {
      log.debug("LDAP login verification failed", e);
      return Responses.error("Login verification failed: " + message(e));
    }
  }

  /**
   * Flushes the LDAP authentication cache.
   *
   * <p>The realm in this build authenticates with {@code setCachingEnabled(false)} and
   * {@link LdapDirectory} keeps no state of its own, so there is nothing left to evict: the call
   * reports success to keep the admin UI flow (and any future caching) in one place.</p>
   */
  @DirectMethod
  @Timed
  @ExceptionMetered
  @RequiresAuthentication
  @RequiresPermissions("nexus:ldap:delete")
  public Response<?> clearCache() {
    log.debug("LDAP authentication cache flush requested; caching is disabled for this realm");
    return Responses.success();
  }

  /**
   * Builds the configuration to verify. When the form only carries the password placeholder (the
   * server was loaded from storage and no new secret was typed in), the stored bind credentials
   * are substituted, exactly like the store does when saving.
   */
  private LdapConfiguration configurationOf(final LdapServerXO xo) {
    if (usesStoredSecret(xo.getAuthPassword())) {
      stored(xo).ifPresent(stored -> {
        xo.setAuthUsername(stored.getAuthUsername());
        xo.setAuthPassword(stored.getAuthPassword());
      });
    }
    return LdapServerXoMapper.toConfiguration(xo);
  }

  private static boolean usesStoredSecret(@Nullable final String authPassword) {
    return authPassword == null || authPassword.isBlank() || PASSWORD_PLACEHOLDER.equals(authPassword);
  }

  private Optional<LdapConfiguration> stored(final LdapServerXO xo) {
    return configurationStore.list().stream()
        .filter(c -> (xo.getId() != null && xo.getId().equals(c.getId()))
            || (xo.getName() != null && xo.getName().equals(c.getName())))
        .findFirst();
  }

  /**
   * Inverse of the browser's {@code btoa}: every byte maps back to the same character code.
   */
  @Nullable
  private static String decode(@Nullable final String encoded) {
    if (encoded == null || encoded.isEmpty()) {
      return null;
    }
    try {
      return new String(Base64.getDecoder().decode(encoded), StandardCharsets.ISO_8859_1);
    }
    catch (IllegalArgumentException e) {
      log.debug("Credentials are not valid base64", e);
      return null;
    }
  }

  private static String message(final Exception e) {
    return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
  }
}
