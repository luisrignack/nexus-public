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
package org.sonatype.nexus.content.conan.internal.recipe;

import java.util.regex.Pattern;

import org.sonatype.goodies.testsupport.TestSupport;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Tests for the wildcard matching used by {@code GET /v1/conans/search?q=...}.
 */
public class ConanSearchMatcherTest
    extends TestSupport
{
  private static final String REFERENCE = "zlib/1.2.13@_/_";

  private static final String NAME = "zlib";

  @Test
  public void matches_exactReference() {
    assertThat(ConanSearchMatcher.matches("zlib/1.2.13@_/_", REFERENCE, NAME), is(true));
    assertThat(ConanSearchMatcher.matches("zlib/1.2.13@conan/stable", REFERENCE, NAME), is(false));
  }

  @Test
  public void matches_starPrefix() {
    assertThat(ConanSearchMatcher.matches("zlib*", REFERENCE, NAME), is(true));
    assertThat(ConanSearchMatcher.matches("z*", REFERENCE, NAME), is(true));
    assertThat(ConanSearchMatcher.matches("lib*", REFERENCE, NAME), is(false));
  }

  @Test
  public void matches_starInTheMiddleAndAtTheEnd() {
    assertThat(ConanSearchMatcher.matches("*/1.2.13@_*", REFERENCE, NAME), is(true));
    assertThat(ConanSearchMatcher.matches("*13@_*", REFERENCE, NAME), is(true));
    assertThat(ConanSearchMatcher.matches("*stable*", REFERENCE, NAME), is(false));
  }

  @Test
  public void matches_singleCharacterWildcard() {
    assertThat(ConanSearchMatcher.matches("zlib", REFERENCE, NAME), is(true));
    assertThat(ConanSearchMatcher.matches("zli?", REFERENCE, NAME), is(true));
    assertThat(ConanSearchMatcher.matches("zli??", REFERENCE, NAME), is(false));
  }

  @Test
  public void matches_againstTheNameWhenTheReferenceDoesNotMatch() {
    assertThat(ConanSearchMatcher.matches("zlib", "other/1.0@_/_", NAME), is(true));
    assertThat(ConanSearchMatcher.matches("other", REFERENCE, NAME), is(false));
  }

  @Test
  public void matches_anyOfTheSpaceSeparatedExpressions() {
    assertThat(ConanSearchMatcher.matches("libpng* zlib*", REFERENCE, NAME), is(true));
    assertThat(ConanSearchMatcher.matches("libpng*  zlib*", REFERENCE, NAME), is(true));
    assertThat(ConanSearchMatcher.matches("libpng* boost*", REFERENCE, NAME), is(false));
  }

  @Test
  public void matches_everythingWhenTheQueryIsEmpty() {
    assertThat(ConanSearchMatcher.matches(null, REFERENCE, NAME), is(true));
    assertThat(ConanSearchMatcher.matches("", REFERENCE, NAME), is(true));
    assertThat(ConanSearchMatcher.matches("   ", REFERENCE, NAME), is(true));
  }

  @Test
  public void glob_treatsRegexCharactersLiterally() {
    Pattern pattern = ConanSearchMatcher.glob("1.2.13");

    assertThat(pattern.matcher("1.2.13").matches(), is(true));
    assertThat(pattern.matcher("1x2y13").matches(), is(false));
    assertThat(ConanSearchMatcher.glob("a+b").matcher("a+b").matches(), is(true));
    assertThat(ConanSearchMatcher.glob("a+b").matcher("aab").matches(), is(false));
  }
}
