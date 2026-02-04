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
package org.sonatype.nexus.api.rest.selfhosted.status;

import javax.ws.rs.GET;

import com.codahale.metrics.health.HealthCheck.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.util.SortedMap;

@Api("Status")
public interface StatusCheckResourceDoc
{
  @GET
  @ApiOperation("Health check endpoint that returns the results of the system status checks")
  @ApiResponses({
      @ApiResponse(code = 200, message = "The system status check results", response = Result.class,
          responseContainer = "Map")
  })
  SortedMap<String, Result> getSystemStatusChecks();
}
