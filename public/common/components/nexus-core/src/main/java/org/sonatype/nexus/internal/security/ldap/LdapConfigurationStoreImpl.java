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
package org.sonatype.nexus.internal.security.ldap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.sonatype.nexus.capability.CapabilityIdentity;
import org.sonatype.nexus.capability.CapabilityReference;
import org.sonatype.nexus.capability.CapabilityReferenceFilterBuilder;
import org.sonatype.nexus.capability.CapabilityRegistry;
import org.sonatype.nexus.crypto.secrets.Secret;
import org.sonatype.nexus.crypto.secrets.SecretsService;
import org.sonatype.nexus.security.authc.ldap.LdapRealm;
import org.sonatype.nexus.security.ldap.LdapConfiguration;
import org.sonatype.nexus.security.ldap.LdapConfigurationStore;
import org.sonatype.nexus.security.realm.RealmManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import static com.google.common.base.Preconditions.checkNotNull;

import static org.sonatype.nexus.internal.security.ldap.LdapCapabilityProperties.AUTH_PASSWORD;
import static org.sonatype.nexus.internal.security.ldap.LdapCapabilityProperties.AUTH_REALM;
import static org.sonatype.nexus.internal.security.ldap.LdapCapabilityProperties.AUTH_SCHEME;
import static org.sonatype.nexus.internal.security.ldap.LdapCapabilityProperties.AUTH_USERNAME;
import static org.sonatype.nexus.internal.security.ldap.LdapCapabilityProperties.CONNECTION_RETRY_DELAY_SECONDS;
import static org.sonatype.nexus.internal.security.ldap.LdapCapabilityProperties.CONNECTION_TIMEOUT_SECONDS;
import static org.sonatype.nexus.internal.security.ldap.LdapCapabilityProperties.GROUP_BASE_DN;
import static org.sonatype.nexus.internal.security.ldap.LdapCapabilityProperties.GROUP_ID_ATTRIBUTE;
import static org.sonatype.nexus.internal.security.ldap.LdapCapabilityProperties.GROUP_MEMBER_ATTRIBUTE;
import static org.sonatype.nexus.internal.security.ldap.LdapCapabilityProperties.GROUP_MEMBER_FORMAT;
import static org.sonatype.nexus.internal.security.ldap.LdapCapabilityProperties.GROUP_OBJECT_CLASS;
import static org.sonatype.nexus.internal.security.ldap.LdapCapabilityProperties.GROUP_SUBTREE;
import static org.sonatype.nexus.internal.security.ldap.LdapCapabilityProperties.GROUP_TYPE;
import static org.sonatype.nexus.internal.security.ldap.LdapCapabilityProperties.HOST;
import static org.sonatype.nexus.internal.security.ldap.LdapCapabilityProperties.LDAP_GROUPS_AS_ROLES;
import static org.sonatype.nexus.internal.security.ldap.LdapCapabilityProperties.MAX_INCIDENTS_COUNT;
import static org.sonatype.nexus.internal.security.ldap.LdapCapabilityProperties.ORDER;
import static org.sonatype.nexus.internal.security.ldap.LdapCapabilityProperties.PORT;
import static org.sonatype.nexus.internal.security.ldap.LdapCapabilityProperties.PROTOCOL;
import static org.sonatype.nexus.internal.security.ldap.LdapCapabilityProperties.SEARCH_BASE;
import static org.sonatype.nexus.internal.security.ldap.LdapCapabilityProperties.SERVER_NAME;
import static org.sonatype.nexus.internal.security.ldap.LdapCapabilityProperties.USE_TRUST_STORE;
import static org.sonatype.nexus.internal.security.ldap.LdapCapabilityProperties.USER_BASE_DN;
import static org.sonatype.nexus.internal.security.ldap.LdapCapabilityProperties.USER_EMAIL_ADDRESS_ATTRIBUTE;
import static org.sonatype.nexus.internal.security.ldap.LdapCapabilityProperties.USER_ID_ATTRIBUTE;
import static org.sonatype.nexus.internal.security.ldap.LdapCapabilityProperties.USER_LDAP_FILTER;
import static org.sonatype.nexus.internal.security.ldap.LdapCapabilityProperties.USER_MEMBER_OF_ATTRIBUTE;
import static org.sonatype.nexus.internal.security.ldap.LdapCapabilityProperties.USER_OBJECT_CLASS;
import static org.sonatype.nexus.internal.security.ldap.LdapCapabilityProperties.USER_PASSWORD_ATTRIBUTE;
import static org.sonatype.nexus.internal.security.ldap.LdapCapabilityProperties.USER_REAL_NAME_ATTRIBUTE;
import static org.sonatype.nexus.internal.security.ldap.LdapCapabilityProperties.USER_SUBTREE;

