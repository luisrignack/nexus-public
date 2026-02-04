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
package org.sonatype.nexus.common.metrics;

import java.lang.reflect.Method;

import org.sonatype.goodies.common.ComponentSupport;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.annotation.ExceptionMetered;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import static com.codahale.metrics.MetricRegistry.name;

/**
 * Aspect to handle {@link ExceptionMetered} annotation and register exception meters in the metrics registry.
 */
@Aspect
public class ExceptionMeteredAspect
    extends ComponentSupport
{
  private final MetricRegistry metricRegistry = SharedMetricRegistries.getOrCreate("nexus");

  @Around("@annotation(exceptionMetered) && execution(* *(..))")
  public Object around(final ProceedingJoinPoint joinPoint, final ExceptionMetered exceptionMetered) throws Throwable {
    Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
    try {
      return joinPoint.proceed();
    }
    catch (Throwable t) {
      if (isInstanceOf(t, exceptionMetered.cause())) {
        String metricName = metricName(method, exceptionMetered);
        Meter meter = metricRegistry.meter(metricName);
        meter.mark();
      }
      throw t;
    }
  }

  private boolean isInstanceOf(Throwable t, Class<? extends Throwable> cause) {
    return cause.isAssignableFrom(t.getClass());
  }

  public static String metricName(final Method method, final ExceptionMetered exceptionMetered) {
    if (exceptionMetered.absolute()) {
      return exceptionMetered.name();
    }

    if (exceptionMetered.name().isEmpty()) {
      return name(method.getDeclaringClass(), method.getName(), ExceptionMetered.DEFAULT_NAME_SUFFIX);
    }

    return name(method.getDeclaringClass(), exceptionMetered.name());
  }
}
