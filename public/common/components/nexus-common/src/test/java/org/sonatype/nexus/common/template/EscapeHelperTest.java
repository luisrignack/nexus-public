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
package org.sonatype.nexus.common.template;

import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class EscapeHelperTest
{
  EscapeHelper underTest;

  @Before
  public void setup() {
    underTest = new EscapeHelper();
  }

  @Test
  public void testStripJavaEl() {
    String test = "${badstuffinhere}";
    String result = underTest.stripJavaEl(test);
    assertThat(result, is("{badstuffinhere}"));
  }

  @Test
  public void testStripJavaEl_multiple_dollar_signs() {
    String test = "$$$$${badstuffinhere}";
    String result = underTest.stripJavaEl(test);
    assertThat(result, is("{badstuffinhere}"));
  }

  @Test
  public void testStripJavaEl_bugged_interpolator() {
    String test = "$\\A{badstuffinhere}";
    String result = underTest.stripJavaEl(test);
    assertThat(result, is("{badstuffinhere}"));
  }

  @Test
  public void testUriSegmentsEncoding() {
    assertThat(underTest.uriSegments("foo/bar+baz"), is("foo/bar+baz"));
    assertThat(underTest.uriSegments("foo/bar%baz"), is("foo/bar%25baz"));
    assertThat(underTest.uriSegments("foo/bar baz"), is("foo/bar%20baz"));
    assertThat(underTest.uriSegments("foo:path/bar:baz"), is("foo%3Apath/bar%3Abaz"));
  }

  @Test
  public void testCustomRules_emptyRules() {
    EscapeHelper customHelper = new EscapeHelper("");

    assertThat(customHelper.uriSegments("foo/bar+baz"), is("foo/bar+baz"));
    assertThat(customHelper.uriSegments("foo/bar%baz"), is("foo/bar%baz"));
    assertThat(customHelper.uriSegments("foo/bar baz"), is("foo/bar baz"));
    assertThat(customHelper.uriSegments("foo:path/bar:baz"), is("foo:path/bar:baz"));
  }

  @Test
  public void testCustomRules_onlyEscapePlus() {
    EscapeHelper customHelper = new EscapeHelper("+:%2B");

    assertThat(customHelper.uriSegments("foo/bar+baz"), is("foo/bar%2Bbaz"));
    assertThat(customHelper.uriSegments("foo/bar%baz"), is("foo/bar%baz"));
    assertThat(customHelper.uriSegments("foo/bar baz"), is("foo/bar baz"));
  }

  @Test
  public void testCustomRules_preserveAlreadyEscaped() {
    EscapeHelper customHelper = new EscapeHelper("");

    assertThat(customHelper.uriSegments("ncurses-c%2B%2B-libs"), is("ncurses-c%2B%2B-libs"));
  }

  @Test
  public void testCustomRules_nullRulesUsesDefaults() {
    EscapeHelper customHelper = new EscapeHelper(null);

    assertThat(customHelper.uriSegments("foo/bar+baz"), is("foo/bar+baz"));
    assertThat(customHelper.uriSegments("foo/bar%baz"), is("foo/bar%25baz"));
    assertThat(customHelper.uriSegments("foo/bar baz"), is("foo/bar%20baz"));
    assertThat(customHelper.uriSegments("foo:path/bar:baz"), is("foo%3Apath/bar%3Abaz"));
  }

  @Test
  public void testCustomRules_customOrder() {
    EscapeHelper customHelper = new EscapeHelper("a:b,b:c");

    assertThat(customHelper.uriSegments("a/b"), is("b/c"));
    assertThat(customHelper.uriSegments("abc"), is("bcc"));
  }

  @Test
  public void testCustomRules_specialCharacters() {
    EscapeHelper customHelper = new EscapeHelper("^:%5E,#:%23");

    assertThat(customHelper.uriSegments("test^file#name"), is("test%5Efile%23name"));
  }

  @Test
  public void testAlternationOrder_overlapping_longerFirstWins() {
    EscapeHelper h = new EscapeHelper("ab:X,a:Y");

    assertThat(h.uriSegments("ab"), is("X"));
    assertThat(h.uriSegments("ababa"), is("XXY"));
  }

  @Test
  public void testAlternationOrder_overlapping_shorterFirstWins() {
    EscapeHelper h = new EscapeHelper("a:Y,ab:X");

    assertThat(h.uriSegments("ab"), is("Yb"));
    assertThat(h.uriSegments("ababa"), is("YbYbY"));
  }

  @Test
  public void testAlternationOrder_insideSegments_only() {
    EscapeHelper h = new EscapeHelper("ab:X,a:Y");

    assertThat(h.uriSegments("ab/a/aba"), is("X/Y/XY"));
  }
}
