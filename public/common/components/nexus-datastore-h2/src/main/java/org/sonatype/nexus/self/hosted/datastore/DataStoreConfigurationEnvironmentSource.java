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
package org.sonatype.nexus.self.hosted.datastore;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.inject.Singleton;

import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.datastore.DataStoreConfigurationSource;
import org.sonatype.nexus.datastore.api.DataStoreConfiguration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Supplies data store configurations from environment variables and system properties.
 *
 * <p>This is what makes an external database (PostgreSQL) usable without editing internal configuration files.
 * The data store name {@code nexus} is configured with:</p>
 *
 * <pre>
 * NEXUS_DATASTORE_NEXUS_JDBCURL=jdbc:postgresql://localhost:5432/nexus
 * NEXUS_DATASTORE_NEXUS_USERNAME=nexus
 * NEXUS_DATASTORE_NEXUS_PASSWORD=secret
 * NEXUS_DATASTORE_NEXUS_SCHEMA=public
 * NEXUS_DATASTORE_NEXUS_ADVANCED="maximumPoolSize=20"
 * </pre>
 *
 * <p>The equivalent system properties are {@code nexus.datastore.nexus.jdbcUrl},
 * {@code nexus.datastore.nexus.username}, and so on. Environment variables win over system properties when both
 * are present. This source has the highest priority, so it overrides the embedded H2 defaults.</p>
 */
@Component
@Qualifier(DataStoreConfigurationEnvironmentSource.SOURCE)
@Order(DataStoreConfigurationSource.ORDER_ENVIRONMENT_VALUES)
@Singleton
public class DataStoreConfigurationEnvironmentSource
    extends ComponentSupport
    implements DataStoreConfigurationSource
{
  public static final String SOURCE = "environment";

  private static final String ENV_PREFIX = "NEXUS_DATASTORE_";

  private static final String SYSTEM_PREFIX = "nexus.datastore.";

  private static final Pattern ENV_PATTERN =
      Pattern.compile("^NEXUS_DATASTORE_([A-Z0-9_]+)_(JDBCURL|USERNAME|PASSWORD|SCHEMA|TYPE|ADVANCED)$");

  private static final String[][] ATTRIBUTES = {
      {"JDBCURL", "jdbcUrl"},
      {"USERNAME", "username"},
      {"PASSWORD", "password"},
      {"SCHEMA", "schema"},
      {"TYPE", "type"},
      {"ADVANCED", "advanced"}
  };

  @Override
  public String getName() {
    return "Environment variables";
  }

  @Override
  public Iterable<String> browseStoreNames() {
    Set<String> stores = new TreeSet<>();
    for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
      Matcher matcher = ENV_PATTERN.matcher(entry.getKey());
      if (matcher.matches() && !entry.getValue().isEmpty()) {
        stores.add(matcher.group(1).toLowerCase(Locale.ROOT));
      }
    }
    for (String name : System.getProperties().stringPropertyNames()) {
      if (name.startsWith(SYSTEM_PREFIX)) {
        String rest = name.substring(SYSTEM_PREFIX.length());
        int dot = rest.indexOf('.');
        if (dot > 0) {
          stores.add(rest.substring(0, dot).toLowerCase(Locale.ROOT));
        }
      }
    }
    return stores;
  }

  @Override
  public DataStoreConfiguration load(final String storeName) {
    Map<String, String> attributes = new LinkedHashMap<>();
    for (String[] attribute : ATTRIBUTES) {
      String value = value(storeName, attribute[0], attribute[1]);
      if (value != null && !value.isEmpty()) {
        attributes.put(attribute[1], value);
      }
    }

    String jdbcUrl = attributes.get("jdbcUrl");
    if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:")) {
      return null;
    }

    DataStoreConfiguration configuration = new DataStoreConfiguration();
    configuration.setName(storeName);
    configuration.setType(attributes.getOrDefault("type", "jdbc"));
    configuration.setSource(SOURCE);
    configuration.setAttributes(attributes);
    return configuration;
  }

  private static String value(final String storeName, final String envSuffix, final String propertyName) {
    String environment = System.getenv(ENV_PREFIX + storeName.toUpperCase(Locale.ROOT) + '_' + envSuffix);
    if (environment != null && !environment.isEmpty()) {
      return environment;
    }
    return System.getProperty(SYSTEM_PREFIX + storeName + '.' + propertyName);
  }

  @Override
  public String toString() {
    return "DataStoreConfigurationEnvironmentSource";
  }
}
