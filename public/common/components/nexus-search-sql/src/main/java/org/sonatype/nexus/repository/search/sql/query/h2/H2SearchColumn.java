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
package org.sonatype.nexus.repository.search.sql.query.h2;

import java.util.Optional;

/**
 * Represents a searchable column in H2 database.
 * H2 doesn't support PostgreSQL's TSVECTOR full-text search, so we use simple VARCHAR with LIKE queries.
 */
class H2SearchColumn
{
  private final String columnName;

  private final Optional<String> sortColumnName;

  private final boolean tokenized;

  private final boolean jsonColumn;

  /**
   * @param columnName the database column name that will also be used for sorting
   */
  H2SearchColumn(final String columnName) {
    this(columnName, columnName, false);
  }

  /**
   * @param columnName the database column name for matches
   * @param sortColumnName the database column name that will be used for sorting
   */
  H2SearchColumn(final String columnName, final String sortColumnName) {
    this(columnName, sortColumnName, false);
  }

  /**
   * @param columnName the database column name that will also be used for sorting
   * @param tokenized whether this column contains tokenized data that always requires LIKE queries
   */
  H2SearchColumn(final String columnName, final boolean tokenized) {
    this(columnName, columnName, tokenized);
  }

  /**
   * @param columnName the database column name for matches
   * @param sortColumnName the database column name that will be used for sorting
   * @param tokenized whether this column contains tokenized data that always requires LIKE queries
   */
  H2SearchColumn(final String columnName, final String sortColumnName, final boolean tokenized) {
    this.columnName = columnName;
    this.sortColumnName = Optional.ofNullable(sortColumnName);
    this.tokenized = tokenized;
    this.jsonColumn = false;
  }

  /**
   * @param columnName the database column name for matches
   * @param tokenized whether this column contains tokenized data that always requires LIKE queries
   * @param jsonColumn whether this column contains json data that always requires special handling for exact match
   *          queries
   */
  H2SearchColumn(final String columnName, final boolean tokenized, final boolean jsonColumn) {
    this.columnName = columnName;
    this.sortColumnName = Optional.ofNullable(columnName);
    this.tokenized = tokenized;
    this.jsonColumn = jsonColumn;
  }

  String getColumnName() {
    return columnName;
  }

  Optional<String> getSortColumnName() {
    return sortColumnName;
  }

  /**
   * Returns whether this column contains tokenized data that requires LIKE matching
   */
  boolean isTokenized() {
    return tokenized;
  }

  /**
   * H2 columns support text search via LIKE
   */
  boolean supportsTextSearch() {
    return true;
  }

  /**
   * Returns whether this column contains json data that always requires special handling for exact match queries
   */
  boolean isJsonColumn() {
    return jsonColumn;
  }
}
