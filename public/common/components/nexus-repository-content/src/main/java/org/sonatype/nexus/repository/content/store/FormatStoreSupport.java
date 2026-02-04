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
package org.sonatype.nexus.repository.content.store;

import org.sonatype.nexus.repository.content.browse.store.BrowseNodeDAO;
import org.sonatype.nexus.repository.content.browse.store.BrowseNodeStore;

public abstract class FormatStoreSupport<CONTENT_REPOSITORY_DAO extends ContentRepositoryDAO, COMPONENT_DAO extends ComponentDAO, ASSET_DAO extends AssetDAO, ASSET_BLOB_DAO extends AssetBlobDAO, BROWSE_NODE_DAO extends BrowseNodeDAO>
    extends
    BespokeFormatStoreSupport<ContentRepositoryStore<CONTENT_REPOSITORY_DAO>, ComponentStore<COMPONENT_DAO>, AssetStore<ASSET_DAO>, AssetBlobStore<ASSET_BLOB_DAO>, BrowseNodeStore<BROWSE_NODE_DAO>>
{

}
