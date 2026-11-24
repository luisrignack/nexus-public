/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 */
package org.sonatype.nexus.saml;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.util.Base64;

import javax.xml.parsers.DocumentBuilderFactory;

import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml.saml2.metadata.IDPSSODescriptor;
import org.opensaml.saml.saml2.metadata.KeyDescriptor;
import org.opensaml.saml.saml2.metadata.SingleSignOnService;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.credential.UsageType;
import org.opensaml.security.x509.BasicX509Credential;
import org.opensaml.xmlsec.signature.KeyInfo;
import org.opensaml.xmlsec.signature.X509Data;
import org.w3c.dom.Element;

/**
 * Parses the identity provider metadata configured by the administrator.
 *
 * <p>Only the parts actually needed by the login flow are exposed: the HTTP-Redirect SSO endpoint
 * and the signing certificate. Metadata is treated as untrusted input and parsed with external entity
 * resolution disabled.</p>
 */
public final class SamlEntityMetadata
{
  private final String singleSignOnUrl;

  private final Credential signingCredential;

  private SamlEntityMetadata(final String singleSignOnUrl, final Credential signingCredential) {
    this.singleSignOnUrl = singleSignOnUrl;
    this.signingCredential = signingCredential;
  }

  public static SamlEntityMetadata fromXml(final String xml) {
    if (xml == null || xml.trim().isEmpty()) {
      throw new IllegalArgumentException("IdP metadata is empty");
    }
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      Element element = factory.newDocumentBuilder()
          .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))).getDocumentElement();
      XMLObject parsed = XMLObjectProviderRegistrySupport.getUnmarshallerFactory()
          .getUnmarshaller(element).unmarshall(element);
      if (!(parsed instanceof EntityDescriptor)) {
        throw new IllegalArgumentException("IdP metadata must be an EntityDescriptor");
      }
      EntityDescriptor descriptor = (EntityDescriptor) parsed;
      IDPSSODescriptor idp = descriptor.getIDPSSODescriptor(SAMLConstants.SAML20P_NS);
      if (idp == null) {
        throw new IllegalArgumentException("IdP metadata has no IDPSSODescriptor");
      }
      return new SamlEntityMetadata(singleSignOnUrl(idp), signingCredential(idp));
    }
    catch (IllegalArgumentException e) {
      throw e;
    }
    catch (Exception e) {
      throw new IllegalArgumentException("Unable to parse IdP metadata: " + e.getMessage(), e);
    }
  }

  private static String singleSignOnUrl(final IDPSSODescriptor idp) {
    for (SingleSignOnService service : idp.getSingleSignOnServices()) {
      if (SAMLConstants.SAML2_REDIRECT_BINDING_URI.equals(service.getBinding())) {
        return service.getLocation();
      }
    }
    if (!idp.getSingleSignOnServices().isEmpty()) {
      return idp.getSingleSignOnServices().get(0).getLocation();
    }
    throw new IllegalArgumentException("IdP metadata has no SingleSignOnService");
  }

  private static Credential signingCredential(final IDPSSODescriptor idp) {
    for (KeyDescriptor descriptor : idp.getKeyDescriptors()) {
      if (descriptor.getUse() != null && descriptor.getUse() != UsageType.SIGNING) {
        continue;
      }
      KeyInfo keyInfo = descriptor.getKeyInfo();
      if (keyInfo == null) {
        continue;
      }
      for (X509Data data : keyInfo.getX509Datas()) {
        if (!data.getX509Certificates().isEmpty()) {
          String base64 = data.getX509Certificates().get(0).getValue();
          try {
            byte[] der = Base64.getMimeDecoder().decode(base64);
            java.security.cert.X509Certificate certificate = (java.security.cert.X509Certificate)
                CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(der));
            return new BasicX509Credential(certificate);
          }
          catch (Exception e) {
            throw new IllegalArgumentException("Unable to read signing certificate from IdP metadata", e);
          }
        }
      }
    }
    throw new IllegalArgumentException("IdP metadata has no signing certificate");
  }

  public String getSingleSignOnUrl() {
    return singleSignOnUrl;
  }

  public Credential getSigningCredential() {
    return signingCredential;
  }
}
