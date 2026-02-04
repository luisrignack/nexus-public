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
package org.sonatype.nexus.jetty.log;

import java.util.Locale;
import java.util.TimeZone;
import java.util.function.LongSupplier;

import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.server.CustomRequestLog;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.DateCache;

public class NexusRepositoryRequestLog
    extends CustomRequestLog
{
  private static final DateCache DATE_CACHE = new DateCache("dd/MMM/yyyy:HH:mm:ss Z",
      Locale.getDefault(),
      TimeZone.getDefault());

  private final LongSupplier timeSupplier;

  public NexusRepositoryRequestLog(RequestLog.Writer writer, String formatString) {
    this(writer, formatString, System::currentTimeMillis);
  }

  public NexusRepositoryRequestLog(RequestLog.Writer writer, String formatString, LongSupplier timeMillisSupplier) {
    super(writer, formatString);
    this.timeSupplier = timeMillisSupplier;
  }

  @Override
  public void log(Request request, Response response) {
    request.setAttribute("threadName", Thread.currentThread().getName());
    request.setAttribute("responseTimestamp", DATE_CACHE.format(timeSupplier.getAsLong()));

    // Check if a sanitized URI was provided by the handler (e.g., Terraform authentication handler)
    // This allows format-specific handlers to sanitize sensitive data before logging
    Object sanitizedUri = request.getAttribute("sanitizedRequestUri");
    if (sanitizedUri != null) {
      Request sanitizedRequest = new SanitizedUriRequestWrapper(request, sanitizedUri.toString());
      super.log(sanitizedRequest, response);
    }
    else {
      super.log(request, response);
    }
  }

  /**
   * Returns the default time zone ID. This method is used in
   * `jetty-requestlog.xml` to dynamically set the time zone for request
   * logs to the system's default time zone.
   *
   * @return The ID of the default time zone.
   */
  public static String getDefaultTimeZoneId() {
    return TimeZone.getDefault().getID();
  }

  /**
   * Request wrapper that overrides the URI path with a sanitized version for logging.
   * This allows format-specific handlers (e.g., Terraform) to provide pre-sanitized URIs
   * without affecting the actual request processing.
   */
  private static class SanitizedUriRequestWrapper
      extends Request.Wrapper
  {
    private final HttpURI sanitizedUri;

    public SanitizedUriRequestWrapper(Request wrapped, String sanitizedPath) {
      super(wrapped);
      HttpURI originalUri = wrapped.getHttpURI();

      // Build a new HttpURI with the sanitized path, keeping other components unchanged
      this.sanitizedUri = HttpURI.build(originalUri)
          .path(sanitizedPath)
          .query(originalUri.getQuery());
    }

    @Override
    public HttpURI getHttpURI() {
      return sanitizedUri;
    }
  }
}
