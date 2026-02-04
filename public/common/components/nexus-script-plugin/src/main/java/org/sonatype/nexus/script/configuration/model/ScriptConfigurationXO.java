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
package org.sonatype.nexus.script.configuration.model;

import java.util.Objects;

import org.sonatype.nexus.script.Script;
import org.sonatype.nexus.script.plugin.internal.ScriptData;

public class ScriptConfigurationXO
{
  private String content;

  private String name;

  private String type;

  public String getContent() {
    return content;
  }

  public void setContent(final String content) {
    this.content = content;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public String getType() {
    return type;
  }

  public void setType(final String type) {
    this.type = type;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ScriptConfigurationXO that = (ScriptConfigurationXO) o;
    return Objects.equals(getContent(), that.getContent()) &&
        Objects.equals(getName(), that.getName()) && Objects.equals(getType(), that.getType());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getContent(), getName(), getType());
  }

  public Script toConfiguration() {
    Script script = new ScriptData();

    script.setName(getName());
    script.setContent(getContent());
    script.setType(getType());

    return script;
  }

  public static ScriptConfigurationXO from(final Script configuration) {
    ScriptConfigurationXO xo = new ScriptConfigurationXO();

    xo.setContent(configuration.getContent());
    xo.setName(configuration.getName());
    xo.setType(configuration.getType());

    return xo;
  }
}
