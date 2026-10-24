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
package org.sonatype.nexus.content.npm.internal.recipe;

import jakarta.inject.Singleton;

import org.sonatype.goodies.i18n.I18N;
import org.sonatype.goodies.i18n.MessageBundle;
import org.sonatype.nexus.common.upgrade.AvailabilityVersion;
import org.sonatype.nexus.content.npm.NpmContentFacet;
import org.sonatype.nexus.formfields.RepositoryCombobox;
import org.sonatype.nexus.repository.RepositoryTaskSupport;
import org.sonatype.nexus.scheduling.TaskDescriptorSupport;

import org.springframework.stereotype.Component;

/**
 * Descriptor for {@link NpmRebuildMetadataTask}.
 */
@AvailabilityVersion(from = "1.0")
@Component
@Singleton
public class NpmRebuildMetadataTaskDescriptor
    extends TaskDescriptorSupport
{
  public static final String TYPE_ID = "repository.npm-rebuild-metadata";

  public static final String REPOSITORY_NAME_FIELD_ID = RepositoryTaskSupport.REPOSITORY_NAME_FIELD_ID;

  private interface Messages
      extends MessageBundle
  {
    @DefaultMessage("Repair - Rebuild npm metadata")
    String name();

    @DefaultMessage("Repository")
    String repositoryLabel();
  }

  private static final Messages messages = I18N.create(Messages.class);

  public NpmRebuildMetadataTaskDescriptor() {
    super(
        TYPE_ID,
        NpmRebuildMetadataTask.class,
        messages.name(),
        VISIBLE,
        EXPOSED,
        new RepositoryCombobox(
            REPOSITORY_NAME_FIELD_ID,
            messages.repositoryLabel(),
            "Select the npm repository to rebuild metadata for",
            true).includingAnyOfFacets(NpmContentFacet.class).includeAnEntryForAllRepositories());
  }

  @Override
  public boolean allowConcurrentRun() {
    return false;
  }
}
