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
package org.sonatype.nexus.coreui.internal.compat;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.sonatype.nexus.rest.Resource;
import org.springframework.stereotype.Component;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

/**
 * Empty responses for internal UI endpoints whose data producers live only in commercial editions.
 * Returning the documented empty shapes keeps the Core UI free of 404s without pretending to offer
 * repository-health or malware data that this build does not collect.
 */
@Component
@Produces(APPLICATION_JSON)
@Path("/internal/ui")
public class ProCompatibilityResource
    implements Resource
{
  @RequiresAuthentication
  @GET
  @Path("/healthcheck/summary")
  public List<Map<String, Object>> healthCheckSummary() {
    return Collections.emptyList();
  }

  @RequiresAuthentication
  @GET
  @Path("/malware/counts")
  public Map<String, Object> malwareCounts() {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("counts", Collections.emptyMap());
    result.put("totalCount", 0);
    result.put("hdsAvailable", false);
    result.put("hcEnabledRepos", Collections.emptyList());
    return result;
  }
}
