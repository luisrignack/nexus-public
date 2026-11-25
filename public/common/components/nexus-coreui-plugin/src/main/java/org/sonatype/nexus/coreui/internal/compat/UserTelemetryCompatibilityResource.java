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
import java.util.Map;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import org.sonatype.nexus.rest.Resource;
import org.springframework.stereotype.Component;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

/**
 * No-op implementation of the user-telemetry endpoints referenced by the boot page. The real
 * telemetry service is a commercial service; this resource only prevents a guaranteed 404 and
 * keeps the page's initialization callback satisfied.
 */
@Component
@Path("/v1/user-telemetry")
public class UserTelemetryCompatibilityResource
    implements Resource
{
  private static final String NOOP_SCRIPT =
      "window.userTelemetry = window.userTelemetry || { initialize: function () {} };";

  @GET
  @Path("/javascript")
  @Produces("application/javascript")
  public Response javascript() {
    return Response.ok(NOOP_SCRIPT).build();
  }

  @GET
  @Path("/config")
  @Produces(APPLICATION_JSON)
  public Map<String, Object> config() {
    return Collections.emptyMap();
  }

  @POST
  @Path("/events")
  @Consumes(APPLICATION_JSON)
  public Response events(final String payload) {
    return Response.noContent().build();
  }
}
