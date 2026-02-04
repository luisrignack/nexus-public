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
package org.sonatype.nexus.repository.webhooks;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class RepositoryWebhookTypeValidatorTest
{
  private final RepositoryWebhookTypeValidator validator = new RepositoryWebhookTypeValidator(List.of(
      new TestRepositoryWebhook("webhook1"),
      new TestRepositoryWebhook("webhook2")));

  @Test
  void validatesEmptyWebhookNamesAsValid() {
    List<String> names = List.of();
    boolean result = validator.isValid(names, null);
    assertThat(result, is(true));
  }

  @Test
  void validatesNullWebhookNamesAsValid() {
    boolean result = validator.isValid(null, null);
    assertThat(result, is(true));
  }

  @Test
  void validatesValidWebhookNames() {
    List<String> names = List.of("webhook1", "webhook2");
    boolean result = validator.isValid(names, null);
    assertThat(result, is(true));
  }

  @Test
  void invalidatesInvalidWebhookNames() {
    List<String> names = List.of("webhook1", "invalidWebhook");
    boolean result = validator.isValid(names, null);
    assertThat(result, is(false));
  }

  private static class TestRepositoryWebhook
      extends RepositoryWebhook
  {
    private final String name;

    public TestRepositoryWebhook(String name) {
      this.name = name;
    }

    @Override
    public String getName() {
      return name;
    }
  }
}
