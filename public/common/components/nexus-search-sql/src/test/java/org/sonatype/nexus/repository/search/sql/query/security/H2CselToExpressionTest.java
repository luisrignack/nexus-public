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
package org.sonatype.nexus.repository.search.sql.query.security;

import org.sonatype.nexus.repository.rest.sql.SearchField;
import org.sonatype.nexus.repository.search.sql.query.syntax.ExactTerm;
import org.sonatype.nexus.repository.search.sql.query.syntax.Operand;
import org.sonatype.nexus.repository.search.sql.query.syntax.SqlPredicate;

import org.apache.commons.jexl3.parser.ASTJexlScript;
import org.junit.jupiter.api.Test;

class H2CselToExpressionTest
    extends CselToExpressionTest
{
  @Override
  protected CselToExpression createUnderTest() {
    return new H2CselToExpression();
  }

  @Test
  public void regexpTest() {
    ASTJexlScript script = jexlEngine.parseExpression("a =~ \"woof\"");

    script.childrenAccept(underTest, builder);

    // "paths_alias ~ :param_0"
    assertPredicate((SqlPredicate) builder.build(), SearchField.FORMAT_FIELD_1, Operand.REGEX,
        new ExactTerm("(\\[\"|,\")(woof)(\",|\"\\])"));

    reset();

    script = jexlEngine.parseExpression("a =~ \"woof$\""); // match always starts from start of path
    script.childrenAccept(underTest, builder);

    assertPredicate((SqlPredicate) builder.build(), SearchField.FORMAT_FIELD_1, Operand.REGEX,
        new ExactTerm("(\\[\"|,\")(woof)(\",|\"\\])"));

    reset();

    script = jexlEngine.parseExpression("a =~ \"^woof$\"");
    script.childrenAccept(underTest, builder);

    assertPredicate((SqlPredicate) builder.build(), SearchField.FORMAT_FIELD_1, Operand.REGEX,
        new ExactTerm("(\\[\"|,\")woof(\",|\"\\])"));

    reset();

    script = jexlEngine.parseExpression("a =~ \"^woof\"");
    script.childrenAccept(underTest, builder);

    assertPredicate((SqlPredicate) builder.build(), SearchField.FORMAT_FIELD_1, Operand.REGEX,
        new ExactTerm("(\\[\"|,\")woof"));

    reset();

    script = jexlEngine.parseExpression("a =~ \"^/woof|/woof/foo\"");

    script.childrenAccept(underTest, builder);

    assertPredicate((SqlPredicate) builder.build(), SearchField.FORMAT_FIELD_1, Operand.REGEX,
        new ExactTerm("(\\[\"|,\")/woof|/woof/foo"));
  }

  @Test
  public void regexpTestWithNestedParentheses() {
    // Test simple negative lookahead - should work
    ASTJexlScript script = jexlEngine.parseExpression("a =~ \"^(?!/test).*$\"");
    script.childrenAccept(underTest, builder);

    assertPredicate((SqlPredicate) builder.build(), SearchField.FORMAT_FIELD_1, Operand.REGEX,
        new ExactTerm("(\\[\"|,\")(?!/test).*(\",|\"\\])"));

    reset();

    // Test complex nested parentheses - the original buggy case
    script = jexlEngine.parseExpression("a =~ \"^(?!(/com/customer/(nested))).*$\"");
    script.childrenAccept(underTest, builder);

    // Should preserve the nested parentheses structure
    assertPredicate((SqlPredicate) builder.build(), SearchField.FORMAT_FIELD_1, Operand.REGEX,
        new ExactTerm("(\\[\"|,\")(?!(/com/customer/(nested))).*(\",|\"\\])"));

    reset();

    // Test nested parentheses without anchors - should also work
    script = jexlEngine.parseExpression("a =~ \"(?!(/com/customer/(nested))).*\"");
    script.childrenAccept(underTest, builder);

    assertPredicate((SqlPredicate) builder.build(), SearchField.FORMAT_FIELD_1, Operand.REGEX,
        new ExactTerm("(\\[\"|,\")((?!(/com/customer/(nested))).*)(\",|\"\\])"));

    reset();

    // Test multiple levels of nesting
    script = jexlEngine.parseExpression("a =~ \"^(?=(.*test.*))(?!(.*debug.*)).*$\"");
    script.childrenAccept(underTest, builder);

    assertPredicate((SqlPredicate) builder.build(), SearchField.FORMAT_FIELD_1, Operand.REGEX,
        new ExactTerm("(\\[\"|,\")(?=(.*test.*))(?!(.*debug.*)).*(\",|\"\\])"));

    reset();

    // Test nested alternation groups
    script = jexlEngine.parseExpression("a =~ \"^/(com|org|(net|io))/.*$\"");
    script.childrenAccept(underTest, builder);

    assertPredicate((SqlPredicate) builder.build(), SearchField.FORMAT_FIELD_1, Operand.REGEX,
        new ExactTerm("(\\[\"|,\")/(com|org|(net|io))/.*(\",|\"\\])"));

    reset();

    // Test anchored pattern without end anchor - should only add start delimiter
    script = jexlEngine.parseExpression("a =~ \"^(?!(/com/test)).*\"");
    script.childrenAccept(underTest, builder);

    assertPredicate((SqlPredicate) builder.build(), SearchField.FORMAT_FIELD_1, Operand.REGEX,
        new ExactTerm("(\\[\"|,\")(?!(/com/test)).*"));
  }
}
