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
 * Landing page for the repository root.
 *
 * <p>It deliberately answers with HTML instead of redirecting to {@code api/v3/}: Galaxy clients
 * such as {@code ansible-galaxy} probe the root, and when the answer is not JSON they fall back to
 * {@code /api/} to discover the available API versions. A JSON redirect target would be mistaken
 * for the API root and would lack {@code available_versions}.</p>
 */
@Component
@Singleton
public class AnsibleRootHandler
    implements Handler
{
  @Nonnull
  @Override
  public Response handle(@Nonnull final Context context) {
    String baseUrl = context.getRepository().getUrl();
    String api = baseUrl + "/api/";
    String index = baseUrl + "/api/v3/plugin/ansible/content/published/collections/index/";
    String html = "<!DOCTYPE html>\n"
        + "<html><head><meta charset=\"utf-8\"><title>Ansible Galaxy repository</title></head>\n"
        + "<body><h1>Ansible Galaxy repository</h1>\n"
        + "<p>Galaxy API: <a href=\"" + api + "\">" + api + "</a></p>\n"
        + "<p>Collections: <a href=\"" + index + "\">" + index + "</a></p>\n"
        + "</body></html>\n";
    return ok(new BytesPayload(html.getBytes(StandardCharsets.UTF_8), TEXT_HTML));
  }
}
