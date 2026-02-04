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
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.codahale.metrics.annotation.CachedGauge;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.PropertyResolver;
import org.springframework.stereotype.Component;

import static com.codahale.metrics.MetricRegistry.name;
import static java.lang.Boolean.parseBoolean;

/**
 * {@link BeanPostProcessor} which provides annotation support for registering {@code CachedGauges}
 *
 * @since 3.26
 */
@Component
public class CachedGaugeProcessor
    extends AbstractGaugeProcessor<CachedGauge>
{
  private static final String CACHE_DISABLE_ALL = "nexus.analytics.cache.disableAll";

  private static final String CACHE_DISABLE_PREFIX = "nexus.analytics.cache.disable.";

  private static final String CACHE_TIMEOUT_SUFFIX = ".cache.timeout";

  private static final String CACHE_TIMEUNIT_SUFFIX = ".cache.timeUnit";

  private static final String GAUGE_DISABLE_SUFFIX = ".disable";

  private final PropertyResolver nexusProperties;

  @Lazy
  @Autowired
  public CachedGaugeProcessor(final PropertyResolver nexusProperties) {
    this.nexusProperties = nexusProperties;
  }

  @Override
  protected void registerMetric(
      final Object bean,
      final Method method,
      final CachedGauge gauge,
      final String metricName)
  {
    if (parseBoolean(nexusProperties.getProperty(metricName + GAUGE_DISABLE_SUFFIX))) {
      log.info("Removed Analytics for {} as directed in nexus.properties", metricName);
      return;
    }
    registerGaugeAsCachedIfEnabled(bean, metricName, method, gauge);
  }

  @Override
  protected String gaugeName() {
    return "@CachedGauge";
  }

  @Override
  protected CachedGauge getAnnotation(final Method method) {
    return AnnotationUtils.getAnnotation(method, CachedGauge.class);
  }

  @Override
  protected String metricName(final Method method, final CachedGauge gauge) {
    if (gauge.absolute()) {
      return gauge.name();
    }
    if (gauge.name().isEmpty()) {
      return name(method.getDeclaringClass(), method.getName(), "gauge");
    }
    return name(method.getDeclaringClass(), gauge.name());
  }

  private void registerGaugeAsCachedIfEnabled(
      final Object bean,
      final String metricName,
      final Method method,
      final CachedGauge annotation)
  {
    if (parseBoolean(nexusProperties.getProperty(CACHE_DISABLE_ALL))
        || parseBoolean(nexusProperties.getProperty(CACHE_DISABLE_PREFIX + metricName))) {
      log.info("Disabled Analytics Cache for {} as directed in nexus.properties", metricName);
      registerMetricAsGauge(bean, metricName, method);
    }
    else {
      registerMetricAsCachedGauges(bean, metricName, method, annotation);
    }
  }

  private void registerMetricAsCachedGauges(
      final Object bean,
      final String metricName,
      final Method method,
      final CachedGauge annotation)
  {
    long timeout = annotation.timeout();
    Optional<Long> timeoutOverride = getTimeoutOverride(metricName);
    if (timeoutOverride.isPresent()) {
      timeout = timeoutOverride.get();
    }

    TimeUnit timeUnit = annotation.timeoutUnit();
    Optional<TimeUnit> timeUnitOverride = getTimeUnitOverride(metricName);
    if (timeUnitOverride.isPresent()) {
      timeUnit = timeUnitOverride.get();
    }

    if (timeout != annotation.timeout() || !timeUnit.equals(annotation.timeoutUnit())) {
      log.info("Updated Analytics Cache for {} to {} {} as directed in nexus.properties", metricName, timeout,
          timeUnit);
    }

    metricRegistry.register(metricName, new com.codahale.metrics.CachedGauge<Object>(timeout, timeUnit)
    {
      @Override
      protected Object loadValue() {
        try {
          return method.invoke(bean);
        }
        catch (Exception e) {
          return new RuntimeException(e);
        }
      }
    });
  }

  private Optional<Long> getTimeoutOverride(final String metricName) {
    if (nexusProperties.containsProperty(metricName + CACHE_TIMEOUT_SUFFIX)) {
      Object value = nexusProperties.getProperty(metricName + CACHE_TIMEOUT_SUFFIX);
      try {
        return Optional.of(Long.parseLong(value.toString()));
      }
      catch (Exception ex) {
        log.warn("Failed to parse Analytics Cache configuration in nexus.properties: {} = {}",
            metricName + CACHE_TIMEOUT_SUFFIX, value);
        if (log.isDebugEnabled()) {
          log.debug("Stack Trace:", ex);
        }
      }
    }
    return Optional.empty();
  }

  private Optional<TimeUnit> getTimeUnitOverride(final String metricName) {
    if (nexusProperties.containsProperty(metricName + CACHE_TIMEUNIT_SUFFIX)) {
      Object value = nexusProperties.getProperty(metricName + CACHE_TIMEUNIT_SUFFIX);
      try {
        return Optional.of(TimeUnit.valueOf(value.toString().toUpperCase()));
      }
      catch (Exception ex) {
        log.warn("Failed to parse Analytics Cache configuration in nexus.properties: {} = {}",
            metricName + CACHE_TIMEUNIT_SUFFIX, value);
        if (log.isDebugEnabled()) {
          log.debug("Stack Trace:", ex);
        }
      }
    }
    return Optional.empty();
  }
}
