/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 */
package org.sonatype.nexus.coreui.internal.ldap;

import org.sonatype.nexus.rapture.UiPluginDescriptor;
import org.sonatype.nexus.rapture.UiPluginDescriptorSupport;

import org.springframework.stereotype.Component;

/**
 * Activates the classic {@code NX.coreui.controller.LdapServers} controller. The LDAP backend is
 * implemented by the fork, so the bundle only needs to exist for the ExtJS UI to enable the screen.
 */
@Component
public class LdapPluginDescriptorImpl
    extends UiPluginDescriptorSupport
    implements UiPluginDescriptor
{
  public LdapPluginDescriptorImpl() {
    super("nexus-ldap-plugin");
  }
}
