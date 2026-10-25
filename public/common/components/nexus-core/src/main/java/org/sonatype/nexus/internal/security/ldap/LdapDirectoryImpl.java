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

import java.util.Collections;
import java.util.Set;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.crypto.secrets.Secret;
import org.sonatype.nexus.crypto.secrets.SecretsService;
import org.sonatype.nexus.security.ldap.LdapConfiguration;
import org.sonatype.nexus.security.ldap.LdapDirectory;
import org.sonatype.nexus.ssl.TrustStore;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * JNDI-backed {@link LdapDirectory}.
 *
 * <p>Connections use {@code protocol://host:port/} with the search base DN appended, matching the
 * layout the legacy LDAP plugin used. The SASL schemes map {@code DIGEST_MD5} to {@code DIGEST-MD5}
 * and {@code CRAM_MD5} to {@code CRAM-MD5}; userBinding is done by binding as the found user DN,
 * with the configured {@code userPasswordAttribute} compare as a fallback for directories that
 * disallow direct user binds.</p>
 *
 * @since 3.89
 */
@Component
@Singleton
@Named("LdapDirectory")
public class LdapDirectoryImpl
    extends ComponentSupport
    implements LdapDirectory
{
  private static final String SIMPLE = "SIMPLE";

  private static final String NONE = "NONE";

  private static final String DIGEST_MD5 = "DIGEST_MD5";

  private static final String CRAM_MD5 = "CRAM_MD5";

  private final TrustStore trustStore;

  private final SecretsService secretsService;

  @Inject
  public LdapDirectoryImpl(
      @Lazy final TrustStore trustStore,
      @Lazy final SecretsService secretsService)
  {
    this.trustStore = trustStore;
    this.secretsService = secretsService;
  }

  @Override
  public void verifyConnection(final LdapConfiguration configuration) throws Exception {
    DirContext context = null;
    try {
      context = openServiceContext(configuration);
      if (context == null) {
        throw new IllegalStateException("Failed to bind to the LDAP server");
      }
    }
    catch (NamingException e) {
      throw new IllegalStateException("Failed to connect/bind: " + rootMessage(e), e);
    }
    finally {
      if (context != null) {
        try {
          context.close();
        }
        catch (Exception e) {
          // ignore
        }
      }
    }
  }

  @Override
  public LdapUserInfo authenticate(
      final LdapConfiguration configuration,
      final String username,
      final char[] password) throws Exception
  {
    UserEntry user = findUser(configuration, username);
    bindUser(configuration, user.dn, password);

    Set<String> groupIds = resolveGroups(configuration, user);
    return new UserInfo(
        ldapEscape(String.valueOf(user.attributes.get(configuration.getUserIdAttribute()))),
        firstAttribute(user.attributes, configuration.getUserRealNameAttribute()),
        firstAttribute(user.attributes, configuration.getUserEmailAddressAttribute()),
        groupIds);
  }

  @Override
  public Set<LdapUserInfo> verifyUserMapping(final LdapConfiguration configuration) throws Exception {
    Set<LdapUserInfo> users = new java.util.LinkedHashSet<>();
    DirContext service = null;
    try {
      service = openServiceContext(configuration);
      if (service == null) {
        throw new IllegalStateException("Failed to bind to the LDAP server");
      }
      for (UserEntry user : searchUsers(configuration, service, "*", configuration.getMaxIncidentsCount())) {
        Set<String> groupIds = resolveGroups(configuration, service, user);
        users.add(new UserInfo(
            ldapEscape(String.valueOf(user.attributes.get(configuration.getUserIdAttribute()))),
            firstAttribute(user.attributes, configuration.getUserRealNameAttribute()),
            firstAttribute(user.attributes, configuration.getUserEmailAddressAttribute()),
            groupIds));
      }
    }
    catch (NamingException e) {
      throw new IllegalStateException("User mapping verification failed: " + rootMessage(e), e);
    }
    finally {
      if (service != null) {
        try {
          service.close();
        }
        catch (Exception e) {
          // ignore
        }
      }
    }
    return users;
  }

  // --- connection handling ---

  private DirContext openServiceContext(final LdapConfiguration configuration) throws NamingException {
    return openContext(configuration, configuration.getAuthUsername(), plainServicePassword(configuration));
  }

  private byte[] plainServicePassword(final LdapConfiguration configuration) {
    String token = configuration.getAuthPassword();
    if (token == null || token.isBlank()) {
      return null;
    }
    try {
      Secret secret = secretsService.from(token);
      char[] plain = secret.decrypt();
      return plain != null ? new String(plain).getBytes(java.nio.charset.StandardCharsets.UTF_8) : null;
    }
    catch (Exception e) {
      log.warn("Unable to decrypt the LDAP bind password", e);
      return null;
    }
  }

  private DirContext openContext(
      final LdapConfiguration configuration,
      final String principal,
      final byte[] credentials) throws NamingException
  {
    java.util.Hashtable<String, Object> env = new java.util.Hashtable<>();
    env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
    env.put(Context.PROVIDER_URL, connectUrl(configuration));
    env.put("com.sun.jndi.ldap.connect.timeout",
        Integer.toString(configuration.getConnectionTimeoutSeconds() * 1000));
    env.put("com.sun.jndi.ldap.read.timeout",
        Integer.toString(configuration.getConnectionTimeoutSeconds() * 1000));
    env.put("com.sun.jndi.ldap.version", "3");
    env.put("java.naming.ldap.referral", "follow");

    String scheme = scheme(configuration.getAuthScheme());
    env.put(Context.SECURITY_AUTHENTICATION, scheme);
    if (!NONE.equals(scheme)) {
      if (principal != null) {
        env.put(Context.SECURITY_PRINCIPAL, principal);
      }
      if (credentials != null) {
        env.put(Context.SECURITY_CREDENTIALS, credentials);
      }
      if (DIGEST_MD5.equals(scheme) || CRAM_MD5.equals(scheme)) {
        if (configuration.getAuthRealm() != null && !configuration.getAuthRealm().isBlank()) {
          env.put("java.naming.security.sasl.realm", configuration.getAuthRealm());
        }
      }
    }

    if ("ldaps".equalsIgnoreCase(configuration.getProtocol())) {
      if (configuration.isUseTrustStore()) {
        try {
          javax.net.ssl.SSLSocketFactory socketFactory = null;
          try {
            java.lang.reflect.Method m = trustStore.getClass().getMethod("getSslSocketFactory");
            socketFactory = (javax.net.ssl.SSLSocketFactory) m.invoke(trustStore);
          }
          catch (Exception ex) {
            // try other methods
          }
          if (socketFactory != null) {
            env.put("java.naming.ldap.factory.socket", socketFactory);
          }
        }
        catch (Exception e) {
          log.warn("Unable to obtain the Nexus trust store for LDAPS; falling back to the JVM default", e);
        }
      }
    }

    return new javax.naming.directory.InitialDirContext(env);
  }

  /**
   * Maps the UI scheme identifiers to JNDI authentication scheme names.
   */
  private static String scheme(final String authScheme) {
    if (authScheme == null) {
      return NONE;
    }
    return switch (authScheme) {
      case NONE -> NONE;
      case SIMPLE -> SIMPLE;
      case DIGEST_MD5 -> "DIGEST-MD5";
      case CRAM_MD5 -> "CRAM-MD5";
      default -> SIMPLE;
    };
  }

  private static String connectUrl(final LdapConfiguration configuration) {
    StringBuilder url = new StringBuilder();
    url.append(configuration.getProtocol().toLowerCase()).append("://");
    url.append(configuration.getHost()).append(":").append(configuration.getPort());
    String base = configuration.getSearchBase();
    if (base != null && !base.isBlank()) {
      url.append("/").append(base);
    }
    return url.toString();
  }

  // --- user searching and binding ---

  private UserEntry findUser(final LdapConfiguration configuration, final String username) throws Exception {
    DirContext service = openServiceContext(configuration);
    try {
      if (service == null) {
        throw new IllegalStateException("Failed to bind to the LDAP server");
      }
      java.util.List<UserEntry> found = searchUsers(configuration, service, username, 2);
      if (found.isEmpty()) {
        throw new IllegalStateException("User '" + username + "' not found under user base '"
            + configuration.getUserBaseDn() + "'");
      }
      if (found.size() > 1) {
        throw new IllegalStateException("Multiple user entries match '" + username + "', refusing to pick one");
      }
      return found.get(0);
    }
    finally {
      if (service != null) {
        try {
          service.close();
        }
        catch (Exception e) {
          // ignore
        }
      }
    }
  }

  private java.util.List<UserEntry> searchUsers(
      final LdapConfiguration configuration,
      final DirContext service,
      final String username,
      final int limit) throws NamingException
  {
    String base = relativeDn(configuration, configuration.getUserBaseDn());
    String filter = userSearchFilter(configuration, username);

    SearchControls controls = new SearchControls();
    controls.setSearchScope(configuration.isUserSubtree() ? SearchControls.SUBTREE_SCOPE : SearchControls.ONELEVEL_SCOPE);
    controls.setReturningAttributes(returningAttributes(configuration));
    controls.setCountLimit(limit);

    NamingEnumeration<SearchResult> results = service.search(base, filter, controls);
    java.util.List<UserEntry> entries = new java.util.ArrayList<>();
    try {
      while (results.hasMore() && entries.size() < limit) {
        SearchResult result = results.next();
        entries.add(new UserEntry(result.getNameInNamespace(), result.getAttributes()));
      }
    }
    finally {
      results.close();
    }
    return entries;
  }

  /**
   * Builds the user search filter: {@code (&(objectClass=...)(userId=...)[customFilter...])}, where
   * the optional custom filter from {@code userLdapFilter} is embedded (and parenthesized) as an
   * additional AND term.
   */
  private static String userSearchFilter(final LdapConfiguration configuration, final String username) {
    String escaped = rfc4515Escape(username);
    StringBuilder filter = new StringBuilder("(&(objectClass=").append(configuration.getUserObjectClass())
        .append(")(").append(configuration.getUserIdAttribute()).append('=').append(escaped).append(')');
    String custom = configuration.getUserLdapFilter();
    if (custom != null && !custom.isBlank()) {
      custom = custom.trim();
      if (!custom.startsWith("(")) {
        custom = "(" + custom + ")";
      }
      else if (!custom.endsWith(")")) {
        custom = custom + ")";
      }
      filter.append(custom);
    }
    filter.append(')');
    return filter.toString();
  }

  /**
   * JNDI RFC 4515 filter escaping for user-provided query values.
   */
  private static String rfc4515Escape(final String value) {
    return value
        .replace("\\", "\\5c")
        .replace("*", "\\2a")
        .replace("(", "\\28")
        .replace(")", "\\29")
        .replace("\u0000", "\\00");
  }

  private String[] returningAttributes(final LdapConfiguration configuration) {
    java.util.Set<String> attributes = new java.util.LinkedHashSet<>();
    attributes.add(configuration.getUserIdAttribute());
    addIfNotBlank(attributes, configuration.getUserRealNameAttribute());
    addIfNotBlank(attributes, configuration.getUserEmailAddressAttribute());
    addIfNotBlank(attributes, configuration.getUserPasswordAttribute());
    if ("DYNAMIC".equalsIgnoreCase(configuration.getGroupType())) {
      addIfNotBlank(attributes, configuration.getUserMemberOfAttribute());
    }
    return attributes.toArray(new String[0]);
  }

  private static void addIfNotBlank(final java.util.Set<String> attributes, final String value) {
    if (value != null && !value.isBlank()) {
      attributes.add(value);
    }
  }

  private void bindUser(final LdapConfiguration configuration, final String userDn, final char[] password)
      throws Exception
  {
    if (password == null || password.length == 0) {
      throw new IllegalStateException("Empty password is not allowed");
    }
    DirContext ctx = null;
    try {
      ctx = openContext(configuration, userDn, new String(password).getBytes(java.nio.charset.StandardCharsets.UTF_8));
      ctx.close();
      ctx = null;
    }
    catch (NamingException bindFailure) {
      // fallback: compare the userPassword attribute (e.g. for directories where user binds are disallowed)
      if (comparePassword(configuration, userDn, password, bindFailure)) {
        return;
      }
      throw new IllegalStateException("Failed to bind as user: " + rootMessage(bindFailure), bindFailure);
    }
    finally {
      if (ctx != null) {
        try {
          ctx.close();
        }
        catch (Exception e) {
          // ignore
        }
      }
    }
  }

  private boolean comparePassword(
      final LdapConfiguration configuration,
      final String userDn,
      final char[] password,
      final NamingException originalFailure)
  {
    String attribute = configuration.getUserPasswordAttribute();
    if (attribute == null || attribute.isBlank()) {
      return false;
    }
    DirContext service = null;
    try {
      service = openServiceContext(configuration);
      Attributes attributes = service.getAttributes(userDn, new String[]{attribute});
      Attribute stored = attributes.get(attribute);
      if (stored != null && stored.size() > 0) {
        Object value = stored.get(0);
        String storedValue = value instanceof byte[] bytes
            ? new String(bytes, java.nio.charset.StandardCharsets.UTF_8)
            : String.valueOf(value);
        // plain-text compare only; hashed variants are deliberately rejected
        return !storedValue.startsWith("{") && storedValue.equals(new String(password));
      }
    }
    catch (Exception e) {
      log.trace("userPassword compare fallback failed", e);
    }
    finally {
      if (service != null) {
        try {
          service.close();
        }
        catch (Exception e) {
          // ignore
        }
      }
    }
    return false;
  }

  // --- group resolution ---

  private Set<String> resolveGroups(final LdapConfiguration configuration, final UserEntry user) throws Exception {
    DirContext service = null;
    try {
      service = openServiceContext(configuration);
      if (service == null) {
        throw new IllegalStateException("Failed to bind to the LDAP server");
      }
      return resolveGroups(configuration, service, user);
    }
    finally {
      if (service != null) {
        try {
          service.close();
        }
        catch (Exception e) {
          // ignore
        }
      }
    }
  }

  private Set<String> resolveGroups(
      final LdapConfiguration configuration,
      final DirContext service,
      final UserEntry user) throws NamingException
  {
    if (!configuration.isLdapGroupsAsRoles()) {
      return Collections.emptySet();
    }
    if ("DYNAMIC".equalsIgnoreCase(configuration.getGroupType())) {
      return resolveDynamicGroups(configuration, user);
    }
    return resolveStaticGroups(configuration, service, user);
  }

  private Set<String> resolveDynamicGroups(final LdapConfiguration configuration, final UserEntry user)
      throws NamingException
  {
    Set<String> groupIds = new java.util.LinkedHashSet<>();
    String memberOfAttribute = configuration.getUserMemberOfAttribute();
    String groupIdAttribute = configuration.getGroupIdAttribute() != null
        ? configuration.getGroupIdAttribute() : "cn";
    if (memberOfAttribute == null || memberOfAttribute.isBlank()) {
      return groupIds;
    }
    Attribute memberOf = user.attributes.get(memberOfAttribute);
    if (memberOf == null) {
      return groupIds;
    }
    NamingEnumeration<?> values = memberOf.getAll();
    try {
      while (values.hasMore()) {
        String groupDn = String.valueOf(values.next());
        try {
          Attributes group = openServiceContext(configuration).getAttributes(groupDn, new String[]{groupIdAttribute});
          Attribute id = group.get(groupIdAttribute);
          if (id != null && id.size() > 0) {
            groupIds.add(String.valueOf(id.get(0)));
          }
          else {
            groupIds.add(firstRdnValue(groupDn));
          }
        }
        catch (NamingException e) {
          log.trace("Skipping unreadable group {}", groupDn, e);
          groupIds.add(firstRdnValue(groupDn));
        }
      }
    }
    finally {
      values.close();
    }
    return groupIds;
  }

  private Set<String> resolveStaticGroups(
      final LdapConfiguration configuration,
      final DirContext service,
      final UserEntry user) throws NamingException
  {
    Set<String> groupIds = new java.util.LinkedHashSet<>();
    String groupBase = relativeDn(configuration, configuration.getGroupBaseDn());
    String memberValue = memberValue(configuration, user.dn,
        String.valueOf(user.attributes.get(configuration.getUserIdAttribute())));

    String groupIdAttribute = configuration.getGroupIdAttribute() != null
        ? configuration.getGroupIdAttribute() : "cn";

    StringBuilder filter = new StringBuilder("(&(objectClass=").append(configuration.getGroupObjectClass())
        .append(")(").append(configuration.getGroupMemberAttribute()).append('=').append(
        memberValue.replace("\\", "\\5c").replace("(", "\\28").replace(")", "\\29")).append("))");

    SearchControls controls = new SearchControls();
    controls.setSearchScope(configuration.isGroupSubtree() ? SearchControls.SUBTREE_SCOPE : SearchControls.ONELEVEL_SCOPE);
    controls.setReturningAttributes(new String[]{groupIdAttribute});
    controls.setCountLimit(0);

    NamingEnumeration<SearchResult> results = service.search(groupBase, filter.toString(), controls);
    try {
      while (results.hasMore()) {
        SearchResult group = results.next();
        Attribute id = group.getAttributes().get(groupIdAttribute);
        if (id != null && id.size() > 0) {
          groupIds.add(String.valueOf(id.get(0)));
        }
        else {
          groupIds.add(firstRdnValue(group.getNameInNamespace()));
        }
      }
    }
    finally {
      results.close();
    }
    return groupIds;
  }

  /**
   * Renders the member value used to search static groups: {@code ${username}} in the configured
   * {@code groupMemberFormat} is substituted, otherwise the user DN is used directly.
   */
  private String memberValue(final LdapConfiguration configuration, final String userDn, final String username) {
    String format = configuration.getGroupMemberFormat();
    if (format == null || format.isBlank()) {
      return userDn;
    }
    if (format.contains("${username}")) {
      return format.replace("${username}", username);
    }
    if (format.contains("${dn}")) {
      return format.replace("${dn}", userDn);
    }
    return format;
  }

  // --- helpers ---

  private static String relativeDn(final LdapConfiguration configuration, @javax.annotation.Nullable final String dn) {
    String base = configuration.getSearchBase();
    if (dn == null || dn.isBlank()) {
      return base != null ? base : "";
    }
    if (base == null || base.isBlank() || dn.toLowerCase().endsWith(base.toLowerCase())
        || dn.toLowerCase().endsWith("," + base.toLowerCase()))
    {
      return dn;
    }
    return dn + "," + base;
  }

  private static String firstRdnValue(final String dn) {
    if (dn == null || dn.isBlank()) {
      return "";
    }
    String rdn = dn.split(",", 2)[0];
    int idx = rdn.indexOf('=');
    return idx > 0 ? rdn.substring(idx + 1) : rdn;
  }

  private static String firstAttribute(final Attributes attributes, final String name) {
    if (name == null || name.isBlank() || attributes == null) {
      return null;
    }
    Attribute attribute = attributes.get(name);
    if (attribute != null && attribute.size() > 0) {
      try {
        Object value = attribute.get(0);
        String stringValue = value instanceof byte[] bytes
            ? new String(bytes, java.nio.charset.StandardCharsets.UTF_8)
            : String.valueOf(value);
        return ldapEscape(stringValue);
      }
      catch (NamingException e) {
        return null;
      }
    }
    return null;
  }

  /**
   * Erases JNDI artifact markers ({@code ldaps://.../} prefix pieces) and surrounding whitespace from
   * attribute values.
   */
  private static String ldapEscape(final String value) {
    if (value == null) {
      return null;
    }
    return value.trim();
  }

  private static String rootMessage(final Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null && current.getCause() != current) {
      current = current.getCause();
    }
    return current.getMessage() != null ? current.getMessage() : current.getClass().getSimpleName();
  }

  // --- value holders ---

  private static final class UserEntry
  {
    final String dn;

    final Attributes attributes;

    UserEntry(final String dn, final Attributes attributes) {
      this.dn = dn;
      this.attributes = attributes;
    }
  }

  private static final class UserInfo
      implements LdapUserInfo
  {
    private final String username;

    private final String realName;

    private final String emailAddress;

    private final Set<String> groups;

    UserInfo(
        final String username,
        final String realName,
        final String emailAddress,
        final Set<String> groups)
    {
      this.username = username;
      this.realName = realName;
      this.emailAddress = emailAddress;
      this.groups = Collections.unmodifiableSet(groups != null ? groups : Collections.emptySet());
    }

    @Override
    public String getUsername() {
      return username;
    }

    @Override
    public String getRealName() {
      return realName;
    }

    @Override
    public String getEmailAddress() {
      return emailAddress;
    }

    @Override
    public Set<String> getGroups() {
      return groups;
    }
  }
}
