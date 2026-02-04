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
package org.sonatype.nexus.internal.web;

import java.io.IOException;

import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import javax.ws.rs.core.Response.Status;

import org.sonatype.nexus.common.template.TemplateHelper;
import org.sonatype.nexus.common.template.TemplateParameters;
import org.sonatype.nexus.common.template.TemplateThrowableAdapter;
import org.sonatype.nexus.servlet.ServletHelper;
import org.sonatype.nexus.servlet.XFrameOptions;

import jakarta.annotation.Nullable;
import org.apache.shiro.web.servlet.ShiroHttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

/**
 * Service to write a rendered HTML error page
 */
@Component
public class ErrorPageService
    extends PageServiceSupport
{
  private static final String TEMPLATE_RESOURCE = "errorPageHtml.vm";

  @Autowired
  public ErrorPageService(final TemplateHelper templateHelper, final XFrameOptions xFrameOptions) {
    super(templateHelper, xFrameOptions, TEMPLATE_RESOURCE);
  }

  /**
   * Write an HTML error page to the provided {@link HttpServletResponse}
   *
   * @param errorInfo a description of the error
   * @param request the inbound request
   * @param response the response to write to
   *
   * @throws IOException when a failure occurs writing to the response
   */
  public void writeErrorResponse(
      final ErrorInfo errorInfo,
      final HttpServletRequest request,
      final HttpServletResponse response) throws IOException
  {
    log.debug("Writing error response for {}", errorInfo);

    Integer errorCode = errorInfo.code;
    String errorMessage = errorInfo.message;

    // this happens if someone browses directly to the error page
    if (errorCode == null) {
      errorCode = SC_NOT_FOUND;
      errorMessage = "Not found";
    }

    // maintain custom status message when (re)setting the status code,
    // we can't use sendError because it doesn't allow custom html body
    if (errorMessage == null) {
      response.setStatus(errorCode);
      errorMessage = "Unknown error";
    }
    else {
      response.setStatus(errorCode, errorMessage);
      ServletResponse resp = response;
      if (response instanceof ShiroHttpServletResponse) {
        resp = ((ShiroHttpServletResponse) response).getResponse();
        while (resp instanceof HttpServletResponseWrapper) {
          resp = ((HttpServletResponseWrapper) resp).getResponse();
        }
      }
      if (resp instanceof org.eclipse.jetty.ee8.nested.Response) {
        ((org.eclipse.jetty.ee8.nested.Response) resp).setStatusWithReason(errorCode, errorMessage);
      }
    }

    TemplateParameters params = templateHelper.parameters();
    params.set("errorCode", errorCode);
    params.set("errorName", Status.fromStatusCode(errorCode).getReasonPhrase());
    params.set("errorDescription", errorMessage);

    // add cause if ?debug enabled and there is an exception
    if (errorInfo.cause != null && ServletHelper.isDebug(request)) {
      params.set("errorCause", new TemplateThrowableAdapter(errorInfo.cause));
    }

    writeResponseWithoutCaching(params, request, response);
  }

  /**
   * @param code the status code
   * @param message a description of the problem to be rendered to the user
   * @param cause an optional cause, will only be rendered when debug is enabled and allowed
   */
  public static record ErrorInfo(Integer code, String message, @Nullable Throwable cause)
  {
    public ErrorInfo(final Integer code, final String message) {
      this(code, message, null);
    }
  }
}
