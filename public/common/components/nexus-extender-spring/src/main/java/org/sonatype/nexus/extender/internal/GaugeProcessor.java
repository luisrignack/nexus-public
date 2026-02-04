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
package org.sonatype.nexus.extender.internal;

import java.lang.reflect.Method;

import com.codahale.metrics.annotation.Gauge;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import static com.codahale.metrics.MetricRegistry.name;

/**
 * Provides annotation support for {@link Gauge} metrics.
 */
@Component
public class GaugeProcessor
    extends AbstractGaugeProcessor<Gauge>
{
  @Override
  protected void registerMetric(Object bean, Method method, Gauge annotation, String metricName) {
    registerMetricAsGauge(bean, metricName, method);
  }

  @Override
  protected Gauge getAnnotation(Method method) {
    return AnnotationUtils.getAnnotation(method, Gauge.class);
  }

  @Override
  protected String metricName(Method method, Gauge gauge) {
    if (gauge.absolute()) {
      return gauge.name();
    }
    if (gauge.name().isEmpty()) {
      return name(method.getDeclaringClass(), method.getName(), "gauge");
    }
    return name(method.getDeclaringClass(), gauge.name());
  }

  @Override
  protected String gaugeName() {
    return "@Gauge";
  }
}
