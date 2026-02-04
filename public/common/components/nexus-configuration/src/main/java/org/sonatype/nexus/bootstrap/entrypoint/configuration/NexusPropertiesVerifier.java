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
package org.sonatype.nexus.bootstrap.entrypoint.configuration;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.lang.Boolean.parseBoolean;
import static org.sonatype.nexus.bootstrap.entrypoint.configuration.NexusDirectoryConfiguration.BASEDIR_SYS_PROP;
import static org.sonatype.nexus.bootstrap.entrypoint.configuration.NexusDirectoryConfiguration.DATADIR_SYS_PROP;
import static org.sonatype.nexus.common.app.FeatureFlags.*;

/**
 * This class will make sure that the NexusProperties are set correctly, that there aren't mutually exclusive
 * configurations, and that required properties are present.
 * <p>
 * A reworking of the NexusEditionPropertiesConfigurer to be used in the new spring-only world
 */
public class NexusPropertiesVerifier
{
  public static final String FALSE = "false";

  public static final String TRUE = "true";

  public static final String DB_FEATURE_PROPERTY_KEY = "nexus-db-feature";

  public static final String COMMUNITY = "nexus-community-edition";

  public void verify(final NexusProperties nexusProperties) {
    // Ensure required properties exist
    requireProperty(nexusProperties, BASEDIR_SYS_PROP);
    requireProperty(nexusProperties, DATADIR_SYS_PROP);

    applyEnvironmentVariables(nexusProperties);

    // the select methods must happen after applying environment variables
    selectDatastoreFeature(nexusProperties);
    selectAuthenticationFeature(nexusProperties);

    requireProperty(nexusProperties, DB_FEATURE_PROPERTY_KEY);
    ensureHACIsDisabled(nexusProperties);
    selectDefaults(nexusProperties);
  }

  private static void applyEnvironmentVariables(final NexusProperties nexusProperties) {
    maybeApplyEnvironmentVariable(
        nexusProperties,
        CHANGE_REPO_BLOBSTORE_TASK_ENABLED,
        "CHANGE_REPO_BLOBSTORE_TASK_ENABLED");
    maybeApplyEnvironmentVariable(nexusProperties, FIREWALL_QUARANTINE_FIX_ENABLED, "FIREWALL_QUARANTINE_FIX_ENABLED");
    maybeApplyEnvironmentVariable(nexusProperties, ZERO_DOWNTIME_BASELINE_FAIL, "NEXUS_ZDU_BASELINE_FAIL");
    maybeApplyEnvironmentVariable(
        nexusProperties,
        ZERO_DOWNTIME_FUTURE_MIGRATION_ENABLED,
        "NEXUS_ZDU_FUTURE_MIGRATION_ENABLED");
    maybeApplyEnvironmentVariable(nexusProperties, SECRETS_FILE, "NEXUS_SECRETS_KEY_FILE");
    maybeApplyEnvironmentVariable(nexusProperties, DATASTORE_CLUSTERED_ENABLED, "DATASTORE_CLUSTERED_ENABLED");
    maybeApplyEnvironmentVariable(
        nexusProperties,
        CLUSTERED_ZERO_DOWNTIME_ENABLED,
        CLUSTERED_ZERO_DOWNTIME_ENABLED_ENV);
    maybeApplyEnvironmentVariable(nexusProperties, "nexus.clustered", "NEXUS_CLUSTERED");
  }

  private static void selectDatastoreFeature(final NexusProperties nexusProperties) {
    if (parseBoolean(nexusProperties.getProperty(DATASTORE_CLUSTERED_ENABLED))) {
      nexusProperties.put(SQL_DISTRIBUTED_CACHE, TRUE);
      nexusProperties.put(DATASTORE_BLOBSTORE_METRICS, TRUE);
    }
    else {
      // Set default of clustered upgrades off when clustering is off
      nexusProperties.put(CLUSTERED_ZERO_DOWNTIME_ENABLED, FALSE);
    }

    nexusProperties.put(DB_FEATURE_PROPERTY_KEY, "nexus-datastore-mybatis");
    nexusProperties.put(
        CHANGE_REPO_BLOBSTORE_TASK_ENABLED,
        nexusProperties.get(CHANGE_REPO_BLOBSTORE_TASK_ENABLED, TRUE));
    nexusProperties.put("nexus.quartz.jobstore.jdbc", TRUE);
  }

  private static void selectAuthenticationFeature(final NexusProperties nexusProperties) {
    nexusProperties.put(NEXUS_SECURITY_OAUTH2_ENABLED, TRUE);
    if (parseBoolean(nexusProperties.getProperty(DATASTORE_CLUSTERED_ENABLED))) {
      // if datastore is clustered, JWT must be enabled
      nexusProperties.put(JWT_ENABLED, TRUE);
      nexusProperties.put(SESSION_ENABLED, FALSE);
    }
    else if (isNullOrEmpty(nexusProperties.getProperty(SESSION_ENABLED))
        && isNullOrEmpty(nexusProperties.getProperty(JWT_ENABLED))) {
      nexusProperties.put(SESSION_ENABLED, TRUE);
      nexusProperties.put(JWT_ENABLED, FALSE);
    }
    else if (!isNullOrEmpty(nexusProperties.getProperty(SESSION_ENABLED))
        && isNullOrEmpty(nexusProperties.getProperty(JWT_ENABLED))) {
      nexusProperties.put(JWT_ENABLED, parseBoolean(nexusProperties.getProperty(SESSION_ENABLED)) ? FALSE : TRUE);
    }
    else if (!isNullOrEmpty(nexusProperties.getProperty(JWT_ENABLED))
        && isNullOrEmpty(nexusProperties.getProperty(SESSION_ENABLED))) {
      nexusProperties.put(SESSION_ENABLED, parseBoolean(nexusProperties.getProperty(JWT_ENABLED)) ? FALSE : TRUE);
    }

    if (!parseBoolean(nexusProperties.getProperty(JWT_ENABLED))) {
      // If JWT is not enabled, then disable OAuth2 as well
      nexusProperties.put(NEXUS_SECURITY_OAUTH2_ENABLED, FALSE);
    }
  }

  private static void ensureHACIsDisabled(final NexusProperties nexusProperties) {
    if (parseBoolean(nexusProperties.getProperty("nexus.clustered"))) {
      throw new IllegalStateException(
          "High Availability Clustering (HA-C) is a legacy feature and is no longer supported");
    }
  }

  private static void selectDefaults(final NexusProperties nexusProperties) {
    if (nexusProperties.getProperty("nexus.onboarding.enabled") == null) {
      nexusProperties.put("nexus.onboarding.enabled", TRUE);
    }
    if (nexusProperties.getProperty("nexus.scripts.allowCreation") == null) {
      nexusProperties.put("nexus.scripts.allowCreation", FALSE);
    }
    if (nexusProperties.getProperty("nexus.http.denyframe.enabled") == null) {
      nexusProperties.put("nexus.http.denyframe.enabled", TRUE);
    }
  }

  private static void maybeApplyEnvironmentVariable(
      final NexusProperties nexusProperties,
      final String propertyKey,
      final String environmentKey)
  {
    String envStr = System.getenv(environmentKey);

    if (envStr != null) {
      nexusProperties.put(propertyKey, envStr);
    }
  }

  private static void requireProperty(final NexusProperties nexusProperties, final String name) {
    if (nexusProperties.getProperty(name) == null) {
      throw new IllegalStateException("Missing required property: " + name);
    }
  }
}
