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
package org.sonatype.nexus.repository.go;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;

/**
 * Helper methods for the Go module proxy protocol (GOPROXY).
 *
 * <p>A module version request looks like {@code /{module}/@v/{version}.{ext}} where {@code module} is the
 * module path (which may contain slashes) and {@code ext} is one of {@code info}, {@code mod} or {@code zip}.
 * The version list is {@code /{module}/@v/list} and the latest version is {@code /{module}/@latest}.</p>
 */
public final class GoCoordinatesHelper
{
  public static final String AT_V = "@v";

  public static final String AT_LATEST = "@latest";

  public static final String LIST = "list";

  public static final String LATEST = "latest";

  public static final String INFO = "info";

  public static final String MOD = "mod";

  public static final String ZIP = "zip";

  public static final String ZIPHASH = "ziphash";

  private GoCoordinatesHelper() {
  }

  /**
   * Coordinates parsed from a Go module proxy path.
   */
  public static final class Coordinates
  {
    private final String module;

    private final String version;

    private final String kind;

    private Coordinates(final String module, final String version, final String kind) {
      this.module = module;
      this.version = version;
      this.kind = kind;
    }

    public String getModule() {
      return module;
    }

    public String getVersion() {
      return version;
    }

    public String getKind() {
      return kind;
    }

    public boolean isList() {
      return LIST.equals(kind);
    }

    public boolean isLatest() {
      return LATEST.equals(kind);
    }

    public boolean isInfo() {
      return INFO.equals(kind);
    }

    public boolean isMod() {
      return MOD.equals(kind);
    }

    public boolean isZip() {
      return ZIP.equals(kind);
    }
  }

  /**
   * Parses a request path into module coordinates, or returns empty when the path is not a GOPROXY path.
   */
  public static Optional<Coordinates> parse(final String rawPath) {
    String path = decode(stripLeadingSlash(stripTrailingSlash(rawPath)));
    if (path == null || path.isEmpty()) {
      return Optional.empty();
    }

    int latest = path.lastIndexOf("/" + AT_LATEST);
    if (latest > 0 && latest + AT_LATEST.length() + 1 == path.length()) {
      return Optional.of(new Coordinates(path.substring(0, latest), null, LATEST));
    }

    int atV = path.lastIndexOf("/" + AT_V + "/");
    if (atV <= 0) {
      return Optional.empty();
    }

    String module = path.substring(0, atV);
    String rest = path.substring(atV + AT_V.length() + 2);
    if (LIST.equals(rest)) {
      return Optional.of(new Coordinates(module, null, LIST));
    }

    int dot = rest.lastIndexOf('.');
    if (dot <= 0 || dot == rest.length() - 1) {
      return Optional.empty();
    }

    String version = rest.substring(0, dot);
    String extension = rest.substring(dot + 1).toLowerCase(Locale.ROOT);
    String kind;
    switch (extension) {
      case INFO:
      case MOD:
      case ZIP:
      case ZIPHASH:
        kind = extension;
        break;
      default:
        return Optional.empty();
    }
    return Optional.of(new Coordinates(module, version, kind));
  }

  /**
   * URL-decodes a path.
   */
  public static String decode(final String value) {
    if (value == null) {
      return null;
    }
    try {
      return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name());
    }
    catch (UnsupportedEncodingException | IllegalArgumentException e) {
      return value;
    }
  }

  public static String stripLeadingSlash(final String path) {
    return path != null && path.startsWith("/") ? path.substring(1) : path;
  }

  public static String stripTrailingSlash(final String path) {
    if (path == null) {
      return "";
    }
    int end = path.length();
    while (end > 0 && path.charAt(end - 1) == '/') {
      end--;
    }
    return path.substring(0, end);
  }

  /**
   * Returns the file name of the last segment of a path or URL.
   */
  public static String fileName(final String path) {
    if (path == null) {
      return "";
    }
    String value = stripTrailingSlash(path);
    int slash = value.lastIndexOf('/');
    return slash >= 0 ? value.substring(slash + 1) : value;
  }

  /**
   * Base64url (unpadded) encoding of an upstream URL.
   */
  public static String encodeUpstreamUrl(final String url) {
    return Base64.getUrlEncoder().withoutPadding()
        .encodeToString(url.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Decodes a base64url token produced by {@link #encodeUpstreamUrl(String)}.
   */
  public static Optional<String> decodeUpstreamUrl(final String token) {
    try {
      byte[] decoded = Base64.getUrlDecoder().decode(token);
      return Optional.of(new String(decoded, StandardCharsets.UTF_8));
    }
    catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
