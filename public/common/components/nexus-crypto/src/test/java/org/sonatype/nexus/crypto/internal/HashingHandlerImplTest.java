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
package org.sonatype.nexus.crypto.internal;

import org.sonatype.nexus.crypto.CryptoHelper;
import org.sonatype.nexus.crypto.HashingHandler;
import org.sonatype.nexus.crypto.internal.error.CipherException;
import org.sonatype.nexus.crypto.secrets.EncryptedSecret;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.sonatype.nexus.crypto.internal.EncryptionHelper.KEY_ITERATION_PHC;
import static org.sonatype.nexus.crypto.internal.EncryptionHelper.KEY_LEN_PHC;
import static org.sonatype.nexus.crypto.internal.EncryptionHelper.fromBase64;

class HashingHandlerImplTest
{
  private CryptoHelper cryptoHelper;

  @BeforeEach
  void setup() {
    this.cryptoHelper = new TestCryptoHelper();
  }

  @Test
  void test_hash_a_value() {
    HashingHandler hashingHandler =
        new HashingHandlerImpl(cryptoHelper, "PBKDF2WithHmacSHA1", "salt".getBytes(), 1024, 128);
    char[] plaintext = "Sensitive_data".toCharArray();

    EncryptedSecret encrypted = hashingHandler.hash(plaintext);
    assertNotNull(encrypted);
    assertEquals("PBKDF2WithHmacSHA1", encrypted.getAlgorithm());
  }

  @Test
  void test_hash_when_it_uses_configuration_of_the_stored_EncryptedSecret() throws CipherException {
    HashingHandler hashingHandler =
        new HashingHandlerImpl(cryptoHelper, "PBKDF2WithHmacSHA1", "salt".getBytes(), 1024, 128);
    EncryptedSecret hashed = hashingHandler.hash("myPassword".toCharArray());

    String algorithm = hashed.getAlgorithm();
    byte[] salt = fromBase64(hashed.getSalt());
    int keyIterations = Integer.parseInt(hashed.getAttributes().get(KEY_ITERATION_PHC));
    int keyLength = Integer.parseInt(hashed.getAttributes().get(KEY_LEN_PHC));
    // using the stored hash to configure the handler
    HashingHandler hashingHandler2 = new HashingHandlerImpl(cryptoHelper, algorithm, salt, keyIterations, keyLength);

    char[] password = "myPassword".toCharArray();
    EncryptedSecret hash2 = hashingHandler2.hash(password);

    assertNotNull(hash2);
    assertEquals("PBKDF2WithHmacSHA1", hash2.getAlgorithm());
    assertEquals(hashed.getAlgorithm(), hash2.getAlgorithm());

    // Each hash should be equals due to they use the same salt
    assertEquals(hashed.getValue(), hash2.getValue());
  }

  @Test
  void test_hash_when_it_uses_a_new_random_salt_configuration() throws CipherException {
    HashingHandler hashingHandler =
        new HashingHandlerImpl(cryptoHelper, "PBKDF2WithHmacSHA1", "salt".getBytes(), 1024, 128);
    HashingHandler hashingHandler2 =
        new HashingHandlerImpl(cryptoHelper, "PBKDF2WithHmacSHA1", "differentSalt".getBytes(), 1024, 128);
    char[] password = "myPassword".toCharArray();

    EncryptedSecret hash1 = hashingHandler.hash(password);
    EncryptedSecret hash2 = hashingHandler2.hash(password);

    assertNotNull(hash1);
    assertNotNull(hash2);
    assertEquals("PBKDF2WithHmacSHA1", hash1.getAlgorithm());
    assertEquals("PBKDF2WithHmacSHA1", hash2.getAlgorithm());

    // Each hash should be different due to salt
    assertNotEquals(hash1.getValue(), hash2.getValue());
  }
}
