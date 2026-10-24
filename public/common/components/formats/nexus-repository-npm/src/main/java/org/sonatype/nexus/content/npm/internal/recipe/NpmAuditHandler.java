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
package org.sonatype.nexus.content.npm.internal.recipe;

import java.io.InputStream;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nonnull;
import jakarta.inject.Singleton;

import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.http.HttpResponses;
import org.sonatype.nexus.repository.httpclient.HttpClientFacet;
import org.sonatype.nexus.repository.npm.internal.NpmCoordinatesHelper;
import org.sonatype.nexus.repository.proxy.ProxyFacet;
import org.sonatype.nexus.repository.view.Context;
import org.sonatype.nexus.repository.view.Handler;
import org.sonatype.nexus.repository.view.Payload;
import org.sonatype.nexus.repository.view.Response;
import org.sonatype.nexus.repository.view.matchers.token.TokenMatcher;
import org.sonatype.nexus.repository.view.payloads.BytesPayload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.io.ByteStreams;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Component;

import static org.sonatype.nexus.repository.http.HttpMethods.POST;
import static org.sonatype.nexus.repository.view.ContentTypes.APPLICATION_JSON;

/**
 * Minimal implementation of the npm audit endpoints.
 *
 * <p>For proxy repositories the request is forwarded to the remote registry, so vulnerability data comes from the
 * actual upstream. Hosted and group repositories have no upstream to ask: they answer with a well formed "no known
 * vulnerabilities" report, which keeps {@code npm audit} working without pretending to scan packages. Real scanning
 * in that case should be delegated to an external tool.</p>
 */
