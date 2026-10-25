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
package org.sonatype.nexus.content.conan.internal.recipe;

import java.util.Map;

import org.sonatype.goodies.testsupport.TestSupport;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Tests for the rewriting of {@code download_urls} documents: proxy responses must point at Nexus instead of the
 * remote, and group responses must point at the group instead of the member repository.
 */
public class ConanDownloadUrlsRewriterTest
    extends TestSupport
{
  private static final String REMOTE = "https://center.conan.io";

  private static final String LOCAL = "http://localhost:8081/repository/conan-proxy";

  private static final String GROUP = "http://localhost:8081/repository/conan-group";

  private static final String BODY = "{"
      + "\"conanfile\":{\"files\":{"
      + "\"/conanfile.py\":{\"url\":\"" + REMOTE + "/v1/files/conans/zlib/1.2.13/_/_/exports/conanfile.py\"},"
      + "\"/conanexport.tgz\":{\"url\":\"" + REMOTE + "/v1/files/conans/zlib/1.2.13/_/_/exports/conanexport.tgz\"}"
      + "}}}";

  @Test
  public void rewriteFromRemote_replacesTheRemoteRoot() {
    String rewritten = ConanDownloadUrlsRewriter.rewriteFromRemote(BODY, REMOTE, LOCAL);

    assertThat(rewritten.contains(REMOTE), is(false));
    assertThat(rewritten.contains(
        LOCAL + "/v1/files/conans/zlib/1.2.13/_/_/exports/conanfile.py"), is(true));
    assertThat(rewritten.contains(
        LOCAL + "/v1/files/conans/zlib/1.2.13/_/_/exports/conanexport.tgz"), is(true));
    // structure of the document is preserved
    assertThat(rewritten.startsWith("{\"conanfile\":{\"files\":{"), is(true));
    assertThat(rewritten.contains("\"/conanfile.py\":{\"url\":\""), is(true));
  }

  @Test
  public void rewriteFromRemote_handlesTrailingSlashOnTheRemoteRoot() {
    String rewritten = ConanDownloadUrlsRewriter.rewriteFromRemote(BODY, REMOTE + "/", LOCAL + "/");

    assertThat(rewritten.contains(REMOTE), is(false));
    assertThat(rewritten.contains(LOCAL + "/v1/files/"), is(true));
  }

  @Test
  public void rewriteFromRemote_fallsBackToSwappingTheOrigin() {
    String body = "{\"conanfile\":{\"files\":{"
        + "\"/conanfile.py\":{\"url\":\"https://elsewhere.example.com/v1/files/a/b\"}}}}";

    String rewritten = ConanDownloadUrlsRewriter.rewriteFromRemote(body, REMOTE, LOCAL);

    assertThat(rewritten.contains("https://elsewhere.example.com"), is(false));
    assertThat(rewritten.contains(LOCAL + "/v1/files/a/b"), is(true));
  }

  @Test
  public void rewriteFromRemote_leavesTheBodyUntouchedWhenNothingMatches() {
    String body = "{\"conanfile\":{\"files\":{\"/conanfile.py\":{\"url\":\"/relative/file\"}}}}";

    assertThat(ConanDownloadUrlsRewriter.rewriteFromRemote(body, REMOTE, LOCAL), is(body));
  }

  @Test
  public void rewriteFromRemote_returnsBodyAsIsForUnusableInput() {
    assertThat(ConanDownloadUrlsRewriter.rewriteFromRemote(null, REMOTE, LOCAL), is((String) null));
    assertThat(ConanDownloadUrlsRewriter.rewriteFromRemote("not json", REMOTE, LOCAL), is("not json"));
    assertThat(ConanDownloadUrlsRewriter.rewriteFromRemote("{}", null, LOCAL), is("{}"));
  }

  @Test
  public void rewritePrefix_movesMemberUrlsToTheGroup() {
    String member = "http://localhost:8081/repository/conan-hosted";

    String rewritten = ConanDownloadUrlsRewriter.rewritePrefix(BODY, member, GROUP);
    // nothing to do: the body does not point at that member
    assertThat(rewritten, is(BODY));

    String memberBody = BODY.replace(REMOTE, member);
    rewritten = ConanDownloadUrlsRewriter.rewritePrefix(memberBody, member, GROUP);

    assertThat(rewritten.contains(member), is(false));
    assertThat(rewritten.contains(GROUP + "/v1/files/conans/zlib/1.2.13/_/_/exports/conanfile.py"), is(true));
  }

  @Test
  public void rewritePrefix_isANoOpForEqualPrefixes() {
    assertThat(ConanDownloadUrlsRewriter.rewritePrefix(BODY, LOCAL, LOCAL + "/"), is(BODY));
    assertThat(ConanDownloadUrlsRewriter.rewritePrefix(BODY, null, GROUP), is(BODY));
    assertThat(ConanDownloadUrlsRewriter.rewritePrefix(BODY, "", GROUP), is(BODY));
  }

  @Test
  public void extractFileUrls_returnsEveryUrl() {
    Map<String, String> urls = ConanDownloadUrlsRewriter.extractFileUrls(BODY);

    assertThat(urls.size(), is(2));
    assertThat(urls.get("/conanfile.py"),
        is(REMOTE + "/v1/files/conans/zlib/1.2.13/_/_/exports/conanfile.py"));
    assertThat(ConanDownloadUrlsRewriter.extractFileUrls("nonsense").isEmpty(), is(true));
  }

  @Test
  public void extractFiles_readsLastModifiedAndUrl() {
    String body = "{\"conanfile\":{\"files\":{"
        + "\"/conanfile.py\":{\"url\":\"" + REMOTE + "/file\",\"lastModified\":1700000000000}}}}";

    Map<String, Map<String, Object>> files = ConanDownloadUrlsRewriter.extractFiles(body);

    assertThat(files.size(), is(1));
    assertThat(files.get("/conanfile.py").get("url"), is((Object) (REMOTE + "/file")));
    assertThat(files.get("/conanfile.py").get("lastModified"), is((Object) 1700000000000L));
  }
}
