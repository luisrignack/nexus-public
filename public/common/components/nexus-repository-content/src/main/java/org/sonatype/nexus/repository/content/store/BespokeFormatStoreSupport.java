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

import org.sonatype.nexus.repository.content.browse.store.BrowseNodeStore;

import net.bytebuddy.ByteBuddy;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.AutowireCandidateQualifier;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;

@SuppressWarnings({"rawtypes", "unchecked", "unused"})
public abstract class BespokeFormatStoreSupport<CONTENT_REPOSITORY_STORE extends ContentRepositoryStore<?>, COMPONENT_STORE extends ComponentStore<?>, ASSET_STORE extends AssetStore<?>, ASSET_BLOB_STORE extends AssetBlobStore<?>, BROWSE_STORE extends BrowseNodeStore<?>>
    extends ContentStoreBeanDefinitionRegistryPostProcessorSupport
{
  protected BespokeFormatStoreSupport() {
    super(BespokeFormatStoreSupport.class);
  }

  @Override
  public void postProcessBeanDefinitionRegistry(final BeanDefinitionRegistry registry) {
    super.postProcessBeanDefinitionRegistry(registry);

    AbstractBeanDefinition def = BeanDefinitionBuilder.rootBeanDefinition(createType())
        .addConstructorArgValue(formatClassPrefix)
        .setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE)
        .getBeanDefinition();

    def.addQualifier(new AutowireCandidateQualifier(getClass(), def));

    registry.registerBeanDefinition(format + FormatStoreManager.class.getSimpleName(), def);
  }

  private Class<? extends FormatStoreManager> createType() {
    return new ByteBuddy().subclass(FormatStoreManager.class)
        .annotateType(format)
        .make()
        .load(getClass().getClassLoader())
        .getLoaded();
  }
}
