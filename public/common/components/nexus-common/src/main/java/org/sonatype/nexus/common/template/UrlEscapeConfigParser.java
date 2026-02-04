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

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class UrlEscapeConfigParser
{
  private static final Logger log = LoggerFactory.getLogger(UrlEscapeConfigParser.class);

  private static final int MAX_CONFIG_LENGTH = 500;

  private static final int MAX_RULES = 20;

  private static final int MAX_PATTERN_LENGTH = 3;

  private static final int MAX_REPLACEMENT_LENGTH = 10;

  private UrlEscapeConfigParser() {
  }

  /**
   * Parses a configuration string of URL escape rules in the format "pattern:replacement".
   * Multiple rules are separated by commas.
   *
   * @param configValue the configuration string
   * @return a map of pattern to replacement
   */
  public static Map<String, String> parseRules(final String configValue) {
    Map<String, String> rules = new LinkedHashMap<>();

    if (configValue == null || configValue.trim().isEmpty()) {
      return rules;
    }

    if (configValue.length() > MAX_CONFIG_LENGTH) {
      log.warn("URL escape config too long ({} chars). Ignoring custom rules.", configValue.length());
      return rules;
    }

    String[] entries = configValue.split(",");
    if (entries.length > MAX_RULES) {
      log.warn("Too many URL escape rules ({}). Limiting to {}.", entries.length, MAX_RULES);
    }

    for (int i = 0; i < Math.min(entries.length, MAX_RULES); i++) {
      String rule = entries[i];
      if (!rule.isEmpty()) {
        parseAndAddRule(rule, rules);
      }
    }

    return rules;
  }

  private static void parseAndAddRule(final String rule, final Map<String, String> rules) {
    String[] parts = splitRule(rule);

    if (parts.length != 2) {
      log.warn("Invalid URL escape rule format: '{}'. Expected 'pattern:replacement'", rule);
      return;
    }

    String pattern = parts[0];
    String replacement = parts[1];

    if (isValidRule(pattern, replacement, rule)) {
      rules.put(pattern, replacement);
      log.debug("Loaded URL escape rule: '{}' -> '{}'", pattern, replacement);
    }
  }

  private static String[] splitRule(final String rule) {
    return rule.startsWith("::")
        ? new String[]{":", rule.substring(2)}
        : rule.split(":", 2);
  }

  private static boolean isValidRule(final String pattern, final String replacement, final String rule) {
    if (pattern.length() > MAX_PATTERN_LENGTH || replacement.length() > MAX_REPLACEMENT_LENGTH) {
      log.warn("Skipping overly long rule: '{}'", rule);
      return false;
    }

    if (containsControlChars(pattern) || containsControlChars(replacement)) {
      log.warn("Skipping rule containing control characters: '{}'", rule);
      return false;
    }

    return true;
  }

  private static boolean containsControlChars(final String value) {
    return value.contains("\n") || value.contains("\r");
  }
}
