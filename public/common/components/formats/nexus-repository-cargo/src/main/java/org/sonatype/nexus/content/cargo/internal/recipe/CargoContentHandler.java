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
package org.sonatype.nexus.content.cargo.internal.recipe;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

import javax.annotation.Nonnull;

import jakarta.inject.Singleton;

import org.sonatype.nexus.content.cargo.CargoContentFacet;
import org.sonatype.nexus.repository.cargo.CargoCoordinatesHelper;
import org.sonatype.nexus.repository.cargo.CargoCoordinatesHelper.CrateCoordinates;
import org.sonatype.nexus.repository.cargo.CargoCrateSummary;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.Context;
import org.sonatype.nexus.repository.view.Handler;
import org.sonatype.nexus.repository.view.Payload;
import org.sonatype.nexus.repository.view.Request;
import org.sonatype.nexus.repository.view.Response;
import org.sonatype.nexus.repository.view.Status;
import org.sonatype.nexus.repository.view.matchers.token.TokenMatcher;
import org.sonatype.nexus.repository.view.payloads.BytesPayload;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.io.ByteStreams;
import org.springframework.stereotype.Component;

import static org.sonatype.nexus.repository.http.HttpMethods.DELETE;
import static org.sonatype.nexus.repository.http.HttpMethods.GET;
import static org.sonatype.nexus.repository.http.HttpMethods.HEAD;
import static org.sonatype.nexus.repository.http.HttpMethods.PUT;
import static org.sonatype.nexus.repository.http.HttpResponses.methodNotAllowed;
import static org.sonatype.nexus.repository.http.HttpResponses.notFound;
import static org.sonatype.nexus.repository.http.HttpResponses.ok;
import static org.sonatype.nexus.repository.view.ContentTypes.APPLICATION_JSON;

/**
 * Serves the cargo sparse index, the crates.io compatible API endpoints and accepts cargo publishes on hosted
 * repositories.
 *
 * <p>Cargo clients may authenticate with {@code cargo-protocol} / {@code User-Agent: cargo/...} headers; those are
 * accepted silently like any other standard header.</p>
 *
 * @since 3.89
 */
