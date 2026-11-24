/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 */
package org.sonatype.nexus.coreui.internal.saml;

import org.sonatype.nexus.rapture.UiPluginDescriptor;
import org.sonatype.nexus.rapture.UiPluginDescriptorSupport;

import org.springframework.stereotype.Component;

/**
 * Makes the SAML configuration screens visible by activating the {@code nexus-saml-plugin} bundle.
 */
@Component
public class SamlPluginDescriptorImpl
    extends UiPluginDescriptorSupport
    implements UiPluginDescriptor
{
  public SamlPluginDescriptorImpl() {
    super("nexus-saml-plugin");
  }
}
