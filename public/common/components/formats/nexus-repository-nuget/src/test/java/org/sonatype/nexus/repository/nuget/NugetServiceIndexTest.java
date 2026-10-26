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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Service index generation and the URL rewriting used by the proxy.
 */
public class NugetServiceIndexTest
    extends TestSupport
{
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String BASE = "http://localhost:8081/repository/nuget-proxy";

  @Test
  public void buildsServiceIndexPointingAtThisRepository() throws Exception {
    JsonNode root = MAPPER.readTree(NugetServiceIndex.build(BASE));

    assertEquals("3.0.0", root.get("version").asText());

    JsonNode resources = root.get("resources");
    assertTrue(resources.isArray());
    assertEquals(4, resources.size());
    assertEquals(BASE + "/v3-flatcontainer/", resource(resources, "PackageBaseAddress/3.0.0"));
    assertEquals(BASE + "/v3/registration/", resource(resources, "RegistrationsBaseUrl/3.6.0"));
    assertEquals(BASE + "/v3/query", resource(resources, "SearchQueryService/3.5.0"));
    assertEquals(BASE + "/api/v2/package", resource(resources, "PackagePublish/2.0.0"));
  }

  @Test
  public void readsResourceUrlsFromServiceIndex() {
    String json = "{\"version\":\"3.0.0\",\"resources\":["
        + "{\"@id\":\"https://api.nuget.org/v3-flatcontainer/\",\"@type\":\"PackageBaseAddress/3.0.0\"},"
        + "{\"@id\":\"https://api.nuget.org/v3/registration5-gz-semver2/\",\"@type\":\"RegistrationsBaseUrl/3.6.0\"}]}";

    assertEquals("https://api.nuget.org/v3-flatcontainer/",
        NugetServiceIndex.resourceUrl(json, "PackageBaseAddress/3."));
    assertEquals("https://api.nuget.org/v3/registration5-gz-semver2/",
        NugetServiceIndex.resourceUrl(json, "RegistrationsBaseUrl/"));
    assertEquals(null, NugetServiceIndex.resourceUrl(json, "SearchQueryService/"));
  }

  @Test
  public void keepsTheRegistrationVariantOfTheRequestedPath() {
    assertEquals(
        "https://api.nuget.org/v3/registration5-gz-semver2/six/index.json",
        NugetServiceIndex.registrationUrl(
            "https://api.nuget.org/v3/registration5-semver1/",
            "registration5-gz-semver2/six/index.json"));
    assertEquals(
        "https://api.nuget.org/v3/registration5-semver1/six/index.json",
        NugetServiceIndex.registrationUrl(
            "https://api.nuget.org/v3/registration5-gz-semver2/",
            "registration5-semver1/six/index.json"));
  }

  @Test
  public void rewritesUpstreamUrlsToLocalRepository() throws Exception {
    String json = "{\"version\":\"3.0.0\",\"resources\":["
        + "{\"@id\":\"https://api.nuget.org/v3/index.json\",\"@type\":\"Index\"},"
        + "{\"@id\":\"https://api.nuget.org/v3-flatcontainer/\",\"@type\":\"PackageBaseAddress/3.0.0\"},"
        + "{\"@id\":\"https://api.nuget.org/v3/registration5-gz-semver2/\",\"@type\":\"RegistrationsBaseUrl/3.6.0\"},"
        + "{\"@id\":\"https://azuresearch.nuget.org/query\",\"@type\":\"SearchQueryService/3.5.0\"}]}";

    String rewritten = NugetServiceIndex.rewriteUrls(json, "https://api.nuget.org", BASE);

    JsonNode root = MAPPER.readTree(rewritten);
    JsonNode resources = root.get("resources");
    assertEquals(BASE + "/v3/index.json", resources.get(0).get("@id").asText());
    assertEquals(BASE + "/v3-flatcontainer/", resource(resources, "PackageBaseAddress/3.0.0"));
    assertEquals(BASE + "/v3/registration5-gz-semver2/", resource(resources, "RegistrationsBaseUrl/3.6.0"));
    // the search service lives on another host: it is localized separately
    assertEquals("https://azuresearch.nuget.org/query", resource(resources, "SearchQueryService/3.5.0"));
    assertFalse(rewritten.contains("api.nuget.org"));
  }

  @Test
  public void localizesCrossOriginSearchService() throws Exception {
    String json = "{\"version\":\"3.0.0\",\"resources\":["
        + "{\"@id\":\"https://api.nuget.org/v3-flatcontainer/\",\"@type\":\"PackageBaseAddress/3.0.0\"},"
        + "{\"@id\":\"https://azuresearch.nuget.org/query\",\"@type\":\"SearchQueryService/3.5.0\"}]}";

    assertEquals("https://azuresearch.nuget.org/query", NugetServiceIndex.searchServiceUrl(json));

    // as done by the proxy: rewrite the upstream origin first, then point the search service at the local query
    String rewritten = NugetServiceIndex.rewriteUrls(json, "https://api.nuget.org", BASE);
    JsonNode localized = MAPPER.readTree(NugetServiceIndex.localizeSearchResources(rewritten, BASE));

    assertEquals(BASE + "/v3/query", resource(localized.get("resources"), "SearchQueryService/3.5.0"));
    assertEquals(BASE + "/v3-flatcontainer/",
        resource(localized.get("resources"), "PackageBaseAddress/3.0.0"));

    // localizing an already local document is a no-op
    String once = NugetServiceIndex.localizeSearchResources(rewritten, BASE);
    assertEquals(once, NugetServiceIndex.localizeSearchResources(once, BASE));
  }

  @Test
  public void leavesNonJsonDocumentsUntouched() {
    String unrelated = "https://example.org/some.json";
    assertEquals(unrelated, NugetServiceIndex.rewriteUrls(unrelated, "https://api.nuget.org", BASE));
    assertEquals("<html>500</html>",
        NugetServiceIndex.rewriteUrls("<html>500</html>", "https://api.nuget.org", BASE));
    assertEquals("", NugetServiceIndex.rewriteUrls("", "https://api.nuget.org", BASE));
  }

  @Test
  public void rewritesRegistrationLeafUrls() {
    String json = "{\"@id\":\"https://api.nuget.org/v3/registration5-gz-semver2/newtonsoft.json/index.json\","
        + "\"items\":[{\"@id\":\"https://api.nuget.org/v3/registration5-gz-semver2/newtonsoft.json/13.0.1.json\","
        + "\"packageContent\":\"https://api.nuget.org/v3-flatcontainer/newtonsoft.json/13.0.1/"
        + "newtonsoft.json.13.0.1.nupkg\"}]}";

    String rewritten = NugetServiceIndex.rewriteUrls(json, "https://api.nuget.org/v3/index.json", BASE);

    assertTrue(rewritten.contains(BASE + "/v3/registration5-gz-semver2/newtonsoft.json/index.json"));
    assertTrue(rewritten.contains(BASE + "/v3-flatcontainer/newtonsoft.json/13.0.1/newtonsoft.json.13.0.1.nupkg"));
    assertFalse(rewritten.contains("api.nuget.org"));
  }

  @Test
  public void sameOriginDocumentsAreOnlyRewrittenByPrefix() throws Exception {
    // groups rewrite member documents: the member and the group share an origin, so only the repository path is
    // replaced and unrelated URLs of the same server survive untouched
    String json = "{\"@id\":\"http://localhost:8081/repository/member/v3/query\","
        + "\"projectUrl\":\"http://localhost:8081/repository/other\"}";

    String rewritten = NugetServiceIndex.rewriteUrls(json,
        "http://localhost:8081/repository/member",
        "http://localhost:8081/repository/group");

    JsonNode root = MAPPER.readTree(rewritten);
    assertEquals("http://localhost:8081/repository/group/v3/query", root.get("@id").asText());
    assertEquals("http://localhost:8081/repository/other", root.get("projectUrl").asText());
  }

  @Test
  public void v2ServiceIndexAdvertisesV2Endpoints() throws Exception {
    JsonNode root = MAPPER.readTree(NugetServiceIndex.buildV2(BASE));
    JsonNode resources = root.get("resources");

    assertEquals(BASE + "/Packages()", resource(resources, "SearchQueryService/2.0.0"));
    assertEquals(BASE + "/FindPackagesById()", resource(resources, "RegistrationsBaseUrl/2.0.0"));
    assertEquals(BASE + "/Download/", resource(resources, "PackageBaseAddress/2.0.0"));
    assertEquals(BASE + "/api/v2/package", resource(resources, "PackagePublish/2.0.0"));
  }

  private static String resource(final JsonNode resources, final String type) {
    for (JsonNode resource : resources) {
      if (type.equals(resource.get("@type").asText())) {
        return resource.get("@id").asText();
      }
    }
    return null;
  }
}
