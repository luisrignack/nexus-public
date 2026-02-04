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
package org.sonatype.nexus.bootstrap.entrypoint.edition;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

public class NexusEditionSelectorTest
{
  private NexusEdition testEdition1;

  private NexusEdition testEdition2;

  private NexusEditionSelector underTest;

  @BeforeEach
  public void setup() {
    testEdition1 = new TestEdition1();
    testEdition2 = new TestEdition2();
    List<NexusEdition> editions = List.of(testEdition1, testEdition2);
    underTest = new NexusEditionSelector(editions);
  }

  @Test
  public void testGetCurrent() {
    assertThat(underTest.getCurrent(), not(nullValue()));
    assertThat(underTest.getCurrent(), is(testEdition1));
  }

  @Test
  public void testGetCurrent_swapEditionOrder() {
    List<NexusEdition> editions = List.of(testEdition2, testEdition1);
    underTest = new NexusEditionSelector(editions);

    // still same results
    assertThat(underTest.getCurrent(), not(nullValue()));
    assertThat(underTest.getCurrent(), is(testEdition1));
  }

  private static class TestEdition1
      extends NexusEditionSupport
  {
    public TestEdition1() {
      super("test-1", "TEST 1", "TEST1", 100);
    }

    @Override
    public boolean isActive() {
      return true;
    }
  }

  private static class TestEdition2
      extends NexusEditionSupport
  {
    public TestEdition2() {
      super("test-2", "TEST 2", "TEST2", 200);
    }

    @Override
    public boolean isActive() {
      return true;
    }
  }
}
