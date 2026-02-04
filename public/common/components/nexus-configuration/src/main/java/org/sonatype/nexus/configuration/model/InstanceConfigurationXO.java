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
package org.sonatype.nexus.configuration.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InstanceConfigurationXO
{
  private static final Logger LOG = LoggerFactory.getLogger(InstanceConfigurationXO.class);

  private List<ConfigurationXO> configurationXOs = new ArrayList<>();

  public void setConfigurationXOs(final List<ConfigurationXO> configurationXOs) {
    this.configurationXOs = configurationXOs.stream()
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  public List<ConfigurationXO> getConfigurationXOs() {
    return configurationXOs;
  }

  public void addConfigurationXO(final ConfigurationXO configurationXO) {
    if (configurationXO == null) {
      LOG.debug("Ignoring null configurationXO");
    }
    else {
      configurationXOs.add(configurationXO);
    }
  }

  public ConfigurationXO getConfigurationXO(final String typeId) {
    for (ConfigurationXO configurationXO : configurationXOs) {
      if (typeId.equals(configurationXO.getConfigurationTypeId())) {
        return configurationXO;
      }
    }
    return null;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    InstanceConfigurationXO that = (InstanceConfigurationXO) o;
    return Objects.equals(getConfigurationXOs(), that.getConfigurationXOs());
  }

  @Override
  public int hashCode() {
    return getConfigurationXOs() != null ? getConfigurationXOs().hashCode() : 0;
  }
}
