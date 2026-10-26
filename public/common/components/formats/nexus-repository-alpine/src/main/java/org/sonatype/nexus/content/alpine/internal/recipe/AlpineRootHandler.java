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
package org.sonatype.nexus.content.alpine.internal.recipe;

import java.nio.charset.StandardCharsets;

import javax.annotation.Nonnull;
import jakarta.inject.Singleton;

import org.sonatype.nexus.repository.view.Context;
import org.sonatype.nexus.repository.view.Handler;
import org.sonatype.nexus.repository.view.Response;
import org.sonatype.nexus.repository.view.payloads.BytesPayload;

import org.springframework.stereotype.Component;

import static org.sonatype.nexus.repository.http.HttpResponses.ok;
import static org.sonatype.nexus.repository.view.ContentTypes.TEXT_HTML;

/**
 * Landing page for the repository root, useful to check that the repository is reachable.
 */
@Component
@Singleton
public class AlpineRootHandler
    implements Handler
{
  @Nonnull
  @Override
  public Response handle(@Nonnull final Context context) {
    String baseUrl = context.getRepository().getUrl();
    String html = "<!DOCTYPE html>\n"
        + "<html><head><meta charset=\"utf-8\"><title>Alpine repository</title></head>\n"
        + "<body><h1>Alpine repository</h1>\n"
        + "<p>Layout: <code>&lt;branch&gt;/&lt;repository&gt;/&lt;arch&gt;/</code></p>\n"
        + "<p>Example: <a href=\"" + baseUrl + "/v3.20/main/x86_64/APKINDEX.tar.gz\">"
        + baseUrl + "/v3.20/main/x86_64/APKINDEX.tar.gz</a></p>\n"
        + "</body></html>\n";
    return ok(new BytesPayload(html.getBytes(StandardCharsets.UTF_8), TEXT_HTML));
  }
}
