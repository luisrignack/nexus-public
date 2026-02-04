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
package org.sonatype.nexus.outbound.context;

import org.junit.Before;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.*;

public class OutboundRequestContextTest
{

  @Before
  public void setUp() {
    // Clear the context before each test to ensure isolation
    OutboundRequestContext.remove();
  }

  @After
  public void tearDown() {
    // Clear the context after each test to avoid interference
    OutboundRequestContext.remove();
  }

  @Test
  public void testSetAndGetDownloadTime() {
    long downloadTime = 12345L;
    OutboundRequestContext.setDownloadTime(downloadTime);

    Long retrievedTime = OutboundRequestContext.getDownloadTime();
    assertNotNull("Download time should not be null", retrievedTime);
    assertEquals("Download time should match the set value", downloadTime, retrievedTime.longValue());
  }

  @Test
  public void testSetAndGetFormattedString() {
    String formattedString = "Test formatted string";
    OutboundRequestContext.setFormattedString(formattedString);

    String retrievedString = OutboundRequestContext.getFormattedString();
    assertNotNull("Formatted string should not be null", retrievedString);
    assertEquals("Formatted string should match the set value", formattedString, retrievedString);
  }

  @Test
  public void testRemoveClearsContext() {
    OutboundRequestContext.setDownloadTime(12345L);
    OutboundRequestContext.setFormattedString("Test formatted string");

    OutboundRequestContext.remove();

    assertNull("Download time should be null after remove", OutboundRequestContext.getDownloadTime());
    assertNull("Formatted string should be null after remove", OutboundRequestContext.getFormattedString());
  }

  @Test
  public void testGetContextMapSize() {
    assertEquals("Initial context map size should be 0", 0, OutboundRequestContext.getContextMapSize());

    OutboundRequestContext.setDownloadTime(12345L);
    assertEquals("Context map size should be 1 after setting download time", 1,
        OutboundRequestContext.getContextMapSize());

    OutboundRequestContext.setFormattedString("Test formatted string");
    assertEquals("Context map size should be 2 after setting formatted string", 2,
        OutboundRequestContext.getContextMapSize());
  }
}
