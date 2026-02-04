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

import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

import org.sonatype.nexus.crypto.CryptoHelper;

/**
 * Minimal test implementation of CryptoHelper that returns Java default algorithms.
 */
public class TestCryptoHelper
    implements CryptoHelper
{

  @Override
  public Provider getProvider() {
    return null;
  }

  @Override
  public Cipher createCipher(String algorithm) throws NoSuchAlgorithmException {
    try {
      return Cipher.getInstance(algorithm);
    }
    catch (Exception e) {
      throw new NoSuchAlgorithmException("Failed to create cipher for algorithm: " + algorithm, e);
    }
  }

  @Override
  public int getCipherMaxAllowedKeyLength(final String transformation) throws NoSuchAlgorithmException {
    return 0;
  }

  @Override
  public Signature createSignature(final String algorithm) throws NoSuchAlgorithmException {
    return null;
  }

  @Override
  public SecureRandom createSecureRandom(final String algorithm) throws NoSuchAlgorithmException {
    return null;
  }

  @Override
  public SecretKeyFactory createSecretKeyFactory(String algorithm) throws NoSuchAlgorithmException {
    return SecretKeyFactory.getInstance(algorithm);
  }

  @Override
  public SecureRandom createSecureRandom() {
    return new SecureRandom();
  }

  @Override
  public KeyStore createKeyStore(final String type) throws KeyStoreException {
    return null;
  }

  @Override
  public KeyPairGenerator createKeyPairGenerator(final String algorithm) throws NoSuchAlgorithmException {
    return null;
  }

  @Override
  public CertificateFactory createCertificateFactory(final String type) throws CertificateException {
    return null;
  }

  @Override
  public KeyManagerFactory createKeyManagerFactory(final String algorithm) throws NoSuchAlgorithmException {
    return null;
  }

  @Override
  public TrustManagerFactory createTrustManagerFactory(final String algorithm) throws NoSuchAlgorithmException {
    return null;
  }

  @Override
  public MessageDigest createDigest(String algorithm) throws NoSuchAlgorithmException {
    return MessageDigest.getInstance(algorithm);
  }
}
