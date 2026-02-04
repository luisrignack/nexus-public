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
package org.sonatype.nexus.validation.constraint;

/**
 * Constants for validating logger names in the system.
 *
 */

public final class LoggerNameValidator
{

  private LoggerNameValidator() {
    // utility class
  }

  /**
   * Regex pattern for valid logger names.
   * Matches Java package names and ROOT logger.
   */
  public static final String REGEX = ".*[<>&'\"\\n\\r\\t/].*";

  /**
   * Error message for invalid logger names.
   */
  public static final String MESSAGE =
      "Logger name cannot include <, >, &, ', \", /, newline, or tab characters";

  /**
   * @param name the logger name to validate
   * @throws IllegalArgumentException if the name is invalid
   */
  public static void validate(String name) throws IllegalArgumentException {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("Logger name is required");
    }

    if (name.matches(LoggerNameValidator.REGEX)) {
      throw new IllegalArgumentException(LoggerNameValidator.MESSAGE);
    }
  }
}
