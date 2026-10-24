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
package org.sonatype.nexus.internal.security.usertoken;

import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.sonatype.nexus.security.UserPrincipalsHelper;
import org.sonatype.nexus.security.authc.NexusApiKeyAuthenticationToken;
import org.sonatype.nexus.security.authc.apikey.ApiKeyService;
import org.sonatype.nexus.security.token.BearerTokenRealm;
import org.sonatype.nexus.security.user.UserNotFoundException;

import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.subject.PrincipalCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Realm that authenticates {@link NexusApiKeyAuthenticationToken}s issued by the user token feature.
 *
 * @since 3.89
 */
@Component
@Singleton
@Qualifier(UserTokenRealm.NAME)
public class UserTokenRealm
    extends BearerTokenRealm
{
  public static final String NAME = UserTokenService.DOMAIN;

  private static final Logger log = LoggerFactory.getLogger(UserTokenRealm.class);

  private final UserTokenService userTokens;

  private final UserPrincipalsHelper principalsHelper;

  @Inject
  public UserTokenRealm(
      final ApiKeyService apiKeyService,
      final UserPrincipalsHelper principalsHelper,
      final UserTokenService userTokens)
  {
    super(apiKeyService, principalsHelper, NAME);
    this.principalsHelper = checkNotNull(principalsHelper);
    this.userTokens = checkNotNull(userTokens);
    // tokens can be reset at any time; always resolve them against the store
    setAuthenticationCachingEnabled(false);
  }

  @Override
  protected AuthenticationInfo doGetAuthenticationInfo(final AuthenticationToken token) {
    if (!(token instanceof NexusApiKeyAuthenticationToken) || !userTokens.isEnabled()) {
      return null;
    }

    Optional<PrincipalCollection> principals =
        userTokens.authenticate(new String((char[]) token.getCredentials()));

    if (principals.isEmpty()) {
      return null;
    }

    PrincipalCollection principalCollection = principals.get();
    try {
      if (principalsHelper.getUserStatus(principalCollection).isActive()) {
        return new SimpleAuthenticationInfo(principalCollection, token.getCredentials());
      }
      log.debug("Rejecting user token of inactive user {}", principalCollection.getPrimaryPrincipal());
    }
    catch (UserNotFoundException e) {
      log.debug("Rejecting user token of unknown user", e);
      userTokens.deleteToken(principalCollection);
    }
    return null;
  }
}
