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
package org.sonatype.nexus.repository.docker.internal;

import java.io.IOException;

import jakarta.inject.Inject;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import jakarta.inject.Singleton;

import org.springframework.stereotype.Component;

/**
 * Exposes the Docker Registry v2 API at {@code /v2/}.
 *
 * <p>Nexus' bootstrap discovers {@link HttpServlet} beans annotated with {@link WebServlet} and registers them with
 * Jetty, which is what makes the standard registry path work without the {@code /repository/<name>} prefix used by
 * other formats. Docker clients can therefore use {@code localhost:8081} directly:</p>
 *
 * <pre>
 * docker login localhost:8081
 * docker pull localhost:8081/library/nginx
 * </pre>
 */
@Component
@Singleton
@WebServlet(name = "dockerRegistryServlet", urlPatterns = {"/v2", "/v2/*"}, asyncSupported = true)
public class DockerRegistryServlet
    extends HttpServlet
{
  private final DockerRegistryService service;

  @Inject
  public DockerRegistryServlet(final DockerRegistryService service) {
    this.service = service;
  }

  @Override
  protected void service(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
    service.handle(request, response);
  }
}
