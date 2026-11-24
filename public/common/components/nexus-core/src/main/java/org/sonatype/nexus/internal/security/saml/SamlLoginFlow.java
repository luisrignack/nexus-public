/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 */
package org.sonatype.nexus.internal.security.saml;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServletRequest;

import org.sonatype.nexus.security.authc.saml.SamlAuthenticationToken;
import org.sonatype.nexus.security.saml.SamlConfiguration;
import org.sonatype.nexus.security.saml.SamlConfigurationStore;
import org.sonatype.nexus.saml.SamlEntityMetadata;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.subject.Subject;
import org.opensaml.core.config.InitializationService;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.schema.XSString;
import org.opensaml.core.xml.util.XMLObjectSupport;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.Attribute;
import org.opensaml.saml.saml2.core.AttributeStatement;
import org.opensaml.saml.saml2.core.AudienceRestriction;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.Conditions;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.saml.saml2.core.StatusCode;
import org.opensaml.saml.saml2.core.SubjectConfirmationData;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml.saml2.metadata.IDPSSODescriptor;
import org.opensaml.security.credential.Credential;
import org.opensaml.xmlsec.signature.support.SignatureValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Browser flow for SAML 2.0 single sign-on.
 *
 * <p>Starting a login builds a minimal SP-initiated {@code AuthnRequest} and redirects the browser
 * to the IdP. The assertion consumer endpoint decodes the {@code SAMLResponse}, validates its
 * signature against the certificate published in the configured IdP metadata, checks time and
 * audience conditions, and finally logs the Shiro subject in through {@link SamlAuthenticationToken}.</p>
 */
@Component
@Singleton
public class SamlLoginFlow
{
  public static final String LOGIN_PATH = "/saml/login";

  public static final String ACS_PATH = "/saml/acs";

  private static final Logger log = LoggerFactory.getLogger(SamlLoginFlow.class);

  private static final AtomicBoolean OPEN_SAML_INITIALIZED = new AtomicBoolean();

  private static final int CLOCK_SKEW_SECONDS = 120;

  private final SamlConfigurationStore configurationStore;

