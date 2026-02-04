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
package org.sonatype.nexus.common.conversion;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.beans.PropertyChangeListener;
import java.beans.PropertyEditor;

public abstract class PropertyEditorSupport
    implements PropertyEditor
{
  protected Object value;

  @Override
  public void setAsText(final String text) throws IllegalArgumentException {
    setValue(text);
  }

  @Override
  public Object getValue() {
    return value;
  }

  @Override
  public boolean isPaintable() {
    return false;
  }

  @Override
  public void paintValue(final Graphics gfx, final Rectangle box) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getJavaInitializationString() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getAsText() {
    return value != null ? value.toString() : null;
  }

  @Override
  public String[] getTags() {
    return null;
  }

  @Override
  public Component getCustomEditor() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean supportsCustomEditor() {
    return false;
  }

  @Override
  public void addPropertyChangeListener(final PropertyChangeListener listener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void removePropertyChangeListener(final PropertyChangeListener listener) {
    throw new UnsupportedOperationException();
  }
}
