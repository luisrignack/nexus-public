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
package org.sonatype.nexus.node.upgrade;

import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;

import org.sonatype.nexus.node.datastore.NodeHeartbeat;
import org.sonatype.nexus.node.datastore.NodeHeartbeatManager;
import org.sonatype.nexus.scheduling.TaskSupport;

/**
 * Base class for migration tasks.
 */
public abstract class MigrationTaskSupport
    extends TaskSupport
{
  private static final String NEXUS_STATUS_KEY = "nexus-status";

  private static final String VERSION_KEY = "version";

  private static final String BUILD_REVISION_KEY = "buildRevision";

  protected final NodeHeartbeatManager heartbeatManager;

  public MigrationTaskSupport(
      @Nullable final NodeHeartbeatManager heartbeatManager)
  {
    this.heartbeatManager = heartbeatManager;
  }

  /**
   * Checks if any nodes in the cluster are running different versions, Returns false in OSS/single-node
   * deployments (no HA).
   *
   * @return true if any nodes have different version/buildRevision, false otherwise
   */
  protected boolean oldNodesRunning() {
    if (heartbeatManager == null) {
      return false;
    }

    return heartbeatManager.getActiveNodeHeartbeatData()
        .stream()
        .map(NodeHeartbeat::systemInfo)
        .filter(Objects::nonNull)
        .map(systemInfo -> systemInfo.get(NEXUS_STATUS_KEY))
        .filter(Objects::nonNull)
        .filter(nexusStatus -> nexusStatus instanceof Map)
        .map(nexusStatus -> (Map<String, String>) nexusStatus)
        .map(nexusStatus -> {
          String version = nexusStatus.get(VERSION_KEY);
          String buildRevision = nexusStatus.get(BUILD_REVISION_KEY);
          return version + "|" + buildRevision;
        })
        .distinct()
        .count() > 1L;
  }
}
