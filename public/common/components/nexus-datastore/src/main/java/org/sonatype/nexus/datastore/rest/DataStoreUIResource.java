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
package org.sonatype.nexus.datastore.rest;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.sonatype.nexus.datastore.DataStoreConfigurationManager;
import org.sonatype.nexus.datastore.api.DataStoreConfiguration;
import org.sonatype.nexus.rest.Resource;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

/**
 * Internal UI resource for the database configuration screen. Uses the public
 * {@link DataStoreConfigurationManager} API; it does not expose the password attribute.
 */
@Named
@Singleton
@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Path("/internal/ui/datastore")
public class DataStoreUIResource
    implements Resource
{
  private static final String DEFAULT_STORE = "nexus";

  private static final String ADVANCED = "advanced";

  private static final String MAXIMUM_POOL_SIZE = "maximumPoolSize";

  private final DataStoreConfigurationManager manager;

  @Inject
  public DataStoreUIResource(final DataStoreConfigurationManager manager) {
    this.manager = manager;
  }

  @GET
  @RequiresAuthentication
  @RequiresPermissions("nexus:settings:read")
  public Map<String, Object> get() {
    DataStoreConfiguration config = findConfig();
    if (config == null) {
      return emptyConfig();
    }
    return toXO(config);
  }

  @PUT
  @RequiresAuthentication
  @RequiresPermissions("nexus:settings:update")
  public Response update(final Map<String, Object> payload) {
    DataStoreConfiguration config = findConfig();
    if (config == null) {
      return Response.status(Response.Status.NOT_FOUND).entity("Data store not found").build();
    }

    Map<String, String> attributes = config.getAttributes();
    String advanced = payload.get(ADVANCED) instanceof String
        ? (String) payload.get(ADVANCED)
        : attributes.getOrDefault(ADVANCED, "");
    Integer pool = toInteger(payload.get("maximumConnectionPool"));
    if (pool != null) {
      advanced = setPoolSize(advanced, pool);
    }
    attributes.put(ADVANCED, advanced);
    manager.save(config);
    return Response.ok(toXO(config)).build();
  }

  @Nullable
  private DataStoreConfiguration findConfig() {
    DataStoreConfiguration fallback = null;
    for (DataStoreConfiguration config : manager.load()) {
      if (DEFAULT_STORE.equalsIgnoreCase(config.getName())) {
        return config;
      }
      if (fallback == null) {
        fallback = config;
      }
    }
    return fallback;
  }

  private Map<String, Object> toXO(final DataStoreConfiguration config) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("name", config.getName());
    result.put("source", config.getSource());
    result.put("type", config.getType());

    Map<String, String> attributes = config.getAttributes();
    result.put("jdbcUrl", redactUrl(attributes.get("jdbcUrl")));
    result.put("username", attributes.getOrDefault("username", ""));
    result.put("schema", attributes.getOrDefault("schema", ""));
    result.put("password", DataStoreConfiguration.REDACTED);

    String advanced = attributes.getOrDefault(ADVANCED, "");
    result.put(ADVANCED, advanced);
    result.put("maximumConnectionPool", readPoolSize(advanced));
    return result;
  }

  private Map<String, Object> emptyConfig() {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("name", DEFAULT_STORE);
    result.put("source", "");
    result.put("type", "");
    result.put("jdbcUrl", "");
    result.put("username", "");
    result.put("schema", "");
    result.put("password", DataStoreConfiguration.REDACTED);
    result.put(ADVANCED, "");
    result.put("maximumConnectionPool", 10);
    return result;
  }

  @Nullable
  private static Integer toInteger(final Object value) {
    if (value instanceof Number) {
      return ((Number) value).intValue();
    }
    if (value instanceof String) {
      try {
        return Integer.valueOf((String) value);
      }
      catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }

  private static int readPoolSize(final String advanced) {
    Integer pool = parseAdvanced(advanced).entrySet().stream()
        .filter(e -> MAXIMUM_POOL_SIZE.equalsIgnoreCase(e.getKey()))
        .map(e -> toInteger(e.getValue()))
        .findFirst()
        .orElse(null);
    return pool != null && pool > 0 ? pool : 10;
  }

  private static String setPoolSize(final String advanced, final int pool) {
    Map<String, String> parsed = parseAdvanced(advanced);
    parsed.put(MAXIMUM_POOL_SIZE, String.valueOf(pool));
    StringBuilder sb = new StringBuilder();
    parsed.forEach((key, value) -> sb.append(key).append('=').append(value).append('\n'));
    return sb.toString().trim();
  }

  private static Map<String, String> parseAdvanced(final String advanced) {
    Map<String, String> parsed = new LinkedHashMap<>();
    if (advanced == null) {
      return parsed;
    }
    for (String line : advanced.split("[\\r\\n]+")) {
      int idx = line.indexOf('=');
      if (idx > 0) {
        parsed.put(line.substring(0, idx).trim(), line.substring(idx + 1).trim());
      }
    }
    return parsed;
  }

  private static String redactUrl(@Nullable final String url) {
    if (url == null) {
      return "";
    }
    int idx = url.indexOf("://");
    if (idx < 0) {
      return url;
    }
    return url.substring(0, idx + 3) + "**REDACTED**";
  }
}
