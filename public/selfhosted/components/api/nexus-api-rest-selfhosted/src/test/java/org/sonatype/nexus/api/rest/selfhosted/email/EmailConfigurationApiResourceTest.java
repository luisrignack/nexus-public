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
package org.sonatype.nexus.api.rest.selfhosted.email;

import org.sonatype.goodies.testsupport.Test5Support;
import org.sonatype.nexus.api.rest.selfhosted.email.model.ApiEmailConfiguration;
import org.sonatype.nexus.common.text.Strings2;
import org.sonatype.nexus.email.EmailConfiguration;
import org.sonatype.nexus.email.EmailManager;
import org.sonatype.nexus.testcommon.extensions.AuthenticationExtension;
import org.sonatype.nexus.testcommon.extensions.AuthenticationExtension.WithUser;
import org.sonatype.nexus.testcommon.validation.ValidationExtension;

import org.apache.commons.mail.EmailException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(ValidationExtension.class)
@ExtendWith(AuthenticationExtension.class)
@WithUser
class EmailConfigurationApiResourceTest
    extends Test5Support
{
  @Mock
  private EmailManager emailManager;

  @Mock
  private EmailConfiguration emailConfiguration;

  private EmailConfigurationApiResource underTest;

  @BeforeEach
  void setup() {
    underTest = new EmailConfigurationApiResource(emailManager);
  }

  @Test
  void getUnconfiguredEmailConfigurationHandlesNullDefaultConfiguration() {
    ApiEmailConfiguration response = underTest.getEmailConfiguration();

    assertThat(response.getFromAddress(), is(nullValue()));
    assertThat(response.getHost(), is(nullValue()));
    assertThat(response.getPassword(), is(nullValue()));
    assertThat(response.getPort(), is(nullValue()));
    assertThat(response.getSubjectPrefix(), is(nullValue()));
    assertThat(response.getUsername(), is(nullValue()));
    assertThat(response.isEnabled(), is(false));
    assertThat(response.isNexusTrustStoreEnabled(), is(false));
    assertThat(response.isSslOnConnectEnabled(), is(false));
    assertThat(response.isSslServerIdentityCheckEnabled(), is(false));
    assertThat(response.isStartTlsEnabled(), is(false));
    assertThat(response.isStartTlsRequired(), is(false));
  }

  @Test
  void getEmailConfigurationObfuscatesThePassword() {
    when(emailManager.getConfiguration()).thenReturn(emailConfiguration);

    ApiEmailConfiguration response = underTest.getEmailConfiguration();

    assertThat(response.getPassword(), is(nullValue()));
  }

  @Test
  void setEmailConfigurationSetsTheNewConfiguration() {
    EmailConfiguration newConfiguration = mock(EmailConfiguration.class);
    String newPassword = "testPassword";
    ApiEmailConfiguration request = new ApiEmailConfiguration();
    request.setEnabled(true);
    request.setPassword(newPassword);

    when(emailManager.newConfiguration()).thenReturn(newConfiguration);

    underTest.setEmailConfiguration(request);

    verify(emailManager).setConfiguration(newConfiguration, newPassword);
    verify(newConfiguration).setEnabled(true);
  }

  @Test
  void setEmailConfigurationKeepsTheOriginalPassword() {
    EmailConfiguration newConfiguration = mock(EmailConfiguration.class);
    when(emailManager.newConfiguration()).thenReturn(newConfiguration);

    ApiEmailConfiguration request = new ApiEmailConfiguration();
    request.setEnabled(true);
    request.setPassword(Strings2.EMPTY);

    underTest.setEmailConfiguration(request);

    verify(newConfiguration).setEnabled(true);
    verify(emailManager).setConfiguration(newConfiguration, Strings2.EMPTY);
  }

  @Test
  void testEmailConfigurationSendsTestEmail() throws EmailException {
    when(emailManager.getConfiguration()).thenReturn(emailConfiguration);
    String destinationAddress = "test@example.com";

    underTest.testEmailConfiguration(destinationAddress);

    verify(emailManager).sendVerification(emailConfiguration, destinationAddress);
  }
}