/**
 * {@link LdapConfigurationStore} backed by the {@code ldap} capability: every server configuration
 * is one capability instance, and the bind password is the only secret piece (encrypted through
 * {@link SecretsService}, mirroring the OIDC configuration store).
 *
 * @since 3.89
 */
@Component
@Singleton
public class LdapConfigurationStoreImpl
    implements LdapConfigurationStore
{
  private static final Logger log = LoggerFactory.getLogger(LdapConfigurationStoreImpl.class);

  private static final String SECRET_PURPOSE = "ldap";

  /**
   * Sentinel stored in the UI-facing bind password field so a round-trip through the form does not
   * overwrite the persisted secret with a blank value.
   */
  public static final String PASSWORD_PLACEHOLDER = "#~NXRM~PLACEHOLDER~LDAP~SECRET~#";

  private final CapabilityRegistry capabilityRegistry;

  private final SecretsService secretsService;

  private final RealmManager realmManager;

  @Inject
  public LdapConfigurationStoreImpl(
      final CapabilityRegistry capabilityRegistry,
      final SecretsService secretsService,
      @Lazy final RealmManager realmManager)
  {
    this.capabilityRegistry = checkNotNull(capabilityRegistry);
    this.secretsService = checkNotNull(secretsService);
    this.realmManager = realmManager;
  }

  @Override
  public List<LdapConfiguration> list() {
    List<LdapConfiguration> servers = new ArrayList<>();
    for (CapabilityReference reference : references()) {
      servers.add(toConfiguration(reference));
    }
    servers.sort(java.util.Comparator.comparingInt(LdapConfiguration::getOrder));
    return servers;
  }

  @Override
  public List<LdapConfiguration> listActive() {
    List<LdapConfiguration> servers = new ArrayList<>();
    for (CapabilityReference reference : references()) {
      if (reference.context().isActive()) {
        servers.add(toConfiguration(reference));
      }
    }
    servers.sort(java.util.Comparator.comparingInt(LdapConfiguration::getOrder));
    return servers;
  }

  @Override
  public LdapConfiguration create(final LdapConfiguration configuration) {
    checkNotNull(configuration);
    int order = nextOrder();
    Map<String, String> properties = toProperties(configuration, order, null);
    CapabilityReference reference = capabilityRegistry.add(
        LdapCapability.TYPE,
        true,
        notes(configuration.getName()),
        properties);
    log.info("Created LDAP server '{}' (order {})", configuration.getName(), order);
    return toConfiguration(reference);
  }

  @Override
  public void update(final LdapConfiguration configuration) {
    checkNotNull(configuration);
    CapabilityReference existing = reference(configuration.getId())
        .orElseThrow(() -> new IllegalArgumentException("LDAP server '" + configuration.getId() + "' not found"));
    Map<String, String> properties = toProperties(
        configuration,
        configuration.getOrder(),
        existing.context().properties().get(AUTH_PASSWORD));
    capabilityRegistry.update(
        existing.context().id(),
        existing.context().isEnabled(),
        notes(configuration.getName()),
        properties);
    log.info("Updated LDAP server '{}'", configuration.getName());
  }

  @Override
  public boolean delete(final String id) {
    CapabilityReference reference = reference(id)
        .orElseThrow(() -> new IllegalArgumentException("LDAP server '" + id + "' not found"));
    capabilityRegistry.remove(reference.context().id());
    log.info("Deleted LDAP server '{}'", reference.context().properties().get(SERVER_NAME));

    if (activeCount() == 0) {
      try {
        realmManager.disableRealm(LdapRealm.REALM_NAME);
        log.info("Disabled the {} realm, no active LDAP servers are left", LdapRealm.REALM_NAME);
      }
      catch (Exception e) {
        log.debug("Unable to disable the LDAP realm", e);
      }
      return true;
    }
    return false;
  }

  @Override
  public void changeOrder(final List<String> ids) {
    checkNotNull(ids);
    Map<String, CapabilityReference> byId = new HashMap<>();
    for (CapabilityReference reference : references()) {
      byId.put(reference.context().id().toString(), reference);
    }
    int order = 1;
    for (String id : ids) {
      CapabilityReference reference = byId.get(id);
      if (reference == null) {
        continue;
      }
      Map<String, String> properties = new HashMap<>(reference.context().properties());
      properties.put(ORDER, Integer.toString(order++));
      capabilityRegistry.update(
          reference.context().id(),
          reference.context().isEnabled(),
          reference.context().notes(),
          properties);
    }
    log.info("Reordered {} LDAP servers", ids.size());
  }

  @Override
  public int activeCount() {
    return (int) references().stream().filter(r -> r.context().isActive()).count();
  }

  // --- capability plumbing ---

  private List<CapabilityReference> references() {
    return capabilityRegistry.get(
            CapabilityReferenceFilterBuilder.capabilities()
                .withType(LdapCapability.TYPE)
                .includeNotExposed())
        .stream()
        .map(CapabilityReference.class::cast)
        .toList();
  }

  private Optional<CapabilityReference> reference(final String id) {
    try {
      CapabilityIdentity identity = new CapabilityIdentity(id);
      return Optional.ofNullable(capabilityRegistry.get(identity));
    }
    catch (Exception e) {
      return Optional.empty();
    }
  }

  private int nextOrder() {
    // a new server sorts after every existing one; the caller may always reorder explicitly afterwards
    return references().stream()
        .mapToInt(r -> parseInt(r.context().properties().get(ORDER), 0))
        .filter(v -> v < MAX_REASONABLE_ORDER)
        .max()
        .orElse(0) + 1;
  }

  private static final int MAX_REASONABLE_ORDER = 1_000_000;

  private static String notes(final String name) {
    return "LDAP server '" + name + "'";
  }

  // --- property conversion ---

  private LdapConfiguration toConfiguration(final CapabilityReference reference) {
    Map<String, String> properties = reference.context().properties();

    LdapConfigurationData data = new LdapConfigurationData();
    data.setId(String.valueOf(reference.context().id()));
    data.setOrder(parseInt(properties.get(ORDER), MAX_REASONABLE_ORDER));
    data.setName(properties.get(SERVER_NAME));
    data.setProtocol(orDefault(properties.get(PROTOCOL), "LDAP"));
    data.setUseTrustStore(parseBoolean(properties.get(USE_TRUST_STORE), false));
    data.setHost(properties.get(HOST));
    data.setPort(parseInt(properties.get(PORT), 389));
    data.setSearchBase(properties.get(SEARCH_BASE));
    data.setAuthScheme(orDefault(properties.get(AUTH_SCHEME), NONE));
    data.setAuthRealm(properties.get(AUTH_REALM));
    data.setAuthUsername(properties.get(AUTH_USERNAME));
    data.setAuthPassword(decryptSecret(properties.get(AUTH_PASSWORD)));
    data.setConnectionTimeoutSeconds(parseInt(properties.get(CONNECTION_TIMEOUT_SECONDS), 30));
    data.setConnectionRetryDelaySeconds(parseInt(properties.get(CONNECTION_RETRY_DELAY_SECONDS), 30));
    data.setMaxIncidentsCount(parseInt(properties.get(MAX_INCIDENTS_COUNT), 30));
    data.setUserBaseDn(properties.get(USER_BASE_DN));
    data.setUserSubtree(parseBoolean(properties.get(USER_SUBTREE), true));
    data.setUserObjectClass(properties.get(USER_OBJECT_CLASS));
    data.setUserLdapFilter(properties.get(USER_LDAP_FILTER));
    data.setUserIdAttribute(properties.get(USER_ID_ATTRIBUTE));
    data.setUserRealNameAttribute(properties.get(USER_REAL_NAME_ATTRIBUTE));
    data.setUserEmailAddressAttribute(properties.get(USER_EMAIL_ADDRESS_ATTRIBUTE));
    data.setUserPasswordAttribute(properties.get(USER_PASSWORD_ATTRIBUTE));
    data.setLdapGroupsAsRoles(parseBoolean(properties.get(LDAP_GROUPS_AS_ROLES), false));
    data.setGroupType(orDefault(properties.get(GROUP_TYPE), "DYNAMIC"));
    data.setGroupBaseDn(properties.get(GROUP_BASE_DN));
    data.setGroupSubtree(parseBoolean(properties.get(GROUP_SUBTREE), true));
    data.setGroupObjectClass(properties.get(GROUP_OBJECT_CLASS));
    data.setGroupIdAttribute(properties.get(GROUP_ID_ATTRIBUTE));
    data.setGroupMemberAttribute(properties.get(GROUP_MEMBER_ATTRIBUTE));
    data.setGroupMemberFormat(properties.get(GROUP_MEMBER_FORMAT));
    data.setUserMemberOfAttribute(properties.get(USER_MEMBER_OF_ATTRIBUTE));
    return data;
  }

  private Map<String, String> toProperties(
      final LdapConfiguration configuration,
      final int order,
      @Nullable final String existingSecretToken)
  {
    Map<String, String> properties = new HashMap<>();
    putIfNotNull(properties, SERVER_NAME, configuration.getName());
    properties.put(PROTOCOL, configuration.getProtocol());
    properties.put(USE_TRUST_STORE, Boolean.toString(configuration.isUseTrustStore()));
    putIfNotNull(properties, HOST, configuration.getHost());
    properties.put(PORT, Integer.toString(configuration.getPort()));
    putIfNotNull(properties, SEARCH_BASE, configuration.getSearchBase());
    properties.put(AUTH_SCHEME, configuration.getAuthScheme());
    putIfNotNull(properties, AUTH_REALM, configuration.getAuthRealm());
    putIfNotNull(properties, AUTH_USERNAME, configuration.getAuthUsername());
    properties.put(CONNECTION_TIMEOUT_SECONDS, Integer.toString(configuration.getConnectionTimeoutSeconds()));
    properties.put(CONNECTION_RETRY_DELAY_SECONDS, Integer.toString(configuration.getConnectionRetryDelaySeconds()));
    properties.put(MAX_INCIDENTS_COUNT, Integer.toString(configuration.getMaxIncidentsCount()));
    putIfNotNull(properties, USER_BASE_DN, configuration.getUserBaseDn());
    properties.put(USER_SUBTREE, Boolean.toString(configuration.isUserSubtree()));
    putIfNotNull(properties, USER_OBJECT_CLASS, configuration.getUserObjectClass());
    putIfNotNull(properties, USER_LDAP_FILTER, configuration.getUserLdapFilter());
    putIfNotNull(properties, USER_ID_ATTRIBUTE, configuration.getUserIdAttribute());
    putIfNotNull(properties, USER_REAL_NAME_ATTRIBUTE, configuration.getUserRealNameAttribute());
    putIfNotNull(properties, USER_EMAIL_ADDRESS_ATTRIBUTE, configuration.getUserEmailAddressAttribute());
    putIfNotNull(properties, USER_PASSWORD_ATTRIBUTE, configuration.getUserPasswordAttribute());
    properties.put(LDAP_GROUPS_AS_ROLES, Boolean.toString(configuration.isLdapGroupsAsRoles()));
    properties.put(GROUP_TYPE, configuration.getGroupType());
    putIfNotNull(properties, GROUP_BASE_DN, configuration.getGroupBaseDn());
    properties.put(GROUP_SUBTREE, Boolean.toString(configuration.isGroupSubtree()));
    putIfNotNull(properties, GROUP_OBJECT_CLASS, configuration.getGroupObjectClass());
    putIfNotNull(properties, GROUP_ID_ATTRIBUTE, configuration.getGroupIdAttribute());
    putIfNotNull(properties, GROUP_MEMBER_ATTRIBUTE, configuration.getGroupMemberAttribute());
    putIfNotNull(properties, GROUP_MEMBER_FORMAT, configuration.getGroupMemberFormat());
    putIfNotNull(properties, USER_MEMBER_OF_ATTRIBUTE, configuration.getUserMemberOfAttribute());
    properties.put(ORDER, Integer.toString(order));

    String secretToken = resolveSecretToken(existingSecretToken, configuration.getAuthPassword());
    if (secretToken != null) {
      properties.put(AUTH_PASSWORD, secretToken);
    }
    return properties;
  }

  /**
   * Decides which secret token to persist. A null, blank or placeholder incoming secret keeps the
   * token already stored; any other value is treated as a new plain secret and encrypted.
   */
  @Nullable
  private String resolveSecretToken(@Nullable final String existingToken, @Nullable final String incomingSecret) {
    if (incomingSecret == null
        || incomingSecret.isBlank()
        || PASSWORD_PLACEHOLDER.equals(incomingSecret)
        || looksLikeSecretToken(incomingSecret)) {
      return existingToken;
    }

    try {
      Secret encrypted = secretsService.encrypt(SECRET_PURPOSE, incomingSecret.toCharArray(), null);
      // Drop the previous token so the secrets store does not accumulate orphans.
      if (existingToken != null && !existingToken.isBlank()) {
        try {
          secretsService.remove(secretsService.from(existingToken));
        }
        catch (Exception e) {
          log.debug("Unable to remove previous LDAP bind secret", e);
        }
      }
      return encrypted.getId();
    }
    catch (Exception e) {
      throw new IllegalStateException("Unable to encrypt the LDAP bind password", e);
    }
  }

  /**
   * A value that already looks like an opaque secret token (created by a previous save) should not
   * be re-encrypted as a plain password.
   */
  private static boolean looksLikeSecretToken(final String value) {
    return value.length() > 40 && !value.contains(" ");
  }

  /**
   * Decrypts the stored secret token back to its plain value. A missing or undecryptable token
   * yields {@code null} so the rest of the configuration still loads.
   */
  @Nullable
  private String decryptSecret(@Nullable final String secretToken) {
    if (secretToken == null || secretToken.isBlank()) {
      return null;
    }
    try {
      Secret secret = secretsService.from(secretToken);
      char[] plain = secret.decrypt();
      return plain != null ? new String(plain) : null;
    }
    catch (Exception e) {
      log.warn("Unable to decrypt the LDAP bind password", e);
      return null;
    }
  }

  private static void putIfNotNull(final Map<String, String> properties, final String key, @Nullable final String value) {
    if (value != null) {
      properties.put(key, value);
    }
  }

  private static int parseInt(@Nullable final String value, final int fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    try {
      return Integer.parseInt(value.trim());
    }
    catch (NumberFormatException e) {
      return fallback;
    }
  }

  private static boolean parseBoolean(@Nullable final String value, final boolean fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    return Boolean.parseBoolean(value.trim());
  }

  private static String orDefault(@Nullable final String value, final String fallback) {
    return value != null && !value.isBlank() ? value : fallback;
  }

  private static final String NONE = "NONE";
}
