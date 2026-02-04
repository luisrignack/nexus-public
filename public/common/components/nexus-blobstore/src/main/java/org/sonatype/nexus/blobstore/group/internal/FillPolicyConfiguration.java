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
package org.sonatype.nexus.blobstore.group.internal;

import org.sonatype.nexus.blobstore.group.FillPolicy;

import jakarta.inject.Provider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FillPolicyConfiguration
{
  @Bean
  public Provider<FillPolicy> roundRobinFillPolicyProvider(final ObjectProvider<RoundRobinFillPolicy> provider) {
    return new RoundRobinFillPolicyProvider(provider);
  }

  @Bean
  public Provider<FillPolicy> writeToFirstFillPolicyProvider(
      final ObjectProvider<WriteToFirstMemberFillPolicy> provider)
  {
    return new WriteToFirstMemberFillPolicyProvider(provider);
  }

  @Qualifier(WriteToFirstMemberFillPolicy.TYPE)
  private record WriteToFirstMemberFillPolicyProvider(ObjectProvider<WriteToFirstMemberFillPolicy> provider)
      implements Provider<FillPolicy>
  {
    @Override
    public FillPolicy get() {
      return provider.getIfUnique();
    }
  }

  @Qualifier(RoundRobinFillPolicy.TYPE)
  private record RoundRobinFillPolicyProvider(ObjectProvider<RoundRobinFillPolicy> provider)
      implements Provider<FillPolicy>
  {
    @Override
    public FillPolicy get() {
      return provider.getIfUnique();
    }
  }
}