  private final Cache<String, PendingAuthorization> pending =
      CacheBuilder.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).build();

  private final Cache<String, Boolean> consumedAssertions =
      CacheBuilder.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).build();

  @Inject
  public SamlLoginFlow(final SamlConfigurationStore configurationStore) {
    this.configurationStore = checkNotNull(configurationStore);
  }

  public Outcome begin(final HttpServletRequest request) {
    SamlConfiguration configuration = configurationStore.load();
    if (configuration == null || !configuration.isEnabled() || configuration.getIdpMetadata() == null) {
      return Outcome.error(503, "SAML single sign-on is not configured");
    }
    try {
      initializeOpenSaml();
      SamlEntityMetadata metadata = SamlEntityMetadata.fromXml(configuration.getIdpMetadata());

      AuthnRequest authnRequest = build(AuthnRequest.DEFAULT_ELEMENT_NAME);
      authnRequest.setID(uniqueId());
      authnRequest.setIssueInstant(Instant.now());
      authnRequest.setDestination(metadata.getSingleSignOnUrl());
      authnRequest.setAssertionConsumerServiceURL(acsUrl(request));
      authnRequest.setProtocolBinding(SAMLConstants.SAML2_POST_BINDING_URI);

      Issuer issuer = build(Issuer.DEFAULT_ELEMENT_NAME);
      issuer.setValue(entityId(configuration, request));
      authnRequest.setIssuer(issuer);

      String state = uniqueId();
      pending.put(state, new PendingAuthorization(state, hash(request), Instant.now()));

      String xml = toString(XMLObjectSupport.marshall(authnRequest));
      String encoded = Base64.getUrlEncoder().withoutPadding()
          .encodeToString(xml.getBytes(StandardCharsets.UTF_8));
      String location = metadata.getSingleSignOnUrl()
          + (metadata.getSingleSignOnUrl().contains("?") ? "&" : "?")
          + "SAMLRequest=" + encoded + "&RelayState=" + state;
      return Outcome.redirect(location);
    }
    catch (Exception e) {
      log.warn("Unable to build SAML AuthnRequest", e);
      return Outcome.error(502, "Unable to start SAML login: " + e.getMessage());
    }
  }

  public Outcome complete(final HttpServletRequest request) {
    SamlConfiguration configuration = configurationStore.load();
    if (configuration == null || !configuration.isEnabled()) {
      return Outcome.error(503, "SAML single sign-on is not configured");
    }
    try {
      initializeOpenSaml();
      String rawResponse = request.getParameter("SAMLResponse");
      if (rawResponse == null || rawResponse.isEmpty()) {
        return Outcome.error(400, "Missing SAMLResponse");
      }

      Response response = parse(Base64.getDecoder().decode(rawResponse), Response.class);
      String status = response.getStatus() == null || response.getStatus().getStatusCode() == null
          ? null : response.getStatus().getStatusCode().getValue();
      if (!StatusCode.SUCCESS.equals(status)) {
        return Outcome.error(401, "SAML response status: " + status);
      }

      String relayState = request.getParameter("RelayState");
      PendingAuthorization authorization = relayState == null ? null : pending.getIfPresent(relayState);
      if (relayState != null) {
        pending.invalidate(relayState);
      }

      SamlEntityMetadata metadata = SamlEntityMetadata.fromXml(configuration.getIdpMetadata());
      Credential credential = metadata.getSigningCredential();
      validateSignatures(configuration, response, credential);

      String destination = acsUrl(request);
      if (response.getDestination() != null && !destination.equals(response.getDestination())) {
        throw new AuthenticationException("SAML response destination does not match this service provider");
      }

      Map<String, Object> attributes = new LinkedHashMap<>();
      List<String> groups = new ArrayList<>();
      String userId = null;
      for (Assertion assertion : response.getAssertions()) {
        validateAssertion(assertion, configuration, credential, destination,
            authorization == null ? null : authorization.inResponseTo);

        if (assertion.getSubject() != null && assertion.getSubject().getNameID() != null) {
          userId = assertion.getSubject().getNameID().getValue();
        }
        for (AttributeStatement statement : assertion.getAttributeStatements()) {
          for (Attribute attribute : statement.getAttributes()) {
            List<String> values = attributeValues(attribute);
            if (values.isEmpty()) {
              continue;
            }
            attributes.put(attribute.getName(), values.get(0));
            if (attribute.getName().equals(configuration.getGroupsAttribute())) {
              groups.addAll(values);
            }
          }
        }
      }

      String usernameAttribute = configuration.getUsernameAttribute();
      if (usernameAttribute != null && !"nameid".equalsIgnoreCase(usernameAttribute)
          && attributes.get(usernameAttribute) != null) {
        userId = String.valueOf(attributes.get(usernameAttribute));
      }
      if (userId == null || userId.isEmpty()) {
        return Outcome.error(401, "SAML assertion contained no usable user identity");
      }

      Subject subject = SecurityUtils.getSubject();
      subject.login(new SamlAuthenticationToken(userId, attributes, groups));
      log.info("SAML login succeeded for user '{}'", userId);

      String target = authorization == null ? null : authorization.destination;
      return Outcome.redirect(target == null || target.isEmpty() ? "/" : target);
    }
    catch (AuthenticationException e) {
      log.warn("SAML login rejected", e);
      return Outcome.error(401, "SAML login failed: " + e.getMessage());
    }
    catch (Exception e) {
      log.warn("SAML login failed", e);
      return Outcome.error(502, "SAML login failed: " + e.getMessage());
    }
  }

  private void validateSignatures(
      final SamlConfiguration configuration,
      final Response response,
      final Credential credential) throws Exception
  {
    boolean responseFlag = Boolean.TRUE.equals(configuration.getValidateResponseSignature());
    boolean assertionFlag = Boolean.TRUE.equals(configuration.getValidateAssertionSignature());
    boolean validateResponse = responseFlag || (responseFlag == assertionFlag && response.getSignature() != null);
    boolean validateAssertion = assertionFlag || (responseFlag == assertionFlag && response.getSignature() == null);

    if (validateResponse) {
      if (response.getSignature() == null) {
        throw new AuthenticationException("SAML response is not signed");
      }
      SignatureValidator.validate(response.getSignature(), credential);
    }
    if (validateAssertion) {
      boolean validated = false;
      for (Assertion assertion : response.getAssertions()) {
        if (assertion.getSignature() != null) {
          SignatureValidator.validate(assertion.getSignature(), credential);
          validated = true;
        }
      }
      if (!validated) {
        throw new AuthenticationException("SAML assertion is not signed");
      }
    }
  }

  private void validateAssertion(
      final Assertion assertion,
      final SamlConfiguration configuration,
      final Credential credential,
      final String destination,
      @Nullable final String inResponseTo) throws Exception
  {
    if (consumedAssertions.getIfPresent(assertion.getID()) != null) {
      throw new AuthenticationException("SAML assertion has already been consumed");
    }
    consumedAssertions.put(assertion.getID(), Boolean.TRUE);

    Conditions conditions = assertion.getConditions();
    if (conditions == null) {
      throw new AuthenticationException("SAML assertion has no conditions");
    }
    Instant now = Instant.now();
    if (conditions.getNotBefore() != null && now.plus(CLOCK_SKEW_SECONDS, ChronoUnit.SECONDS)
        .isBefore(conditions.getNotBefore())) {
      throw new AuthenticationException("SAML assertion is not yet valid");
    }
    if (conditions.getNotOnOrAfter() != null && !now.minus(CLOCK_SKEW_SECONDS, ChronoUnit.SECONDS)
        .isBefore(conditions.getNotOnOrAfter())) {
      throw new AuthenticationException("SAML assertion has expired");
    }

    String audience = configuration.getEntityId() != null && !configuration.getEntityId().isEmpty()
        ? configuration.getEntityId() : destination;
    boolean audienceMatched = false;
    for (AudienceRestriction restriction : conditions.getAudienceRestrictions()) {
      for (org.opensaml.saml.saml2.core.Audience entry : restriction.getAudiences()) {
        if (audience.equals(entry.getAudienceURI()) || destination.equals(entry.getAudienceURI())) {
          audienceMatched = true;
        }
      }
    }
    if (!conditions.getAudienceRestrictions().isEmpty() && !audienceMatched) {
      throw new AuthenticationException("SAML assertion audience does not match this service provider");
    }

    if (assertion.getSubject() != null) {
      for (org.opensaml.saml.saml2.core.SubjectConfirmation confirmation : assertion.getSubject()
          .getSubjectConfirmations())
      {
        SubjectConfirmationData data = confirmation.getSubjectConfirmationData();
        if (data == null) {
          continue;
        }
        if (data.getNotOnOrAfter() != null && !now.minus(CLOCK_SKEW_SECONDS, ChronoUnit.SECONDS)
            .isBefore(data.getNotOnOrAfter())) {
          throw new AuthenticationException("SAML subject confirmation has expired");
        }
        if (data.getRecipient() != null && !destination.equals(data.getRecipient())) {
          throw new AuthenticationException("SAML subject confirmation recipient does not match");
        }
        if (inResponseTo != null && data.getInResponseTo() != null && !inResponseTo.equals(data.getInResponseTo())) {
          throw new AuthenticationException("SAML subject confirmation has an unexpected InResponseTo");
        }
      }
    }
  }

  private static List<String> attributeValues(final Attribute attribute) {
    List<String> values = new ArrayList<>();
    for (XMLObject value : attribute.getAttributeValues()) {
      if (value instanceof XSString) {
        values.add(((XSString) value).getValue());
      }
      else if (value.getDOM() != null) {
        values.add(value.getDOM().getTextContent());
      }
    }
    return values;
  }

  private static <T extends XMLObject> T parse(final byte[] xml, final Class<T> type) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    Element element = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml)).getDocumentElement();
    XMLObject object = XMLObjectProviderRegistrySupport.getUnmarshallerFactory()
        .getUnmarshaller(element).unmarshall(element);
    return type.cast(object);
  }

  @SuppressWarnings("unchecked")
  private static <T extends XMLObject> T build(final javax.xml.namespace.QName name) {
    return (T) XMLObjectProviderRegistrySupport.getBuilderFactory().getBuilderOrThrow(name).buildObject(name);
  }

  private static String toString(final Element element) throws Exception {
    Transformer transformer = TransformerFactory.newInstance().newTransformer();
    transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
    StringWriter writer = new StringWriter();
    transformer.transform(new DOMSource(element), new StreamResult(writer));
    return writer.toString();
  }

  private static void initializeOpenSaml() {
    if (OPEN_SAML_INITIALIZED.compareAndSet(false, true)) {
      try {
        InitializationService.initialize();
      }
      catch (Exception e) {
        OPEN_SAML_INITIALIZED.set(false);
        throw new IllegalStateException("Unable to initialize OpenSAML", e);
      }
    }
  }

  private static String entityId(final SamlConfiguration configuration, @Nullable final HttpServletRequest request) {
    if (configuration.getEntityId() != null && !configuration.getEntityId().isEmpty()) {
      return configuration.getEntityId();
    }
    return request == null ? "urn:nexus:repository:saml" : acsUrl(request);
  }

  private static String acsUrl(final HttpServletRequest request) {
    String scheme = request.getScheme();
    String host = request.getServerName();
    int port = request.getServerPort();
    boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
    return scheme + "://" + host + (defaultPort ? "" : ":" + port) + request.getContextPath() + ACS_PATH;
  }

  private static String uniqueId() {
    return "_" + UUID.randomUUID().toString().replace("-", "");
  }

  private static String hash(final HttpServletRequest request) {
    String hash = request.getParameter("hash");
    return hash == null ? "" : hash;
  }

  /**
   * Redirect or error result, deliberately transport-agnostic.
   */
  public static final class Outcome
  {
    private final boolean redirect;

    private final int status;

    private final String location;

    private final String message;

    private Outcome(final boolean redirect, final int status, final String location, final String message) {
      this.redirect = redirect;
      this.status = status;
      this.location = location;
      this.message = message;
    }

    public static Outcome redirect(final String location) {
      return new Outcome(true, 303, location, null);
    }

    public static Outcome error(final int status, final String message) {
      return new Outcome(false, status, null, message);
    }

    public boolean isRedirect() {
      return redirect;
    }

    public int getStatus() {
      return status;
    }

    public String getLocation() {
      return location;
    }

    public String getMessage() {
      return message;
    }
  }

  private static final class PendingAuthorization
  {
    private final String inResponseTo;

    private final String destination;

    private final Instant created;

    private PendingAuthorization(final String inResponseTo, final String destination, final Instant created) {
      this.inResponseTo = inResponseTo;
      this.destination = destination;
      this.created = created;
    }
  }
}
