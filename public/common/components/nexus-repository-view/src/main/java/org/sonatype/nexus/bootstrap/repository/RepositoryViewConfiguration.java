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
package org.sonatype.nexus.bootstrap.repository;

import java.util.Collections;
import java.util.List;

import org.sonatype.nexus.repository.view.handlers.ContributedHandler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * This configuration is essential for the (OSS) version of Nexus.
 * It provides a default empty list for {@code ContributedHandler} beans,
 * preventing a {@code NoSuchBeanDefinitionException} at startup.
 *
 * <p>
 * Professional (PRO) and Community (CE) editions of Nexus include actual
 * implementations of {@code ContributedHandler}, so this bean isn't created
 * for those versions as it's not needed.
 */
@Configuration
public class RepositoryViewConfiguration
{
  @Bean
  @ConditionalOnMissingBean(ContributedHandler.class)
  public List<ContributedHandler> contributedHandlers() {
    return Collections.emptyList();
  }
}
