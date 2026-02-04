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
import java.security.spec.AlgorithmParameterSpec;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.sonatype.nexus.crypto.CryptoHelper;
import org.sonatype.nexus.crypto.internal.error.CipherException;
import org.sonatype.nexus.crypto.secrets.EncryptedSecret;
import org.sonatype.nexus.crypto.HashingHandler;
import org.sonatype.nexus.crypto.secrets.internal.EncryptionKeyList.SecretEncryptionKey;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Value;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sonatype.nexus.common.app.FeatureFlags.NEXUS_SECURITY_SECRETS_ALGORITHM_NAMED_VALUE;
import static org.sonatype.nexus.common.app.FeatureFlags.NEXUS_SECURITY_SECRETS_ITERATIONS_NAMED_VALUE;
import static org.sonatype.nexus.crypto.internal.EncryptionHelper.KEY_ITERATION_PHC;
import static org.sonatype.nexus.crypto.internal.EncryptionHelper.KEY_LEN_PHC;
import static org.sonatype.nexus.crypto.internal.EncryptionHelper.fromBase64;
import static org.sonatype.nexus.crypto.internal.EncryptionHelper.toBase64;
import static org.sonatype.nexus.crypto.internal.HashingHandlerFactoryImpl.KEY_FACTORY_ALGORITHM_SHA1;

import org.springframework.stereotype.Component;

/**
 * Default implementation for {@link PbeCipherFactory} . provides a simple cipher supporting PHC string format
 */
