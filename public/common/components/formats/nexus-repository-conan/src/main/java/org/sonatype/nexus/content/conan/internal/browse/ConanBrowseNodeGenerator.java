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
package org.sonatype.nexus.content.conan.internal.browse;

import jakarta.inject.Singleton;

import org.sonatype.nexus.repository.content.browse.AssetPathBrowseNodeGenerator;
import org.sonatype.nexus.repository.conan.internal.ConanFormat;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Conan places components at the same level as their assets, so the storage tree mirrors
 * {@code conans/name/version/user/channel/revisions/...}.
 *
 * @since 3.89
 */
@Singleton
@Component
@Qualifier(ConanFormat.NAME)
public class ConanBrowseNodeGenerator
    extends AssetPathBrowseNodeGenerator
{
}
