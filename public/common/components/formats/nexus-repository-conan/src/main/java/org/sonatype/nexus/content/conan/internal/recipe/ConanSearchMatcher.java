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

/**
 * Implements the wildcard matching used by the Conan v1 search endpoint ({@code /v1/conans/search?q=...}).
 *
 * <p>The query supports {@code *} (any sequence) and {@code ?} (single character) and may be a single expression
 * or several expressions separated by spaces, all of which must match (logical OR).</p>
 *
 * <p>All methods are static and side effect free so they can be exercised by unit tests.</p>
 */
public final class ConanSearchMatcher
{
  private ConanSearchMatcher() {
    // no instances
  }

  /**
   * @param query the raw {@code q} parameter, e.g. {@code zlib*} or {@code zlib* libpng*}
   * @param reference the reference to test, e.g. {@code zlib/1.2.13@user/channel}
   * @param name the package name to test, e.g. {@code zlib}
   * @return {@code true} when the reference or the name matches any of the expressions
   */
  public static boolean matches(final String query, final String reference, final String name) {
    if (query == null || query.trim().isEmpty()) {
      return true;
    }
    for (final String expression : query.trim().split("\\s+")) {
      if (glob(expression).matcher(reference).matches() || glob(expression).matcher(name).matches()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Compiles a glob expression into an anchored regular expression.
   */
  public static Pattern glob(final String expression) {
    StringBuilder regex = new StringBuilder();
    for (final char character : expression.toCharArray()) {
      switch (character) {
        case '*':
          regex.append(".*");
          break;
        case '?':
          regex.append('.');
          break;
        default:
          regex.append(Pattern.quote(String.valueOf(character)));
      }
    }
    return Pattern.compile(regex.toString(), Pattern.DOTALL);
  }
}
