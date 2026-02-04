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
package org.sonatype.nexus.blobstore.metrics;

import org.sonatype.nexus.blobstore.BlobSupport;
import org.sonatype.nexus.blobstore.api.BlobStore;
import org.sonatype.nexus.blobstore.api.OperationMetrics;
import org.sonatype.nexus.blobstore.api.OperationType;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkState;

/**
 * Aspect to monitor blob store operations (see {@link OperationType}) of the annotated method.
 *
 */
@Aspect
public class BlobStoreAnalyticsAspect
{
  @Around("@annotation(metricsAnnotation) && execution(* *(..))")
  public Object monitorBlobStoreOperation(
      final ProceedingJoinPoint joinPoint,
      final MonitoringBlobStoreMetrics metricsAnnotation) throws Throwable
  {
    String clazz = joinPoint.getTarget().getClass().getSimpleName();
    String methodName = joinPoint.getSignature().getName();

    checkState(metricsAnnotation != null);
    OperationType operationType = metricsAnnotation.operationType();

    OperationMetrics operationMetrics = null;
    if (joinPoint.getTarget() instanceof BlobStore blobStore) {
      blobStore = (BlobStore) joinPoint.getTarget();
      operationMetrics = blobStore.getOperationMetricsDelta().get(operationType);
    }
    if (operationMetrics == null) {
      LoggerFactory.getLogger(BlobStoreAnalyticsAspect.class)
          .info("Can't monitor operation metrics for class={}, methodName={}", clazz, methodName);
      return joinPoint.proceed();
    }

    long start = System.currentTimeMillis();
    try {
      Object result = joinPoint.proceed();

      // Record metrics only in case of successful processing.
      operationMetrics.addSuccessfulRequest();
      operationMetrics.addTimeOnRequests(System.currentTimeMillis() - start);

      if (result instanceof BlobSupport) {
        long totalSize = ((BlobSupport) result).getMetrics().getContentSize();
        operationMetrics.addBlobSize(totalSize);
      }

      return result;
    }
    catch (Exception e) {
      operationMetrics.addErrorRequest();
      throw e;
    }
  }
}
