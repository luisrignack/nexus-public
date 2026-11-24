/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 */
package org.sonatype.nexus.internal.security.saml.rest;

import java.io.StringWriter;

import javax.xml.namespace.QName;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;

import org.sonatype.nexus.internal.security.saml.SamlLoginFlow;
import org.sonatype.nexus.rest.Resource;
import org.sonatype.nexus.security.saml.SamlConfiguration;
import org.sonatype.nexus.security.saml.SamlConfigurationStore;

import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.util.XMLObjectSupport;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.metadata.AssertionConsumerService;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml.saml2.metadata.SPSSODescriptor;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;

import static com.google.common.base.Preconditions.checkNotNull;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.APPLICATION_XML;

/**
 * REST resource backing the SAML configuration screen and the SP metadata endpoint.
 */
@Component
@Singleton
@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Path(SamlConfigurationResource.RESOURCE_PATH)
public class SamlConfigurationResource
    implements Resource
{
  public static final String RESOURCE_PATH = "internal/ui/saml";

  private final SamlConfigurationStore store;

  @Context
  private HttpServletRequest request;

  @Inject
  public SamlConfigurationResource(final SamlConfigurationStore store) {
    this.store = checkNotNull(store);
  }

  @GET
  @RequiresPermissions("nexus:saml-settings:read")
  public SamlConfigurationXO read() {
    SamlConfiguration configuration = store.load();
    return configuration == null ? new SamlConfigurationXO() : SamlConfigurationXO.from(configuration);
  }

  @PUT
  @RequiresAuthentication
  @RequiresPermissions("nexus:saml-settings:update")
  public SamlConfigurationXO update(@NotNull @Valid final SamlConfigurationXO configurationXO) {
    SamlConfiguration configuration = configurationXO.toConfiguration();
    if (configuration.isEnabled() && (configuration.getIdpMetadata() == null
        || configuration.getIdpMetadata().trim().isEmpty())) {
      throw new IllegalArgumentException("IdP metadata is required when SAML is enabled");
    }
    store.save(configuration);
    return SamlConfigurationXO.from(store.load());
  }

  @DELETE
  @RequiresAuthentication
  @RequiresPermissions("nexus:saml-settings:update")
  public void delete() {
    store.delete();
  }

  @GET
  @Path("metadata")
  @Produces(APPLICATION_XML)
  @RequiresPermissions("nexus:saml-settings:read")
  public String metadata() throws Exception {
    SamlConfiguration configuration = store.load();
    String entityId = configuration != null && configuration.getEntityId() != null
        && !configuration.getEntityId().isEmpty()
        ? configuration.getEntityId() : acsUrl();

    EntityDescriptor descriptor = build(EntityDescriptor.DEFAULT_ELEMENT_NAME);
    descriptor.setEntityID(entityId);

    SPSSODescriptor sp = build(SPSSODescriptor.DEFAULT_ELEMENT_NAME);
    sp.setAuthnRequestsSigned(false);
    sp.setWantAssertionsSigned(true);
    descriptor.getRoleDescriptors().add(sp);

    AssertionConsumerService acs = build(AssertionConsumerService.DEFAULT_ELEMENT_NAME);
    acs.setBinding(SAMLConstants.SAML2_POST_BINDING_URI);
    acs.setLocation(acsUrl());
    acs.setIndex(0);
    sp.getAssertionConsumerServices().add(acs);

    Element element = XMLObjectSupport.marshall(descriptor);
    Transformer transformer = TransformerFactory.newInstance().newTransformer();
    transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
    StringWriter writer = new StringWriter();
    transformer.transform(new DOMSource(element), new StreamResult(writer));
    return writer.toString();
  }

  private String acsUrl() {
    String scheme = request.getScheme();
    String host = request.getServerName();
    int port = request.getServerPort();
    boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
    return scheme + "://" + host + (defaultPort ? "" : ":" + port) + request.getContextPath() + SamlLoginFlow.ACS_PATH;
  }

  @SuppressWarnings("unchecked")
  private static <T extends XMLObject> T build(final QName name) {
    return (T) XMLObjectProviderRegistrySupport.getBuilderFactory().getBuilderOrThrow(name).buildObject(name);
  }
}
