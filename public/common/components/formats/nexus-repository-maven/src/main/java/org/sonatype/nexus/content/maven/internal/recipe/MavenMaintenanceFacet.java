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
package org.sonatype.nexus.content.maven.internal.recipe;

import java.util.Set;
import java.util.stream.Stream;

import org.sonatype.nexus.content.maven.MavenContentFacet;
import org.sonatype.nexus.repository.content.Component;
import org.sonatype.nexus.repository.content.fluent.FluentComponent;
import org.sonatype.nexus.repository.content.maintenance.LastAssetMaintenanceFacet;
import org.sonatype.nexus.repository.maven.internal.Maven2Format;

import com.google.common.collect.Sets;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;

import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE;

/**
 * @since 3.29
 */
@Qualifier(Maven2Format.NAME)
@Scope(SCOPE_PROTOTYPE)
@org.springframework.stereotype.Component
public class MavenMaintenanceFacet
    extends LastAssetMaintenanceFacet
{
  @Override
  public Set<String> deleteComponent(final Component component) {
    return Sets.union(super.deleteComponent(component), mavenContentFacet().deleteMetadataOrFlagForRebuild(component));
  }

  @Override
  public int deleteComponents(final Stream<FluentComponent> components) {
    return mavenContentFacet().deleteComponents(components);
  }

  private MavenContentFacet mavenContentFacet() {
    return facet(MavenContentFacet.class);
  }
}
