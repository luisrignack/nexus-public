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
package org.sonatype.nexus.repository.maven.internal;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.sonatype.nexus.rapture.UiPluginDescriptorSupport;

import jakarta.annotation.Priority;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Rapture {@link org.sonatype.nexus.rapture.UiPluginDescriptor} for {@code nexus-repository-maven}.
 *
 * @since 3.15
 */
@Component
@Singleton
@Priority(Integer.MAX_VALUE - 300) // after proui
@Order(Ordered.HIGHEST_PRECEDENCE + 300)
public class UiPluginDescriptorImpl
    extends UiPluginDescriptorSupport
{
  @Inject
  public UiPluginDescriptorImpl() {
    super("nexus-repository-maven");
    setHasStyle(false);
    setNamespace("NX.maven");
    setConfigClassName("NX.maven.app.PluginConfig");
  }
}
