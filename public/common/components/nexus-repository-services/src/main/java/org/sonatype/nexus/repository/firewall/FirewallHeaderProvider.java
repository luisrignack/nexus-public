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
package org.sonatype.nexus.repository.firewall;

import org.sonatype.nexus.repository.view.Request;

import com.google.common.collect.ListMultimap;
import org.apache.http.HttpResponse;

/**
 * Provider interface for firewall-related HTTP headers that should be propagated through proxy responses.
 * <p>
 * Implementations of this interface can specify which HTTP headers are relevant for firewall error reporting and should
 * be included when proxying responses that contain firewall-related errors.
 * <p>
 * This interface allows the repository-services module to remain independent of private firewall implementation details
 * while still supporting dynamic header propagation.
 */
public interface FirewallHeaderProvider
{
  /**
   * builds the set of HTTP header names that should be propagated when firewall errors occur.
   * <p>
   * For example, a firewall implementation might return headers like:
   * <ul>
   * <li>"X-NuGet-Warning" for NuGet repositories</li>
   * <li>"X-Error-Code" and "X-Error-Message" for Hugging Face repositories</li>
   * </ul>
   */
  void addHeaders(
      final String repositoryFormat,
      final Request request,
      final HttpResponse httpResponse,
      ListMultimap<String, String> headers);

  String originatingUserAgent(final Request request);
}
