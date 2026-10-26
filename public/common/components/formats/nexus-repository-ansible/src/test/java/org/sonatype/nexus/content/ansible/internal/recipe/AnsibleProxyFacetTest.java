/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the Apache Software Foundation.
 */
package org.sonatype.nexus.content.ansible.internal.recipe;

import org.sonatype.goodies.testsupport.Test5Support;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

class AnsibleProxyFacetTest
    extends Test5Support
{
  private static final String REMOTE = "https://galaxy.ansible.com";

  private static final String LOCAL = "http://localhost:8081/repository/ansible-galaxy-real";

  @Test
  void absoluteGalaxyUrlsPointAtTheRepository() {
    String body = "{\"download_url\":\"" + REMOTE
        + "/api/v3/plugin/ansible/content/published/collections/artifacts/community-general-13.5.0.tar.gz\"}";

    String rewritten = AnsibleProxyFacet.rewrite(body, REMOTE, LOCAL);

    assertThat(rewritten, containsString("\"" + LOCAL + "/api/v3/plugin/"));
    assertThat(rewritten, not(containsString(REMOTE)));
  }

  @Test
  void rootRelativeGalaxyPathsAreAnchoredAtTheRepository() {
    String body = "{\"href\":\"/api/content/published/v3/plugin/ansible/content/published/collections/index/\","
        + "\"next\":\"/api/v3/plugin/ansible/content/published/collections/index/community/general/versions/?limit=10&offset=10\"}";

    String rewritten = AnsibleProxyFacet.rewrite(body, REMOTE, LOCAL);

    assertThat(rewritten, containsString("\"href\":\"" + LOCAL + "/api/content/published/v3/plugin/"));
    assertThat(rewritten, containsString("\"next\":\"" + LOCAL + "/api/v3/plugin/"));
    assertThat(rewritten, not(containsString("\":\"/api/")));
  }

  @Test
  void jsonEscapedRelativePathsAreRewrittenToo() {
    String body = "{\"href\":\"\\/api\\/v3\\/plugin\\/ansible\\/content\\/published\\/collections\\/index\\/\"}";

    String rewritten = AnsibleProxyFacet.rewrite(body, REMOTE, LOCAL);

    assertThat(rewritten, containsString("\"" + LOCAL.replace("/", "\\/") + "\\/api\\/v3\\/plugin\\/"));
  }

  @Test
  void publicGalaxyArtifactsAreNotRewrittenAsJson() {
    assertThat(AnsibleProxyFacet.isJson(
        "/api/v3/plugin/ansible/content/published/collections/artifacts/community-general-13.5.0.tar.gz"),
        is(false));
    assertThat(AnsibleProxyFacet.isJson(
        "/api/v3/plugin/ansible/content/published/collections/index/"), is(true));
  }
}