@Component
@Singleton
public class CargoContentHandler
    implements Handler
{
  private static final String APPLICATION_GZIP_MIME = "application/gzip";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Nonnull
  @Override
  public Response handle(@Nonnull final Context context) throws Exception {
    String path = contentPath(context);
    String method = context.getRequest().getAction();

    Repository repository = context.getRepository();
    CargoContentFacet storage = repository.facet(CargoContentFacet.class);

    switch (method) {
      case HEAD:
      case GET:
        return get(context, storage, path);
      case PUT:
        return put(context, storage, path);
      case DELETE:
        return delete(storage, path);
      default:
        return methodNotAllowed(method, GET, HEAD, PUT, DELETE);
    }
  }

  //
  // GET
  //

  private Response get(final Context context, final CargoContentFacet storage, final String path)
      throws IOException
  {
    if (CargoCoordinatesHelper.isConfigPath(path)) {
      return json(CargoIndexBuilder.configJson(repositoryBase(context)));
    }

    Optional<CrateCoordinates> download = CargoCoordinatesHelper.parseDownload(path);
    if (download.isPresent()) {
      String cratePath = CargoCoordinatesHelper.cratePath(download.get().getCrate(), download.get().getVersion());
      return storage.get(cratePath)
          .map(CargoContentHandler::asGzip)
          .orElseGet(() -> notFound(path));
    }

    if (CargoCoordinatesHelper.isSearchPath(path)) {
      String query = context.getRequest().getParameters().get("q");
      int page = positiveInt(context.getRequest().getParameters().get("page"), 1);
      int perPage = positiveInt(context.getRequest().getParameters().get("per_page"),
          CargoIndexBuilder.DEFAULT_PER_PAGE);
      return json(CargoIndexBuilder.searchResponse(storage.listCrates(), query, page, perPage));
    }

    Optional<String> detailCrate = CargoCoordinatesHelper.crateFromDetailPath(path);
    if (detailCrate.isPresent()) {
      String ndjson = storage.readIndex(detailCrate.get());
      if (ndjson.isEmpty()) {
        return notFound(path);
      }
      CargoCrateSummary summary = storage.crate(detailCrate.get())
          .orElseGet(() -> new CargoCrateSummary(detailCrate.get(), "", CargoIndexBuilder.maxVersion(ndjson), 0, ""));
      return json(CargoIndexBuilder.detailResponse(summary, ndjson));
    }

    if (CargoCoordinatesHelper.isIndexPath(path)) {
      return storage.get(path)
          .map(content -> (Response) ok(content))
          .orElseGet(() -> notFound(path));
    }

    return notFound(path);
  }

  //
  // PUT (publish)
  //

  private Response put(final Context context, final CargoContentFacet storage, final String path) throws IOException {
    Request request = context.getRequest();
    Payload payload = request.getPayload();
    if (payload == null) {
      return error(400, "Missing payload");
    }

    Optional<CrateCoordinates> coordinates = CargoCoordinatesHelper.parsePublish(path);
    if (coordinates.isEmpty()) {
      return error(404, "Unsupported upload path: " + path);
    }

    byte[] body;
    try (InputStream input = payload.openInputStream()) {
      body = ByteStreams.toByteArray(input);
    }
    if (body.length == 0) {
      return error(400, "Empty payload");
    }

    if (isJsonPayload(request, body)) {
      return publishRecord(storage, coordinates.get(), new String(body, StandardCharsets.UTF_8));
    }
    return publishCrate(storage, coordinates.get(), body);
  }

  /**
   * A publish carrying the sparse index record as JSON, as sent by some cargo clients.
   */
  private Response publishRecord(
      final CargoContentFacet storage,
      final CrateCoordinates coordinates,
      final String body) throws IOException
  {
    String crate = coordinates.getCrate();
    String version = coordinates.getVersion();

    String problem = CargoIndexBuilder.validateRecord(body, crate, version);
    if (problem != null) {
      return error(400, problem);
    }

    ObjectNode record = CargoIndexBuilder.parseRecord(body);
    if (CargoIndexBuilder.text(record, "cksum").isEmpty()) {
      Optional<String> cksum = existingCksum(storage, crate, version);
      cksum.ifPresent(value -> record.put("cksum", value));
    }

    return storeRecord(storage, crate, record);
  }

  /**
   * A publish carrying the raw {@code .crate} archive; the checksum is computed server side.
   */
  private Response publishCrate(
      final CargoContentFacet storage,
      final CrateCoordinates coordinates,
      final byte[] body) throws IOException
  {
    String crate = coordinates.getCrate();
    String version = coordinates.getVersion();
    String cksum = CargoIndexBuilder.sha256Hex(body);

    storage.put(CargoCoordinatesHelper.cratePath(crate, version), crate, version,
        new BytesPayload(body, APPLICATION_GZIP_MIME));

    String existing = storage.readIndex(crate);
    ObjectNode record = CargoIndexBuilder.findRecord(existing, version);
    final String recordJson;
    if (record != null) {
      String recordName = CargoIndexBuilder.text(record, "name");
      if (!recordName.isEmpty() && !recordName.equalsIgnoreCase(crate)) {
        return error(400, "Crate name mismatch: path '" + crate + "' does not match record '" + recordName + "'");
      }
      recordJson = CargoIndexBuilder.withCksum(record.toString(), cksum);
    }
    else {
      recordJson = CargoIndexBuilder.recordFor(crate, version, cksum, now());
    }

    ObjectNode merged = CargoIndexBuilder.parseRecord(recordJson.trim());
    return storeRecord(storage, crate, merged);
  }

  /**
   * Merges the record into the sibling record file of the crate and refreshes the search metadata.
   */
  private Response storeRecord(
      final CargoContentFacet storage,
      final String crate,
      final ObjectNode record) throws IOException
  {
    String existingIndex = storage.readIndex(crate);
    String merged = CargoIndexBuilder.mergeRecord(existingIndex, record.toString());

    CargoCrateSummary previous = storage.crate(crate).orElse(null);
    CargoCrateSummary summary = new CargoCrateSummary(
        CargoCoordinatesHelper.lower(crate),
        CargoIndexBuilder.text(record, "description").isEmpty()
            ? (previous == null ? "" : previous.getDescription())
            : CargoIndexBuilder.text(record, "description"),
        CargoIndexBuilder.maxVersion(merged),
        record.has("downloads")
            ? record.path("downloads").asLong(previous == null ? 0 : previous.getDownloads())
            : (previous == null ? 0 : previous.getDownloads()),
        now());

    storage.writeIndex(crate, merged, summary);
    return json(CargoIndexBuilder.okResponse());
  }

  //
  // DELETE
  //

  private Response delete(final CargoContentFacet storage, final String path) throws IOException {
    Optional<CrateCoordinates> coordinates = CargoCoordinatesHelper.parsePublish(path);
    if (coordinates.isPresent()) {
      String crate = coordinates.get().getCrate();
      String version = coordinates.get().getVersion();

      boolean deleted = storage.delete(CargoCoordinatesHelper.cratePath(crate, version));

      String existing = storage.readIndex(crate);
      String remaining = CargoIndexBuilder.removeRecord(existing, version);
      if (remaining.isEmpty()) {
        deleted |= storage.delete(CargoCoordinatesHelper.indexPath(crate));
      }
      else {
        CargoCrateSummary summary = storage.crate(crate)
            .orElseGet(() -> new CargoCrateSummary(CargoCoordinatesHelper.lower(crate), "", "", 0, now()));
        storage.writeIndex(crate, remaining,
            new CargoCrateSummary(summary.getName(), summary.getDescription(),
                CargoIndexBuilder.maxVersion(remaining), summary.getDownloads(), now()));
      }
      return deleted ? noContentResponse() : notFound(path);
    }

    if (CargoCoordinatesHelper.isIndexPath(path)) {
      return storage.delete(path) ? noContentResponse() : notFound(path);
    }
    return notFound(path);
  }

  //
  // helpers
  //

  private static Response noContentResponse() {
    return new Response.Builder().status(Status.success(204)).build();
  }

  private static Optional<String> existingCksum(
      final CargoContentFacet storage,
      final String crate,
      final String version) throws IOException
  {
    Optional<Content> stored = storage.get(CargoCoordinatesHelper.cratePath(crate, version));
    if (stored.isPresent()) {
      try (InputStream input = stored.get().openInputStream()) {
        return Optional.of(CargoIndexBuilder.sha256Hex(input));
      }
    }
    ObjectNode record = CargoIndexBuilder.findRecord(storage.readIndex(crate), version);
    if (record != null) {
      String cksum = CargoIndexBuilder.text(record, "cksum");
      if (!cksum.isEmpty()) {
        return Optional.of(cksum);
      }
    }
    return Optional.empty();
  }

  private static boolean isJsonPayload(final Request request, final byte[] body) {
    Payload payload = request.getPayload();
    String contentType = payload == null || payload.getContentType() == null ? "" : payload.getContentType();
    if (contentType.toLowerCase(Locale.ROOT).contains("json")) {
      return true;
    }
    int index = 0;
    while (index < body.length && Character.isWhitespace(body[index])) {
      index++;
    }
    return index < body.length && body[index] == '{';
  }

  /**
   * Serves crate archives with {@code Content-Type: application/gzip} as required by the cargo protocol.
   */
  private static Response asGzip(final Content content) {
    return ok(new Content(content, new CargoPayload(content.getPayload(), APPLICATION_GZIP_MIME)));
  }

  private static Response json(final String body) {
    return ok(new BytesPayload(body.getBytes(StandardCharsets.UTF_8), APPLICATION_JSON));
  }

  private static Response error(final int code, final String message) {
    ObjectNode detail = MAPPER.createObjectNode();
    detail.put("detail", message);
    ArrayNode errors = MAPPER.createArrayNode();
    errors.add(detail);
    ObjectNode root = MAPPER.createObjectNode();
    root.set("errors", errors);
    String body = root.toString();

    return new Response.Builder()
        .status(Status.failure(code, message))
        .payload(new BytesPayload(body.getBytes(StandardCharsets.UTF_8), APPLICATION_JSON))
        .build();
  }

  private static int positiveInt(final String value, final int fallback) {
    if (value == null) {
      return fallback;
    }
    try {
      int parsed = Integer.parseInt(value.trim());
      return parsed > 0 ? parsed : fallback;
    }
    catch (NumberFormatException e) {
      return fallback;
    }
  }

  private static String now() {
    return DateTimeFormatter.ISO_INSTANT.format(Instant.now());
  }

  private static String repositoryBase(final Context context) {
    String url = context.getRepository().getUrl();
    return url.endsWith("/") ? url : url + "/";
  }

  @Nonnull
  private String contentPath(final Context context) {
    TokenMatcher.State state = context.getAttributes().require(TokenMatcher.State.class);
    return CargoCoordinatesHelper.normalize(state.getTokens().get(CargoRecipeSupport.PATH_NAME));
  }
}
