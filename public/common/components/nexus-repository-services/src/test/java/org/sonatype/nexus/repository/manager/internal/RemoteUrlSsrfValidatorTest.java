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
package org.sonatype.nexus.repository.manager.internal;

import java.util.HashMap;
import java.util.Map;
import javax.validation.ConstraintViolation;

import org.sonatype.goodies.testsupport.Test5Support;
import org.sonatype.nexus.repository.config.Configuration;
import org.sonatype.nexus.validation.ConstraintViolationFactory;
import org.sonatype.nexus.validation.ssrf.AntiSsrfHelper;
import org.sonatype.nexus.validation.ssrf.AntiSsrfHelper.SsrfValidationResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;

import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RemoteUrlSsrfValidatorTest
    extends Test5Support
{
  @Mock
  private ConstraintViolationFactory constraintViolationFactory;

  @Mock
  private Configuration configuration;

  @Mock
  private AntiSsrfHelper antiSsrfHelper;

  private RemoteUrlSsrfValidator validator;

  @BeforeEach
  void setup() {
    validator = new RemoteUrlSsrfValidator(constraintViolationFactory, antiSsrfHelper);
  }

  @Test
  void shouldAllowPublicUrl() {
    Map<String, Map<String, Object>> attributes = new HashMap<>();
    Map<String, Object> proxyAttributes = new HashMap<>();
    proxyAttributes.put("remoteUrl", "https://repo1.maven.org/maven2/");
    attributes.put("proxy", proxyAttributes);

    when(configuration.getAttributes()).thenReturn(attributes);
    when(antiSsrfHelper.validateHostForConfiguration("repo1.maven.org")).thenReturn(SsrfValidationResult.success());

    ConstraintViolation<?> result = validator.validate(configuration);

    assertThat(result, is(nullValue()));
  }

  static Stream<Arguments> blockedUrlTestData() {
    return Stream.of(
        Arguments.of("http://localhost:8080/", "localhost", "loopback address"),
        Arguments.of("http://192.168.1.1/", "192.168.1.1", "private network address"),
        Arguments.of("http://169.254.169.254/latest/meta-data/", "169.254.169.254", "restricted address"));
  }

  @ParameterizedTest
  @MethodSource("blockedUrlTestData")
  void shouldBlockRestrictedUrls(String url, String host, String errorMessage) {
    String expectedMessage = "Proxy URL blocked: " + errorMessage + ". To allow connections, set " +
        "nexus.proxy.allowPrivateNetworks=true to allow all private networks, or configure specific " +
        "hosts using nexus.proxy.privateNetworks.allowedIPs or nexus.proxy.privateNetworks.allowedDomains.";
    ConstraintViolation<?> mockViolation = mock(ConstraintViolation.class);
    when(mockViolation.getMessage()).thenReturn(expectedMessage);
    when(constraintViolationFactory.createViolation("proxy.remoteUrl", expectedMessage))
        .thenReturn((ConstraintViolation) mockViolation);

    Map<String, Map<String, Object>> attributes = new HashMap<>();
    Map<String, Object> proxyAttributes = new HashMap<>();
    proxyAttributes.put("remoteUrl", url);
    attributes.put("proxy", proxyAttributes);

    when(configuration.getAttributes()).thenReturn(attributes);
    when(antiSsrfHelper.validateHostForConfiguration(host)).thenReturn(SsrfValidationResult.failure(errorMessage));

    ConstraintViolation<?> result = validator.validate(configuration);

    assertThat(result, is(notNullValue()));
    assertThat(result.getMessage(), is(expectedMessage));
  }

  @Test
  void shouldAllowNullProxyConfiguration() {
    Map<String, Map<String, Object>> attributes = new HashMap<>();
    when(configuration.getAttributes()).thenReturn(attributes);

    ConstraintViolation<?> result = validator.validate(configuration);

    assertThat(result, is(nullValue()));
  }

  @Test
  void shouldAllowNullRemoteUrl() {
    Map<String, Map<String, Object>> attributes = new HashMap<>();
    Map<String, Object> proxyAttributes = new HashMap<>();
    proxyAttributes.put("remoteUrl", null);
    attributes.put("proxy", proxyAttributes);

    when(configuration.getAttributes()).thenReturn(attributes);

    ConstraintViolation<?> result = validator.validate(configuration);

    assertThat(result, is(nullValue()));
  }

}
