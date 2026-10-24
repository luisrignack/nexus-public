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

import jakarta.inject.Singleton;

import org.sonatype.nexus.repository.Format;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Docker repository format.
 *
 * <p>Docker repositories are exposed by {@link DockerRegistryServlet} under {@code /v2/<repository>/...}. Every
 * repository of this format selects its own upstream registry, so the global upstream selector documented in
 * {@link DockerRegistryConfig} keeps working as a fallback.</p>
 */
@Component
@Qualifier(DockerFormat.NAME)
@Singleton
public class DockerFormat
    extends Format
{
  public static final String NAME = "docker";

  public DockerFormat() {
    super(NAME);
  }
}
