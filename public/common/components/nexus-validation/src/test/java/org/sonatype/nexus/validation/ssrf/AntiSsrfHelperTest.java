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
package org.sonatype.nexus.validation.ssrf;

import org.sonatype.nexus.validation.ssrf.AntiSsrfHelper.SsrfValidationResult;

import java.time.Duration;
import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.Is.is;

@SpringJUnitConfig(classes = {AntiSsrfHelper.class, AntiSsrfHelperTest.TestConfig.class})
class AntiSsrfHelperTest
{
  @Configuration
  static class TestConfig
  {
    @Bean
    public DefaultFormattingConversionService conversionService() {
      DefaultFormattingConversionService service = new DefaultFormattingConversionService();
      service.addConverter(new Converter<String, Duration>()
      {
        @Override
        public Duration convert(String source) {
          return Duration.parse("PT" + source.toUpperCase());
        }
      });
      return service;
    }
  }

  @Nested
  @TestPropertySource(properties = "nexus.proxy.allowPrivateNetworks=false")
  class BlockPrivateNetworks
  {
    @Autowired
    private AntiSsrfHelper helper;

    static Stream<Arguments> blockedHostsTestData() {
      return Stream.of(
          Arguments.of("169.254.169.254", "restricted address"),
          Arguments.of("169.254.1.1", "link-local"),
          Arguments.of("127.0.0.1", "loopback"),
          Arguments.of("::1", "loopback"),
          Arguments.of("0:0:0:0:0:0:0:1", "loopback"),
          Arguments.of("10.0.0.1", "private network"),
          Arguments.of("192.168.1.1", "private network"),
          Arguments.of("172.16.0.1", "private network"),
          Arguments.of(null, "cannot be null"),
          Arguments.of("", "cannot be null or empty"),
          Arguments.of("   ", "cannot be null or empty"),
          Arguments.of("this-host-does-not-exist.invalid", "Failed to resolve"));
    }

    @ParameterizedTest(name = "{index}: Host {0} should be blocked")
    @MethodSource("blockedHostsTestData")
    void shouldBlockRestrictedHosts(String host, String expectedErrorFragment) {
      SsrfValidationResult result = helper.validateHost(host);
      assertThat(result.isValid(), is(false));
      assertThat(result.getErrorMessage(), containsString(expectedErrorFragment));
    }

    @Test
    void shouldAllowPublicIp() {
      SsrfValidationResult result = helper.validateHost("8.8.8.8");
      assertThat(result.isValid(), is(true));
    }

    @Test
    void shouldAllowPublicHostname() {
      SsrfValidationResult result = helper.validateHost("repo1.maven.org");
      assertThat(result.isValid(), is(true));
    }
  }

  @Nested
  @TestPropertySource(properties = "nexus.proxy.allowPrivateNetworks=true")
  class AllowPrivateNetworks
  {
    @Autowired
    private AntiSsrfHelper helper;

    @Test
    void shouldBlockCloudMetadataEvenWhenAllowingPrivateNetworks() {
      SsrfValidationResult result = helper.validateHost("169.254.169.254");
      assertThat(result.isValid(), is(false));
      assertThat(result.getErrorMessage(), containsString("restricted address"));
    }

    static Stream<String> allowedHostsTestData() {
      return Stream.of(
          "169.254.1.1",
          "10.0.0.1",
          "127.0.0.1",
          "this-host-does-not-exist.invalid");
    }

    @ParameterizedTest(name = "{index}: Host {0} should be allowed")
    @MethodSource("allowedHostsTestData")
    void shouldAllowPrivateAndUnresolvableHosts(String host) {
      SsrfValidationResult result = helper.validateHost(host);
      assertThat(result.isValid(), is(true));
    }
  }

  @Nested
  @TestPropertySource(properties = {
      "nexus.proxy.allowPrivateNetworks=false",
      "nexus.proxy.privateNetworks.allowedIPs=192.168.1.100,10.0.0.50"
  })
  class WhitelistedIPs
  {
    @Autowired
    private AntiSsrfHelper helper;

    @Test
    void shouldAllowWhitelistedIP() {
      SsrfValidationResult result = helper.validateHost("192.168.1.100");
      assertThat(result.isValid(), is(true));
    }

    @Test
    void shouldBlockNonWhitelistedPrivateIP() {
      SsrfValidationResult result = helper.validateHost("192.168.1.101");
      assertThat(result.isValid(), is(false));
      assertThat(result.getErrorMessage(), containsString("private network"));
    }
  }

  @Nested
  @TestPropertySource(properties = {
      "nexus.proxy.allowPrivateNetworks=false",
      "nexus.proxy.privateNetworks.allowedIPs=169.254.169.254"
  })
  class CloudMetadataCannotBeWhitelisted
  {
    @Autowired
    private AntiSsrfHelper helper;

    @Test
    void shouldBlockCloudMetadataEvenWhenWhitelistedInIPList() {
      SsrfValidationResult result = helper.validateHost("169.254.169.254");
      assertThat(result.isValid(), is(false));
      assertThat(result.getErrorMessage(), containsString("restricted address"));
    }
  }

  @Nested
  @TestPropertySource(properties = {
      "nexus.proxy.allowPrivateNetworks=false",
      "nexus.proxy.privateNetworks.allowedDomains=169.254.169.254"
  })
  class CloudMetadataCannotBeWhitelistedAsDomain
  {
    @Autowired
    private AntiSsrfHelper helper;

    @Test
    void shouldBlockCloudMetadataEvenWhenWhitelistedInDomainList() {
      SsrfValidationResult result = helper.validateHost("169.254.169.254");
      assertThat(result.isValid(), is(false));
      assertThat(result.getErrorMessage(), containsString("restricted address"));
    }
  }

  @Nested
  @TestPropertySource(properties = {
      "nexus.proxy.allowPrivateNetworks=false",
      "nexus.proxy.privateNetworks.allowedDomains=internal.company.com,  example.local"
  })
  class WhitelistedDomains
  {
    @Autowired
    private AntiSsrfHelper helper;

    @Test
    void shouldAllowWhitelistedDomain() {
      SsrfValidationResult result = helper.validateHost("internal.company.com");
      assertThat(result.isValid(), is(true));

      result = helper.validateHost("example.local");
      assertThat(result.isValid(), is(true));
    }

    @Test
    void shouldBlockNonWhitelistedDomain() {
      SsrfValidationResult result = helper.validateHost("other.company.com");
      assertThat(result.isValid(), is(false));
    }
  }
}
