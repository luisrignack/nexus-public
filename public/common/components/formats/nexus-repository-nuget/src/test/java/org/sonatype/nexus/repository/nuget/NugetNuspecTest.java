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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.sonatype.goodies.testsupport.TestSupport;
import org.sonatype.nexus.repository.InvalidContentException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Nuspec extraction from a nupkg and the registration/catalog documents generated from it.
 */
public class NugetNuspecTest
    extends TestSupport
{
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String NUSPEC_XML =
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
          + "<package xmlns=\"http://schemas.microsoft.com/packaging/2013/05/nuspec.xsd\">\n"
          + "  <metadata>\n"
          + "    <id>Sample.Package</id>\n"
          + "    <version>1.2.3-beta.1</version>\n"
          + "    <title>Sample Package</title>\n"
          + "    <description>A sample package used by the unit tests</description>\n"
          + "    <authors>Alice, Bob</authors>\n"
          + "    <owners>Example Corp</owners>\n"
          + "    <projectUrl>https://example.org/sample</projectUrl>\n"
          + "    <licenseUrl>https://example.org/license</licenseUrl>\n"
          + "    <tags>sample testing nuget</tags>\n"
          + "    <dependencies>\n"
          + "      <dependency id=\"Newtonsoft.Json\" version=\"13.0.1\" />\n"
          + "      <dependency id=\"Serilog\" />\n"
          + "    </dependencies>\n"
          + "  </metadata>\n"
          + "</package>\n";

  @Test
  public void extractsManifestFromNupkg() {
    NugetNuspec nuspec = NugetNuspec.extract(new ByteArrayInputStream(nupkg("Sample.Package", "1.2.3-beta.1")));

    assertEquals("Sample.Package", nuspec.getId());
    assertEquals("1.2.3-beta.1", nuspec.getVersion());
    assertEquals("Sample Package", nuspec.getTitle());
    assertEquals("A sample package used by the unit tests", nuspec.getDescription());
    assertEquals("Alice, Bob", nuspec.getAuthors());
    assertEquals("Example Corp", nuspec.getOwners());
    assertEquals("sample testing nuget", nuspec.getTags());
    assertEquals(List.of("Newtonsoft.Json:13.0.1", "Serilog"), nuspec.getDependencies());
  }

  @Test
  public void manifestBecomesStoredAttributeMap() {
    Map<String, Object> attributes = NugetNuspec.extract(new ByteArrayInputStream(nupkg("Sample.Package", "1.2.3")))
        .toAttributeMap();

    assertEquals("Sample.Package", attributes.get("id"));
    assertEquals("1.2.3-beta.1", attributes.get("version"));
    assertEquals("A sample package used by the unit tests", attributes.get("description"));
    assertEquals(List.of("Newtonsoft.Json:13.0.1", "Serilog"), attributes.get("dependencies"));
    assertFalse("license acceptance flag is not part of the metadata", attributes.containsKey("requiresLicenseAcceptance"));
  }

  @Test
  public void catalogEntryIsMappedFromManifest() throws Exception {
    NugetNuspec nuspec = NugetNuspec.extract(new ByteArrayInputStream(nupkg("Sample.Package", "1.2.3")));
    NugetPackage nugetPackage = NugetPackage.of(nuspec, "2026-01-01T00:00:00Z");

    JsonNode entry = NugetIndexBuilder.catalogEntry(
        "http://localhost:8081/repository/nuget-hosted",
        "http://localhost:8081/repository/nuget-hosted/v3/registration/sample.package/index.json",
        nugetPackage);

    assertEquals("Sample.Package", entry.get("id").asText());
    assertEquals("1.2.3-beta.1", entry.get("version").asText());
    assertEquals("A sample package used by the unit tests", entry.get("description").asText());
    assertEquals("2026-01-01T00:00:00Z", entry.get("published").asText());
    assertTrue(entry.get("listed").asBoolean());
    assertEquals(
        "http://localhost:8081/repository/nuget-hosted/v3-flatcontainer/sample.package/1.2.3-beta.1/"
            + "sample.package.1.2.3-beta.1.nupkg",
        entry.get("packageContent").asText());
    assertTrue(entry.get("dependencies").isArray());
    assertEquals(2, entry.get("dependencies").size());
  }

  @Test
  public void archiveWithoutManifestIsRejected() {
    try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
         ZipOutputStream zip = new ZipOutputStream(buffer))
    {
      zip.putNextEntry(new ZipEntry("lib/net8.0/_._"));
      zip.write(new byte[0]);
      zip.closeEntry();
      zip.finish();

      NugetNuspec.extract(new ByteArrayInputStream(buffer.toByteArray()));
      fail("Expected the archive without a manifest to be rejected");
    }
    catch (InvalidContentException e) {
      assertTrue(e.getMessage().contains(".nuspec"));
    }
    catch (Exception e) {
      fail("Expected InvalidContentException but got " + e);
    }
  }

  @Test
  public void manifestWithoutVersionIsRejected() {
    String xml = "<package><metadata><id>Broken</id></metadata></package>";
    try {
      NugetNuspec.parse(xml);
      fail("Expected the manifest without a version to be rejected");
    }
    catch (InvalidContentException e) {
      assertTrue(e.getMessage().contains("version"));
    }
  }

  private static byte[] nupkg(final String id, final String version) {
    try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
         ZipOutputStream zip = new ZipOutputStream(buffer))
    {
      zip.putNextEntry(new ZipEntry(id + ".nuspec"));
      zip.write(NUSPEC_XML.getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();

      zip.putNextEntry(new ZipEntry("lib/net8.0/Sample.Package.dll"));
      zip.write(new byte[]{0x4d, 0x5a});
      zip.closeEntry();

      zip.finish();
      return buffer.toByteArray();
    }
    catch (Exception e) {
      throw new IllegalStateException("Unable to build the test nupkg", e);
    }
  }
}
