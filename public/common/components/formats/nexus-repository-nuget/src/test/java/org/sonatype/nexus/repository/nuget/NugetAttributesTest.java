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
package org.sonatype.nexus.repository.nuget;

import org.sonatype.goodies.testsupport.TestSupport;
import org.sonatype.nexus.repository.nuget.rest.NugetAttributes;
import org.sonatype.nexus.repository.nuget.rest.NugetProxyRepositoryApiRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Contract of the {@code nugetProxy} REST block used by the repository creation wizard.
 */
public class NugetAttributesTest
    extends TestSupport
{
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String PROXY_REQUEST = "{"
      + "\"name\":\"nuget.org-proxy\","
      + "\"online\":true,"
      + "\"storage\":{\"blobStoreName\":\"default\",\"strictContentTypeValidation\":true},"
      + "\"proxy\":{\"remoteUrl\":\"https://api.nuget.org/v3/index.json\","
      + "\"contentMaxAge\":1440,\"metadataMaxAge\":1440},"
      + "\"negativeCache\":{\"enabled\":true,\"timeToLive\":1440},"
      + "\"httpClient\":{\"blocked\":false,\"autoBlock\":true},"
      + "\"nugetProxy\":{\"nugetVersion\":\"V2\",\"queryCacheItemMaxAge\":300}"
      + "}";

  private static final String PROXY_REQUEST_WITHOUT_NUGET_BLOCK = "{"
      + "\"name\":\"nuget.org-proxy\","
      + "\"online\":true,"
      + "\"storage\":{\"blobStoreName\":\"default\",\"strictContentTypeValidation\":true},"
      + "\"proxy\":{\"remoteUrl\":\"https://api.nuget.org/v3/index.json\","
      + "\"contentMaxAge\":1440,\"metadataMaxAge\":1440},"
      + "\"negativeCache\":{\"enabled\":true,\"timeToLive\":1440},"
      + "\"httpClient\":{\"blocked\":false,\"autoBlock\":true}"
      + "}";

  @Test
  public void readsNugetProxyBlockFromRequest() throws Exception {
    NugetProxyRepositoryApiRequest request = MAPPER.readValue(PROXY_REQUEST, NugetProxyRepositoryApiRequest.class);

    assertEquals("nuget.org-proxy", request.getName());
    assertEquals("nuget", request.getFormat());
    assertEquals("V2", request.getNugetProxy().getNugetVersion());
    assertEquals(Integer.valueOf(300), request.getNugetProxy().getQueryCacheItemMaxAge());
    assertEquals("https://api.nuget.org/v3/index.json", request.getProxy().getRemoteUrl());
  }

  @Test
  public void nugetProxyBlockIsWrittenBackWithTheSameName() throws Exception {
    NugetProxyRepositoryApiRequest request = MAPPER.readValue(PROXY_REQUEST, NugetProxyRepositoryApiRequest.class);

    JsonNode written = MAPPER.readTree(MAPPER.writeValueAsString(request));

    assertTrue(written.has("nugetProxy"));
    assertEquals("V2", written.get("nugetProxy").get("nugetVersion").asText());
    assertEquals(300, written.get("nugetProxy").get("queryCacheItemMaxAge").asInt());
    assertEquals("nuget.org-proxy", written.get("name").asText());
  }

  @Test
  public void optionalNugetProxyFieldsDefaultToWizardValues() throws Exception {
    NugetProxyRepositoryApiRequest request =
        MAPPER.readValue(PROXY_REQUEST_WITHOUT_NUGET_BLOCK, NugetProxyRepositoryApiRequest.class);
    assertNull(request.getNugetProxy());

    NugetAttributes defaults = new NugetAttributes(null, null);
    assertNull(defaults.getNugetVersion());
    assertNull(defaults.getQueryCacheItemMaxAge());
    assertEquals("V3", NugetAttributes.V3);
    assertEquals("V2", NugetAttributes.V2);
    assertEquals(3600, NugetAttributes.DEFAULT_QUERY_CACHE_ITEM_MAX_AGE);
    assertEquals("nugetProxy", NugetAttributes.PROXY_NAME);
    assertEquals("nuget", NugetAttributes.NAME);
  }
}
