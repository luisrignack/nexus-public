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
package org.sonatype.nexus.testdb.example;

import java.sql.SQLException;

import org.sonatype.goodies.testsupport.Test5Support;
import org.sonatype.nexus.testdb.DataSessionConfiguration;
import org.sonatype.nexus.testdb.DatabaseExtension;
import org.sonatype.nexus.testdb.TestDataSessionSupplier;
import org.sonatype.nexus.testdb.TestTable;

import com.google.common.collect.ImmutableMap;
import org.assertj.db.type.Table;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.db.api.Assertions.assertThat;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.sonatype.nexus.datastore.api.DataStoreManager.DEFAULT_DATASTORE_NAME;

@ExtendWith({DatabaseExtension.class})
class DatabaseExtensionTest
    extends Test5Support
{
  @DataSessionConfiguration(daos = TestItemDAO.class)
  TestDataSessionSupplier supplier;

  @DataSessionConfiguration(daos = TestItemDAO.class, storeName = "foo")
  TestDataSessionSupplier fooSupplier;

  @TestTable(table = "test_item")
  Table table;

  @TestTable(storeName = "foo", table = "test_item")
  Table fooTable;

  @Test
  void testGetDataSource() {
    assertThat(supplier.getDataSource().orElse(null), notNullValue());
    assertThat(supplier.getDataSource(DEFAULT_DATASTORE_NAME).orElse(null), notNullValue());
    assertThat(fooSupplier.getDataSource("foo").orElse(null), notNullValue());
    // can only access the expected store
    assertThrows(IllegalArgumentException.class, () -> fooSupplier.getDataSource(DEFAULT_DATASTORE_NAME));
  }

  @Test
  void testOpenConnection() throws SQLException {
    assertThat(supplier.openConnection(), notNullValue());
    assertThat(supplier.openConnection(DEFAULT_DATASTORE_NAME), notNullValue());
    assertThat(fooSupplier.openConnection("foo"), notNullValue());
    // can only access the expected store
    assertThrows(IllegalArgumentException.class, () -> fooSupplier.openConnection(DEFAULT_DATASTORE_NAME));
  }

  @Test
  void testOpenSession() {
    assertThat(supplier.openSession(), notNullValue());
    assertThat(supplier.openSession(DEFAULT_DATASTORE_NAME), notNullValue());
    assertThat(fooSupplier.openSession("foo"), notNullValue());
    // can only access the expected store
    assertThrows(IllegalArgumentException.class, () -> fooSupplier.openSession(DEFAULT_DATASTORE_NAME));
  }

  @Test
  void testOpenSerializableTransactionSession() {
    assertThat(supplier.openSerializableTransactionSession(), notNullValue());
    assertThat(supplier.openSerializableTransactionSession(DEFAULT_DATASTORE_NAME), notNullValue());
    assertThat(fooSupplier.openSerializableTransactionSession("foo"), notNullValue());
    // can only access the expected store
    assertThrows(IllegalArgumentException.class,
        () -> fooSupplier.openSerializableTransactionSession(DEFAULT_DATASTORE_NAME));
  }

  @Test
  void testStoresCreated() {
    assertThat(table).exists();
    assertThat(fooTable).exists();
  }

  @Test
  void testCallDAO() {
    TestItem item = new TestItem();
    item.setVersion(1);
    item.setEnabled(true);
    item.setNotes("test-entity");
    item.setProperties(ImmutableMap.of("sample", "data"));

    supplier.callDAO(TestItemDAO.class, dao -> dao.create(item));
    assertThat(table).hasNumberOfRows(1);
  }

  @Test
  void testWithDAO() {
    Iterable<TestItem> iter = supplier.withDAO(TestItemDAO.class, dao -> dao.browse());
    assertThat(iter, notNullValue());
  }

  @Test
  void testParameterResolver(
      @TestTable(table = "test_item") final Table table,
      @TestTable(storeName = "foo", table = "test_item") final Table fooTable)
  {
    assertThat(table).exists();
    assertThat(fooTable).exists();
  }
}
