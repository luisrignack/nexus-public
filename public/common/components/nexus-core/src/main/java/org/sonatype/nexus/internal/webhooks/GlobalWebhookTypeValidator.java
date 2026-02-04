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
package org.sonatype.nexus.internal.webhooks;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.validation.ConstraintValidatorContext;

import org.sonatype.nexus.validation.ConstraintValidatorSupport;
import org.sonatype.nexus.webhooks.GlobalWebhook;

import jakarta.inject.Inject;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE;

@Component
@Scope(SCOPE_PROTOTYPE)
public class GlobalWebhookTypeValidator
    extends ConstraintValidatorSupport<GlobalWebhookType, List<String>>
{
  private final List<GlobalWebhook> globalWebhooks;

  @Inject
  public GlobalWebhookTypeValidator(final List<GlobalWebhook> globalWebhooks) {
    this.globalWebhooks = globalWebhooks;
  }

  @Override
  public boolean isValid(final List<String> names, final ConstraintValidatorContext constraintValidatorContext) {
    if (names == null || names.isEmpty()) {
      return true; // empty list is valid
    }

    Set<String> webhookNames =
        globalWebhooks.stream().map(GlobalWebhook::getName).collect(Collectors.toUnmodifiableSet());
    // Check if all names in the provided list are valid webhook names
    return webhookNames.containsAll(names);
  }
}
