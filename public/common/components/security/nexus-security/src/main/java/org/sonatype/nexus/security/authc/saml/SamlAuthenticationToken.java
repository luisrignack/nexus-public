/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 */
package org.sonatype.nexus.security.authc.saml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.shiro.authc.AuthenticationToken;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Shiro token produced by a validated SAML assertion.
 */
public class SamlAuthenticationToken
    implements AuthenticationToken
{
  private final String userId;

  private final Map<String, Object> attributes;

  private final List<String> groups;

  public SamlAuthenticationToken(
      final String userId,
      final Map<String, Object> attributes,
      final List<String> groups)
  {
    this.userId = checkNotNull(userId);
    this.attributes = attributes == null ? Collections.emptyMap()
        : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    this.groups = groups == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(groups));
  }

  @Override
  public Object getPrincipal() {
    return userId;
  }

  @Override
  public Object getCredentials() {
    return attributes;
  }

  public String getUserId() {
    return userId;
  }

  public Map<String, Object> getAttributes() {
    return attributes;
  }

  public List<String> getGroups() {
    return groups;
  }

  public String attribute(final String name) {
    Object value = attributes.get(name);
    return value == null ? null : String.valueOf(value);
  }
}
