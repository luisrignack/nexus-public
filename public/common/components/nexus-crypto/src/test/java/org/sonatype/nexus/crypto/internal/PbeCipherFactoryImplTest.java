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

import java.security.SecureRandom;

import org.sonatype.nexus.crypto.CryptoHelper;
import org.sonatype.nexus.crypto.HashingHandler;
import org.sonatype.nexus.crypto.internal.PbeCipherFactory.PbeCipher;
import org.sonatype.nexus.crypto.internal.PbeCipherFactoryImpl.PbeCipherImpl;
import org.sonatype.nexus.crypto.secrets.internal.EncryptionKeyList.SecretEncryptionKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PbeCipherFactoryImplTest
{
  private SecretEncryptionKey encryptionKey;

  private PbeCipherFactoryImpl factory;

  @BeforeEach
  void setUp() {
    CryptoHelper cryptoHelper = mock(CryptoHelper.class);
    when(cryptoHelper.createSecureRandom()).thenReturn(new SecureRandom());
    encryptionKey = mock(SecretEncryptionKey.class);
    when(encryptionKey.getKey()).thenReturn("test-secret-key");
    HashingHandlerFactory hashingHandlerFactory = mock(HashingHandlerFactory.class);
    when(hashingHandlerFactory.create("PBKDF2WithSHA1")).thenReturn(mock(HashingHandler.class));

    factory = new PbeCipherFactoryImpl(cryptoHelper, hashingHandlerFactory, "PBKDF2WithSHA1", null);
  }

  @Test
  void testCreate_DefaultAlgorithm_Pbkdf2Sha1() {
    PbeCipher cipher = factory.create(encryptionKey);
    assertNotNull(cipher);
    assertInstanceOf(PbeCipherImpl.class, cipher);
    assertInstanceOf(cipher.getClass(), cipher);
    assertTrue(cipher.isDefaultCipher());
  }

  @Test
  void testCreate_when_Sha256Algorithm_input_creates_Pbkdf2Sha256() {
    String encoded =
        "$pbkdf2-sha256$iv=a6f7e545dc07ae8b0c5ff522d58b5994,key_iteration=10000,key_len=256$9+gAr77ZJtvlpDZm7Av1bg==$Z7yqZ3ok5JyAFYjoJ9lo+p2G1GZFUX9kqnDEJFHeErg=";

    PbeCipher cipher = factory.create(encryptionKey, encoded);
    assertNotNull(cipher);
    assertFalse(cipher.isDefaultCipher());
  }

  @Test
  void testCreate_UnsupportedAlgorithm_ShouldThrow() {
    String encoded = "$unsupportedabcdef0123456789$16salt";
    assertThrows(IllegalArgumentException.class, () -> factory.create(encryptionKey, encoded));
  }

  @Test
  void testCreate_WithExplicitIterations_SHA1() {
    CryptoHelper cryptoHelper = mock(CryptoHelper.class);
    when(cryptoHelper.createSecureRandom()).thenReturn(new SecureRandom());
    HashingHandlerFactory hashingHandlerFactory = mock(HashingHandlerFactory.class);
    HashingHandler mockHandler = mock(HashingHandler.class);
    when(hashingHandlerFactory.create("PBKDF2WithHmacSHA1", "test-salt".getBytes(), 5000))
        .thenReturn(mockHandler);

    PbeCipherFactoryImpl customFactory = new PbeCipherFactoryImpl(cryptoHelper, hashingHandlerFactory,
        "PBKDF2WithHmacSHA1", null);

    PbeCipher cipher = customFactory.create(encryptionKey, "test-salt", "test-iv", 5000);
    assertNotNull(cipher);
    assertTrue(cipher.isDefaultCipher());
  }

  @Test
  void testCreate_WithExplicitIterations_SHA256() {
    CryptoHelper cryptoHelper = mock(CryptoHelper.class);
    when(cryptoHelper.createSecureRandom()).thenReturn(new SecureRandom());
    HashingHandlerFactory hashingHandlerFactory = mock(HashingHandlerFactory.class);
    HashingHandler mockHandler = mock(HashingHandler.class);
    when(hashingHandlerFactory.create("PBKDF2WithHmacSHA256", "test-salt".getBytes(), 15000))
        .thenReturn(mockHandler);

    PbeCipherFactoryImpl customFactory = new PbeCipherFactoryImpl(cryptoHelper, hashingHandlerFactory,
        "PBKDF2WithHmacSHA256", null);

    PbeCipher cipher = customFactory.create(encryptionKey, "test-salt", "test-iv", 15000);
    assertNotNull(cipher);
    assertTrue(cipher.isDefaultCipher());
  }

  @Test
  void testCreate_WithNullIterations_UsesConfiguredIterations() {
    CryptoHelper cryptoHelper = mock(CryptoHelper.class);
    when(cryptoHelper.createSecureRandom()).thenReturn(new SecureRandom());
    HashingHandlerFactory hashingHandlerFactory = mock(HashingHandlerFactory.class);
    HashingHandler mockHandler = mock(HashingHandler.class);
    // When iterations is null, should use configuredSecretsIterations (12000)
    when(hashingHandlerFactory.create("PBKDF2WithHmacSHA256", "test-salt".getBytes(), 12000))
        .thenReturn(mockHandler);

    PbeCipherFactoryImpl customFactory = new PbeCipherFactoryImpl(cryptoHelper, hashingHandlerFactory,
        "PBKDF2WithHmacSHA256", 12000);

    PbeCipher cipher = customFactory.create(encryptionKey, "test-salt", "test-iv", null);
    assertNotNull(cipher);
    assertTrue(cipher.isDefaultCipher());
  }

  @Test
  void testCreate_WithBothNullIterations_PassesNullToHashingHandler() {
    CryptoHelper cryptoHelper = mock(CryptoHelper.class);
    when(cryptoHelper.createSecureRandom()).thenReturn(new SecureRandom());
    HashingHandlerFactory hashingHandlerFactory = mock(HashingHandlerFactory.class);
    HashingHandler mockHandler = mock(HashingHandler.class);
    // When both are null, should pass null to HashingHandlerFactory (will use defaults)
    when(hashingHandlerFactory.create("PBKDF2WithHmacSHA256", "test-salt".getBytes(), null))
        .thenReturn(mockHandler);

    PbeCipherFactoryImpl customFactory = new PbeCipherFactoryImpl(cryptoHelper, hashingHandlerFactory,
        "PBKDF2WithHmacSHA256", null);

    PbeCipher cipher = customFactory.create(encryptionKey, "test-salt", "test-iv", null);
    assertNotNull(cipher);
    assertTrue(cipher.isDefaultCipher());
  }

  @Test
  void testCreate_FromPhcString_ExtractsIterationsCorrectly() {
    CryptoHelper cryptoHelper = mock(CryptoHelper.class);
    when(cryptoHelper.createSecureRandom()).thenReturn(new SecureRandom());
    HashingHandlerFactory hashingHandlerFactory = mock(HashingHandlerFactory.class);
    HashingHandler mockHandler = mock(HashingHandler.class);
    // PHC string contains key_iteration=8000
    when(hashingHandlerFactory.create(anyString(), any(), eq(8000))).thenReturn(mockHandler);

    PbeCipherFactoryImpl customFactory = new PbeCipherFactoryImpl(cryptoHelper, hashingHandlerFactory,
        "PBKDF2WithHmacSHA1", null);

    String phcString =
        "$PBKDF2WithHmacSHA256$iv=a6f7e545dc07ae8b0c5ff522d58b5994,key_iteration=8000,key_len=256$9+gAr77ZJtvlpDZm7Av1bg==$Z7yqZ3ok5JyAFYjoJ9lo+p2G1GZFUX9kqnDEJFHeErg=";
    PbeCipher cipher = customFactory.create(encryptionKey, phcString);
    assertNotNull(cipher);
    // Should NOT be default cipher because algorithm differs (SHA256 vs SHA1)
    assertFalse(cipher.isDefaultCipher());
  }

  @Test
  void testIsDefaultCipher_WhenAlgorithmMatches_SHA1() {
    CryptoHelper cryptoHelper = mock(CryptoHelper.class);
    when(cryptoHelper.createSecureRandom()).thenReturn(new SecureRandom());
    HashingHandlerFactory hashingHandlerFactory = mock(HashingHandlerFactory.class);
    HashingHandler mockHandler = mock(HashingHandler.class);
    when(hashingHandlerFactory.create(anyString(), any(), any())).thenReturn(mockHandler);

    PbeCipherFactoryImpl customFactory = new PbeCipherFactoryImpl(cryptoHelper, hashingHandlerFactory,
        "PBKDF2WithHmacSHA1", null);

    String phcStringSHA1 = "$PBKDF2WithHmacSHA1$iv=a6f7e545dc07ae8b,key_iteration=1024,key_len=128$c2FsdA==$dmFsdWU=";
    PbeCipher cipher = customFactory.create(encryptionKey, phcStringSHA1);
    assertTrue(cipher.isDefaultCipher());
  }

  @Test
  void testIsDefaultCipher_WhenAlgorithmMatches_SHA256() {
    CryptoHelper cryptoHelper = mock(CryptoHelper.class);
    when(cryptoHelper.createSecureRandom()).thenReturn(new SecureRandom());
    HashingHandlerFactory hashingHandlerFactory = mock(HashingHandlerFactory.class);
    HashingHandler mockHandler = mock(HashingHandler.class);
    when(hashingHandlerFactory.create(anyString(), any(), any())).thenReturn(mockHandler);

    PbeCipherFactoryImpl customFactory = new PbeCipherFactoryImpl(cryptoHelper, hashingHandlerFactory,
        "PBKDF2WithHmacSHA256", null);

    String phcStringSHA256 =
        "$PBKDF2WithHmacSHA256$iv=a6f7e545dc07ae8b,key_iteration=10000,key_len=256$c2FsdA==$dmFsdWU=";
    PbeCipher cipher = customFactory.create(encryptionKey, phcStringSHA256);
    assertTrue(cipher.isDefaultCipher());
  }

  @Test
  void testIsDefaultCipher_WhenAlgorithmDiffers() {
    CryptoHelper cryptoHelper = mock(CryptoHelper.class);
    when(cryptoHelper.createSecureRandom()).thenReturn(new SecureRandom());
    HashingHandlerFactory hashingHandlerFactory = mock(HashingHandlerFactory.class);
    HashingHandler mockHandler = mock(HashingHandler.class);
    when(hashingHandlerFactory.create(anyString(), any(), any())).thenReturn(mockHandler);

    // Factory configured with SHA1
    PbeCipherFactoryImpl customFactory = new PbeCipherFactoryImpl(cryptoHelper, hashingHandlerFactory,
        "PBKDF2WithHmacSHA1", null);

    // PHC string contains SHA256
    String phcStringSHA256 =
        "$PBKDF2WithHmacSHA256$iv=a6f7e545dc07ae8b,key_iteration=10000,key_len=256$c2FsdA==$dmFsdWU=";
    PbeCipher cipher = customFactory.create(encryptionKey, phcStringSHA256);
    assertFalse(cipher.isDefaultCipher());
  }

  @Test
  void testIsDefaultCipher_WithDirectCreation_AlwaysTrue() {
    CryptoHelper cryptoHelper = mock(CryptoHelper.class);
    when(cryptoHelper.createSecureRandom()).thenReturn(new SecureRandom());
    HashingHandlerFactory hashingHandlerFactory = mock(HashingHandlerFactory.class);
    HashingHandler mockHandler = mock(HashingHandler.class);
    when(hashingHandlerFactory.create(anyString(), any(), any())).thenReturn(mockHandler);

    PbeCipherFactoryImpl customFactory = new PbeCipherFactoryImpl(cryptoHelper, hashingHandlerFactory,
        "PBKDF2WithHmacSHA256", null);

    // Direct creation (not from PHC string) always uses default cipher flag
    PbeCipher cipher1 = customFactory.create(encryptionKey);
    assertTrue(cipher1.isDefaultCipher());

    PbeCipher cipher2 = customFactory.create(encryptionKey, "salt", "iv", 5000);
    assertTrue(cipher2.isDefaultCipher());
  }

  @Test
  void testCreate_IterationsPriorityOrder_ExplicitOverConfigured() {
    CryptoHelper cryptoHelper = mock(CryptoHelper.class);
    when(cryptoHelper.createSecureRandom()).thenReturn(new SecureRandom());
    HashingHandlerFactory hashingHandlerFactory = mock(HashingHandlerFactory.class);
    HashingHandler mockHandler = mock(HashingHandler.class);
    // Explicit iterations (7000) should take priority over configured (12000)
    when(hashingHandlerFactory.create("PBKDF2WithHmacSHA256", "test-salt".getBytes(), 7000))
        .thenReturn(mockHandler);

    PbeCipherFactoryImpl customFactory = new PbeCipherFactoryImpl(cryptoHelper, hashingHandlerFactory,
        "PBKDF2WithHmacSHA256", 12000);

    PbeCipher cipher = customFactory.create(encryptionKey, "test-salt", "test-iv", 7000);
    assertNotNull(cipher);
  }

  @Test
  void testCreate_IterationsPriorityOrder_ConfiguredOverNull() {
    CryptoHelper cryptoHelper = mock(CryptoHelper.class);
    when(cryptoHelper.createSecureRandom()).thenReturn(new SecureRandom());
    HashingHandlerFactory hashingHandlerFactory = mock(HashingHandlerFactory.class);
    HashingHandler mockHandler = mock(HashingHandler.class);
    // When explicit is null, should use configured (12000)
    when(hashingHandlerFactory.create("PBKDF2WithHmacSHA256", "test-salt".getBytes(), 12000))
        .thenReturn(mockHandler);

    PbeCipherFactoryImpl customFactory = new PbeCipherFactoryImpl(cryptoHelper, hashingHandlerFactory,
        "PBKDF2WithHmacSHA256", 12000);

    PbeCipher cipher = customFactory.create(encryptionKey, "test-salt", "test-iv", null);
    assertNotNull(cipher);
  }

  @Test
  void testCreate_FromPhcString_IterationsInAttributeTakesPrecedence() {
    CryptoHelper cryptoHelper = mock(CryptoHelper.class);
    when(cryptoHelper.createSecureRandom()).thenReturn(new SecureRandom());
    HashingHandlerFactory hashingHandlerFactory = mock(HashingHandlerFactory.class);
    HashingHandler mockHandler = mock(HashingHandler.class);
    // PHC string has key_iteration=9500, configured is 12000
    // PHC iterations should take precedence
    when(hashingHandlerFactory.create(anyString(), any(), eq(9500))).thenReturn(mockHandler);

    PbeCipherFactoryImpl customFactory = new PbeCipherFactoryImpl(cryptoHelper, hashingHandlerFactory,
        "PBKDF2WithHmacSHA256", 12000);

    String phcString = "$PBKDF2WithHmacSHA256$iv=a6f7e545dc07ae8b,key_iteration=9500,key_len=256$c2FsdA==$dmFsdWU=";
    PbeCipher cipher = customFactory.create(encryptionKey, phcString);
    assertNotNull(cipher);
  }
}
