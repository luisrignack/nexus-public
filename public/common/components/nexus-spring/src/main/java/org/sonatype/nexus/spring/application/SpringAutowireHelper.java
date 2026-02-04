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
package org.sonatype.nexus.spring.application;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Helper class to autowire beans out of the Spring context.
 */
@Component
public class SpringAutowireHelper
    implements ApplicationContextAware
{
  private static AutowireCapableBeanFactory autowireBeanFactory;

  @Override
  public void setApplicationContext(final ApplicationContext applicationContext) throws BeansException {
    autowireBeanFactory = applicationContext.getAutowireCapableBeanFactory();
  }

  public static void autowireBean(final Object bean) throws BeansException {
    if (bean != null && autowireBeanFactory != null) {
      autowireBeanFactory.autowireBean(bean);
    }
  }
}
