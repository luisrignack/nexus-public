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
package org.sonatype.nexus.testcommon.extensions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashSet;
import java.util.Set;

import org.apache.shiro.authz.Permission;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.authz.permission.WildcardPermission;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.realm.SimpleAccountRealm;
import org.apache.shiro.authc.SimpleAccount;
import org.apache.shiro.session.Session;
import org.apache.shiro.session.mgt.SimpleSession;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.support.DelegatingSubject;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;

/**
 * A JUnit 5 extension which helps {@link RequiresAuthentication} and {@link RequiresPermissions} annotated methods
 * uses a real Shiro SecurityManager and SimpleAccountRealm instead of mocks.
 */
public class AuthenticationExtension
    implements Extension, BeforeEachCallback, AfterEachCallback, BeforeAllCallback, AfterAllCallback
{
  @Target({ElementType.TYPE, ElementType.METHOD})
  @Retention(RetentionPolicy.RUNTIME)
  public @interface WithUser
  {
    /**
     * The name of the logged in user
     */
    String value() default "admin";

    /**
     * When no values are specified we treat this as all nexus permissions
     */
    String[] permissions() default {"nexus:*"};

    /**
     * Whether the user is authenticated or not
     */
    boolean isAuthenticated() default true;
  }

  private DefaultSecurityManager securityManager;

  private AuthenticationTestRealm realm;

  @Override
  public void beforeAll(final ExtensionContext context) throws Exception {
    setupSecurityManager();

    context.getTestClass()
        .flatMap(clazz -> AnnotationSupport.findAnnotation(clazz, WithUser.class))
        .ifPresent(this::withUser);
  }

  @Override
  public void afterAll(final ExtensionContext context) throws Exception {
    context.getTestClass()
        .flatMap(clazz -> AnnotationSupport.findAnnotation(clazz, WithUser.class))
        .ifPresent(this::unsetUser);

    cleanupSecurityManager();
  }

  @Override
  public void afterEach(final ExtensionContext context) throws Exception {
    ThreadContext.unbindSubject();
    ThreadContext.unbindSecurityManager();
  }

  @Override
  public void beforeEach(final ExtensionContext context) throws Exception {
    realm = new AuthenticationTestRealm("test-realm");
    securityManager.setRealm(realm);

    ThreadContext.bind(securityManager);

    context.getTestMethod()
        .flatMap(method -> AnnotationSupport.findAnnotation(method, WithUser.class))
        .or(() -> context.getTestClass()
            .flatMap(clazz -> AnnotationSupport.findAnnotation(clazz, WithUser.class)))
        .ifPresent(this::withUser);
  }

  private void setupSecurityManager() {
    securityManager = new DefaultSecurityManager();
    realm = new AuthenticationTestRealm("test-realm");
    securityManager.setRealm(realm);

    ThreadContext.bind(securityManager);
  }

  private void cleanupSecurityManager() {
    if (securityManager != null) {
      ThreadContext.unbindSecurityManager();
      ThreadContext.unbindSubject();
      securityManager.destroy();
      securityManager = null;
      realm = null;
    }
  }

  private void withUser(final WithUser withUser) {
    String username = withUser.value();
    boolean isAuthenticated = withUser.isAuthenticated();

    if (isAuthenticated) {
      realm.addAccount(username, "password", "user-role", withUser.permissions());
    }

    SimplePrincipalCollection principals = new SimplePrincipalCollection(username, realm.getName());
    Session session = new SimpleSession();

    DelegatingSubject subject =
        new DelegatingSubject(principals, isAuthenticated, "localhost", session, securityManager);
    ThreadContext.bind(subject);
  }

  private void unsetUser(final WithUser withUser) {
    ThreadContext.unbindSubject();
  }

  private static class AuthenticationTestRealm
      extends SimpleAccountRealm
  {
    public AuthenticationTestRealm(String name) {
      super(name);
    }

    public void addAccount(String username, String password, String roleName, String[] permissions) {
      Set<Permission> permissionCollection = new HashSet<>();
      for (String permission : permissions) {
        permissionCollection.add(new WildcardPermission(permission));
      }
      SimpleAccount account = new SimpleAccount(username, password, getName(), Set.of(roleName), permissionCollection);
      add(account);
    }
  }
}