@Component
@Singleton
public class PbeCipherFactoryImpl
    implements PbeCipherFactory
{
  private final CryptoHelper cryptoHelper;

  private final HashingHandlerFactory hashingHandlerFactory;

  private final String nexusSecretsAlgorithm;

  private final Integer configuredSecretsIterations;

  @Inject
  public PbeCipherFactoryImpl(
      final CryptoHelper cryptoHelper,
      final HashingHandlerFactory hashingHandlerFactory,
      final @Value(NEXUS_SECURITY_SECRETS_ALGORITHM_NAMED_VALUE) String nexusSecretsAlgorithm,
      final @Value(NEXUS_SECURITY_SECRETS_ITERATIONS_NAMED_VALUE) Integer configuredSecretsIterations)
  {
    this.cryptoHelper = checkNotNull(cryptoHelper);
    this.hashingHandlerFactory = checkNotNull(hashingHandlerFactory);
    this.nexusSecretsAlgorithm = nexusSecretsAlgorithm;
    this.configuredSecretsIterations = configuredSecretsIterations;
  }

  @Override
  public PbeCipher create(final SecretEncryptionKey secretEncryptionKey) throws CipherException {
    return doCreate(secretEncryptionKey, null, null, null, null);
  }

  @Override
  public PbeCipher create(
      final SecretEncryptionKey secretEncryptionKey,
      final String encryptedSecret) throws CipherException
  {
    return doCreate(secretEncryptionKey, encryptedSecret, null, null, null);
  }

  @Override
  public PbeCipher create(
      final SecretEncryptionKey secretEncryptionKey,
      final String salt,
      final String iv,
      final Integer iterations) throws CipherException
  {
    return doCreate(secretEncryptionKey, null, salt, iv, iterations);
  }

  private PbeCipher doCreate(
      final SecretEncryptionKey secretEncryptionKey,
      final String encryptedSecret,
      final String salt,
      final String iv,
      final Integer iterations)
  {
    checkNotNull(secretEncryptionKey);
    EncryptedSecret storedEncryptedSecret = null;
    String algorithm = nexusSecretsAlgorithm;
    boolean isDefaultCipher = true;
    Integer iterationsToBeUsed = iterations;

    byte[] saltToBeUsed = salt != null ? salt.getBytes() : null;

    if (encryptedSecret != null) {
      storedEncryptedSecret = EncryptedSecret.parse(encryptedSecret);
      algorithm = getAlgorithm(storedEncryptedSecret);
      isDefaultCipher = nexusSecretsAlgorithm.equals(algorithm);
      saltToBeUsed = fromBase64(storedEncryptedSecret.getSalt());

      if (iterationsToBeUsed == null) {
        String iterationsStr = storedEncryptedSecret.getAttributes().get(KEY_ITERATION_PHC);
        if (iterationsStr != null) {
          try {
            iterationsToBeUsed = Integer.parseInt(iterationsStr);
            isDefaultCipher = isDefaultCipher && (configuredSecretsIterations == null
                || configuredSecretsIterations.equals(iterationsToBeUsed));
          }
          catch (NumberFormatException e) {
          }
        }

      }
    }

    // Use configured iterations from nexus.properties if no explicit iterations provided
    if (iterationsToBeUsed == null && configuredSecretsIterations != null) {
      iterationsToBeUsed = configuredSecretsIterations;
    }

    HashingHandler hashingHandler = hashingHandlerFactory.create(algorithm, saltToBeUsed, iterationsToBeUsed);

    return new PbeCipherImpl(cryptoHelper, hashingHandler, secretEncryptionKey, storedEncryptedSecret, isDefaultCipher,
        iv);
  }

  private static String getAlgorithm(final EncryptedSecret storedEncryptedSecret) {
    String algorithm = storedEncryptedSecret.getAlgorithm();
    // this is for backwards compatibility since it was being used as PHC identifier
    if (PbeCipherImpl.ALGORITHM.equals(algorithm)) {
      algorithm = KEY_FACTORY_ALGORITHM_SHA1;
    }
    return algorithm;
  }

  /**
   * Abstract {@link PbeCipher} implementation, defines all the logic with no configuration.
   */
  static class PbeCipherImpl
      implements PbeCipher
  {
    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";

    private static final String KEY_ALGORITHM = "AES";

    private static final int IV_SIZE = 16;

    private static final String IV_PHC = "iv";

    private final CryptoHelper cryptoHelper;

    private final SecretEncryptionKey secretEncryptionKey;

    private final HashingHandler hashingHandler;

    private final EncryptedSecret storedEncryptedSecret;

    private final boolean isDefaultCipher;

    private final byte[] iv;

    PbeCipherImpl(
        final CryptoHelper cryptoHelper,
        final HashingHandler hashingHandler,
        final SecretEncryptionKey secretEncryptionKey,
        final EncryptedSecret storedEncryptedSecret,
        final boolean isDefaultCipher,
        final String iv) throws CipherException
    {
      this.cryptoHelper = cryptoHelper;
      this.hashingHandler = hashingHandler;

      this.secretEncryptionKey = secretEncryptionKey;
      this.storedEncryptedSecret = storedEncryptedSecret;
      this.isDefaultCipher = isDefaultCipher;
      if (storedEncryptedSecret != null) {
        String ivStored = storedEncryptedSecret.getAttributes().get(IV_PHC);
        this.iv = ivStored != null ? Hex.decode(ivStored) : null;
      }
      else {
        this.iv = iv == null ? generateRandomBytes(IV_SIZE) : iv.getBytes();
      }
    }

    @Override
    public boolean isDefaultCipher() {
      return this.isDefaultCipher;
    }

    @Override
    public EncryptedSecret encrypt(final byte[] bytes) throws CipherException {
      EncryptedSecret encryptedSecretHash = hashingHandler.hash(secretEncryptionKey.getKey().toCharArray());

      String saltBase64 = encryptedSecretHash.getSalt();
      SecretKey secretKey = new SecretKeySpec(fromBase64(encryptedSecretHash.getValue()), KEY_ALGORITHM);
      AlgorithmParameterSpec paramSpec = new IvParameterSpec(this.iv); // NOSONAR
      byte[] encrypted = transform(Cipher.ENCRYPT_MODE, secretKey, paramSpec, bytes);

      return new EncryptedSecret(encryptedSecretHash.getAlgorithm(), null, saltBase64, toBase64(encrypted),
          ImmutableMap.of(IV_PHC, Hex.toHexString(this.iv),
              KEY_ITERATION_PHC, encryptedSecretHash.getAttributes().get(KEY_ITERATION_PHC),
              KEY_LEN_PHC, encryptedSecretHash.getAttributes().get(KEY_LEN_PHC)));
    }

    @Override
    public byte[] decrypt() throws CipherException {
      return decrypt(fromBase64(storedEncryptedSecret.getValue()));
    }

    @Override
    public byte[] decrypt(byte[] encrypted) throws CipherException {
      EncryptedSecret encryptedSecretHash = hashingHandler.hash(secretEncryptionKey.getKey().toCharArray());

      SecretKey secretKey = new SecretKeySpec(fromBase64(encryptedSecretHash.getValue()), KEY_ALGORITHM);
      AlgorithmParameterSpec paramSpec = new IvParameterSpec(iv); // NOSONAR

      return transform(Cipher.DECRYPT_MODE, secretKey, paramSpec, encrypted);
    }

    private byte[] generateRandomBytes(final int size) {
      SecureRandom localRandom = cryptoHelper.createSecureRandom();
      byte[] bytes = new byte[size];
      localRandom.nextBytes(bytes);
      return bytes;
    }

    private byte[] transform(
        final int mode,
        final SecretKey secretKey,
        final AlgorithmParameterSpec paramSpec,
        final byte[] bytes) throws CipherException
    {
      try {
        Cipher cipher = cryptoHelper.createCipher(ALGORITHM);
        cipher.init(mode, secretKey, paramSpec);
        return cipher.doFinal(bytes, 0, bytes.length);
      }
      catch (Exception e) {
        Throwables.throwIfUnchecked(e);
        throw new CipherException(e.getMessage(), e);
      }
    }
  }
}