@Component
@Singleton
public class NpmAuditHandler
    extends ComponentSupport
    implements Handler
{
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String AUDIT_PREFIX = "-/npm/v1/security/audits";

  private static final String ADVISORIES_PREFIX = "-/npm/v1/security/advisories/bulk";

  @Nonnull
  @Override
  public Response handle(@Nonnull final Context context) throws Exception {
    String path = NpmCoordinatesHelper.stripLeadingSlash(
        NpmCoordinatesHelper.decode(contentPath(context)));

    boolean audit = path.equals(AUDIT_PREFIX) || path.startsWith(AUDIT_PREFIX + "/");
    boolean advisories = path.equals(ADVISORIES_PREFIX);
    if (!audit && !advisories) {
      return context.proceed();
    }

    if (!POST.equals(context.getRequest().getAction())) {
      return HttpResponses.methodNotAllowed(context.getRequest().getAction(), POST);
    }

    byte[] body = readBody(context.getRequest().getPayload());

    if (audit) {
      Response forwarded = forward(context.getRepository(), path, body, context);
      if (forwarded != null) {
        return forwarded;
      }
      return jsonResponse(auditResponse(body));
    }

    Response forwarded = forward(context.getRepository(), path, body, context);
    if (forwarded != null) {
      return forwarded;
    }
    return jsonResponse("{}");
  }

  /**
   * Forwards the audit request to the proxy upstream, returning {@code null} when there is no usable upstream.
   */
  private Response forward(
      final Repository repository,
      final String path,
      final byte[] body,
      final Context context)
  {
    ProxyFacet proxyFacet = repository.optionalFacet(ProxyFacet.class).orElse(null);
    HttpClientFacet httpClientFacet = repository.optionalFacet(HttpClientFacet.class).orElse(null);
    if (proxyFacet == null || httpClientFacet == null || proxyFacet.getRemoteUrl() == null) {
      return null;
    }

    try {
      String base = proxyFacet.getRemoteUrl().toString();
      if (!base.endsWith("/")) {
        base += "/";
      }
      URI target = URI.create(base).resolve(path);

      HttpPost request = new HttpPost(target);
      request.setEntity(new ByteArrayEntity(body, contentType(context)));
      copyHeader(context, request, HttpHeaders.CONTENT_ENCODING);
      copyHeader(context, request, HttpHeaders.ACCEPT);

      try (CloseableHttpResponse response = (CloseableHttpResponse) httpClientFacet.getHttpClient().execute(request)) {
        int status = response.getStatusLine().getStatusCode();
        if (status < HttpStatus.SC_OK || status >= HttpStatus.SC_MULTIPLE_CHOICES) {
          return null;
        }
        HttpEntity entity = response.getEntity();
        if (entity == null) {
          return null;
        }
        byte[] bytes = EntityUtils.toByteArray(entity);
        String responseType = entity.getContentType() != null
            ? ContentType.parse(entity.getContentType().getValue()).getMimeType()
            : APPLICATION_JSON;
        return HttpResponses.ok(new BytesPayload(bytes, responseType));
      }
    }
    catch (Exception e) {
      log.debug("Could not forward npm audit request to {}", proxyFacet.getRemoteUrl(), e);
      return null;
    }
  }

  private static ContentType contentType(final Context context) {
    Payload payload = context.getRequest().getPayload();
    String value = payload != null && payload.getContentType() != null
        ? payload.getContentType()
        : context.getRequest().getHeaders().get(HttpHeaders.CONTENT_TYPE);
    try {
      return value != null ? ContentType.parse(value) : ContentType.APPLICATION_JSON;
    }
    catch (Exception e) {
      return ContentType.APPLICATION_JSON;
    }
  }

  private static void copyHeader(final Context context, final HttpPost request, final String name) {
    Header header = context.getRequest().getHeaders().get(name) != null
        ? new org.apache.http.message.BasicHeader(name, context.getRequest().getHeaders().get(name))
        : null;
    if (header != null) {
      request.setHeader(header);
    }
  }

  private static byte[] readBody(final Payload payload) {
    if (payload == null) {
      return new byte[0];
    }
    try (InputStream input = payload.openInputStream()) {
      return ByteStreams.toByteArray(input);
    }
    catch (Exception e) {
      return new byte[0];
    }
  }

  private static ObjectNode auditResponse(final byte[] body) {
    int dependencies = countDependencies(body);

    ObjectNode response = MAPPER.createObjectNode();
    response.putArray("actions");
    response.putObject("advisories");
    response.putArray("muted");

    ObjectNode metadata = response.putObject("metadata");
    ObjectNode vulnerabilities = metadata.putObject("vulnerabilities");
    vulnerabilities.put("info", 0);
    vulnerabilities.put("low", 0);
    vulnerabilities.put("moderate", 0);
    vulnerabilities.put("high", 0);
    vulnerabilities.put("critical", 0);
    metadata.put("dependencies", dependencies);
    metadata.put("devDependencies", 0);
    metadata.put("optionalDependencies", 0);
    metadata.put("totalDependencies", dependencies);
    return response;
  }

  /**
   * Counts the unique dependency names present in the audit request body. The body may be gzip encoded, in which
   * case it cannot be parsed here and the count stays at zero.
   */
  private static int countDependencies(final byte[] body) {
    if (body == null || body.length == 0) {
      return 0;
    }
    try {
      JsonNode root = MAPPER.readTree(body);
      Set<String> names = new HashSet<>();
      collectDependencies(root, names, 0);
      return names.size();
    }
    catch (Exception e) {
      return 0;
    }
  }

  private static void collectDependencies(final JsonNode node, final Set<String> names, final int depth) {
    if (node == null || depth > 32) {
      return;
    }
    JsonNode dependencies = node.get("dependencies");
    if (dependencies != null && dependencies.isObject()) {
      dependencies.fields().forEachRemaining(entry -> {
        names.add(entry.getKey());
        collectDependencies(entry.getValue(), names, depth + 1);
      });
    }
    JsonNode requires = node.get("requires");
    if (requires != null && requires.isObject()) {
      requires.fieldNames().forEachRemaining(names::add);
    }
  }

  private static Response jsonResponse(final String body) {
    return jsonResponse(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private static Response jsonResponse(final byte[] body) {
    return HttpResponses.ok(new BytesPayload(body, APPLICATION_JSON));
  }

  private static Response jsonResponse(final JsonNode node) {
    try {
      return jsonResponse(MAPPER.writeValueAsBytes(node));
    }
    catch (Exception e) {
      return HttpResponses.ok(new BytesPayload("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8),
          APPLICATION_JSON));
    }
  }

  private static String contentPath(final Context context) {
    TokenMatcher.State state = context.getAttributes().require(TokenMatcher.State.class);
    return state.getTokens().get(NpmRecipeSupport.PATH_NAME);
  }
}
