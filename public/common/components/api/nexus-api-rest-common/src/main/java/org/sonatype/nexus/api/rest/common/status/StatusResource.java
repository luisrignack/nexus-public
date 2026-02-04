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
package org.sonatype.nexus.api.rest.common.status;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.common.app.FreezeService;
import org.sonatype.nexus.common.log.ExceptionSummarizer;
import org.sonatype.nexus.rest.Resource;

import com.codahale.metrics.annotation.Timed;

import static com.google.common.base.Preconditions.checkNotNull;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.ok;
import static javax.ws.rs.core.Response.status;
import static javax.ws.rs.core.Response.Status.SERVICE_UNAVAILABLE;
import static org.sonatype.nexus.common.log.ExceptionSummarizer.sameType;
import static org.sonatype.nexus.common.log.ExceptionSummarizer.summarize;
import static org.sonatype.nexus.common.log.ExceptionSummarizer.warn;
import static org.sonatype.nexus.rest.APIConstants.V1_API_PREFIX;
import org.springframework.stereotype.Component;

/**
 * @since 3.15
 */
@Component
@Singleton
@Path(StatusResource.RESOURCE_URI)
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
public class StatusResource
    extends ComponentSupport
    implements Resource, StatusResourceDoc
{
  public static final String RESOURCE_URI = V1_API_PREFIX + "/status";

  private final FreezeService freezeService;

  private final ExceptionSummarizer exceptionSummarizer = summarize(sameType(), warn(log));

  @Inject
  public StatusResource(final FreezeService freezeService) {
    this.freezeService = checkNotNull(freezeService);
  }

  @GET
  @Timed
  @Override
  public Response isAvailable() {
    try {
      freezeService.checkReadable("Read check failed");
      return ok().build();
    }
    catch (Exception e) {
      exceptionSummarizer.log("Status health check failed, responding server is unavailable", e);
      return status(SERVICE_UNAVAILABLE).build();
    }
  }

  @GET
  @Path("/writable")
  @Timed
  @Override
  public Response isWritable() {
    try {

      if (freezeService.isFrozen()) {
        log.info("Status health check failed because database is frozen");
        return status(SERVICE_UNAVAILABLE).build();
      }

      freezeService.checkWritable("Write check failed");
      return ok().build();
    }
    catch (Exception e) {
      exceptionSummarizer.log("Status health check failed, responding server is unavailable", e);
      return status(SERVICE_UNAVAILABLE).build();
    }
  }
}
