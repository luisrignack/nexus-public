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
package org.sonatype.nexus.content.yum.internal.browse;

import jakarta.inject.Singleton;

import org.sonatype.nexus.repository.content.browse.AssetPathBrowseNodeGenerator;
import org.sonatype.nexus.repository.yum.internal.YumFormat;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Places Yum components at the same level as their assets.
 */
@Component
@Singleton
@Qualifier(YumFormat.NAME)
public class YumBrowseNodeGenerator
    extends AssetPathBrowseNodeGenerator
{
}
