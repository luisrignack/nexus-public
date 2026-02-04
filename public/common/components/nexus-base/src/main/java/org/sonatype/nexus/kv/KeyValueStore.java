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
package org.sonatype.nexus.kv;

import java.util.Optional;

/**
 * Key-value store interface for storing and retrieving configuration data.
 */
public interface KeyValueStore
{
  /**
   * Gets a value by the given key.
   *
   * @param key a string key
   * @return {@link Optional} of {@link NexusKeyValue}
   */
  Optional<NexusKeyValue> getKey(String key);

  /**
   * Gets a value by the given key and deserializes it to the specified type.
   *
   * @param key the string key
   * @param clazz the class to deserialize to
   * @param <E> the type parameter
   * @return {@link Optional} of the deserialized object
   */
  <E> Optional<E> get(String key, Class<E> clazz);

  /**
   * Gets a boolean value by the given key.
   *
   * @param key the string key
   * @return {@link Optional} of {@link Boolean}
   */
  Optional<Boolean> getBoolean(String key);

  /**
   * Gets a string value by the given key.
   *
   * @param key the string key
   * @return {@link Optional} of {@link String}
   */
  Optional<String> getString(String key);

  /**
   * Gets an integer value by the given key.
   *
   * @param key the string key
   * @return {@link Optional} of {@link Integer}
   */
  Optional<Integer> getInt(String key);

  /**
   * Sets a key-value record.
   *
   * @param keyValue record to be created/updated
   */
  void setKey(NexusKeyValue keyValue);

  /**
   * Sets a boolean value for the given key.
   *
   * @param key the string key
   * @param value the boolean value
   */
  void setBoolean(String key, boolean value);

  /**
   * Sets an integer value for the given key.
   *
   * @param key the string key
   * @param value the integer value
   */
  void setInt(String key, int value);

  /**
   * Sets a string value for the given key.
   *
   * @param key the string key
   * @param value the string value
   */
  void setString(String key, String value);

  /**
   * Sets an object value for the given key.
   *
   * @param key the string key
   * @param value the object value
   */
  void setString(String key, Object value);

  /**
   * Removes a value by the given key.
   *
   * @param key a string key
   * @return true if the record was deleted successfully, false otherwise
   */
  boolean removeKey(String key);
}
