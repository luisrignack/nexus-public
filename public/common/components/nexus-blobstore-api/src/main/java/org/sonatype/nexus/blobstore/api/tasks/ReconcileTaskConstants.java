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
package org.sonatype.nexus.blobstore.api.tasks;

/**
 * Shared constants for reconcile tasks
 */
public final class ReconcileTaskConstants
{
  public static final String TASK_SCOPE = "taskScope";

  public static final String TASK_SCOPE_DURATION = "duration";

  public static final String SINCE_DAYS = "sinceDays";

  public static final String SINCE_HOURS = "sinceHours";

  public static final String SINCE_MINUTES = "sinceMinutes";

  public static final String TASK_SCOPE_DATES = "dates";

  public static final String START_DATE = "reconcileStartDate";

  public static final String END_DATE = "reconcileEndDate";

  public static final String PLAN_RECONCILE_TYPE_ID = "blobstore.planReconciliation";

  public static final String EXECUTE_RECONCILE_PLAN_TYPE_ID = "blobstore.executeReconciliationPlan";

  public static final String UNABLE_TO_RUN_TASK_LOG_ERROR = "Unable to run task {}";

  public static final String ENTRY_ALL_BLOB_STORES = "(All Blob Stores)";

  public static final String BLOB_STORE_NAME_FIELD_ID = "blobstoreName";

  public static final String REPOSITORY_NAME_FIELD_ID = "repositoryName";

  public static final String PLAN_INFORMATION_FIELD_ID = "planInformation";

  public static final String ONLY_NOTIFY = "onlyNotify";

  private ReconcileTaskConstants() {
  }
}
