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
//
// ========================================================================
// Copyright (c) 1995 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License v. 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//
package org.eclipse.jetty.server;

import org.eclipse.jetty.http.*;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.util.Attributes;
import org.eclipse.jetty.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public class CustomErrorHandler extends ErrorHandler {

  private static final Logger LOG = LoggerFactory.getLogger(CustomErrorHandler.class);
  HttpField _cacheControl = new PreEncodedHttpField(HttpHeader.CACHE_CONTROL, "must-revalidate,no-cache,no-store");

  @Override
  public boolean handle(Request request, Response response, Callback callback) throws Exception {
    if (LOG.isDebugEnabled())
      LOG.debug("handle({}, {}, {})", request, response, callback);
    if (_cacheControl != null)
      response.getHeaders().put(_cacheControl);

    int code = response.getStatus();
    Throwable cause = (Throwable) request.getAttribute(ERROR_EXCEPTION);
    if (cause instanceof HttpException httpException) {
      code = httpException.getCode();
      response.setStatus(code);
    }
    if (!errorPageForMethod(request.getMethod()) || HttpStatus.hasNoBody(code)) {
      callback.succeeded();
    } else {
      String message = HttpStatus.getMessage(code);
      generateResponse(request, response, code, message, cause, callback);
    }
    return true;
  }

  public static class ErrorRequest extends Request.AttributesWrapper
  {
    private static final Set<String> ATTRIBUTES = Set.of(ERROR_MESSAGE, ERROR_EXCEPTION, ERROR_STATUS);

    public ErrorRequest(Request request, int status, String message, Throwable cause)
    {
      super(request, new Attributes.Synthetic(request)
      {
        @Override
        protected Object getSyntheticAttribute(String name)
        {
          return switch (name)
          {
            case ERROR_MESSAGE -> message;
            case ERROR_EXCEPTION -> cause;
            case ERROR_STATUS -> status;
            default -> null;
          };
        }

        @Override
        protected Set<String> getSyntheticNameSet()
        {
          return ATTRIBUTES;
        }
      });
    }

    @Override
    public Content.Chunk read()
    {
      return Content.Chunk.EOF;
    }

    @Override
    public void demand(Runnable demandCallback)
    {
      demandCallback.run();
    }

    @Override
    public String toString()
    {
      return "%s@%x:%s".formatted(getClass().getSimpleName(), hashCode(), getWrapped());
    }
  }

}


