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
package org.sonatype.nexus.repository.alpine;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Metadata of an Alpine {@code .apk} package (APK v2 format) read from its {@code .PKGINFO} file.
 *
 * <p>The package identity written to the {@code C:} field of an {@code APKINDEX} is the SHA1 of the
 * compressed gzip member that holds the control section (the one containing {@code .PKGINFO}),
 * encoded as {@code Q1} followed by the Base64 digest. That is exactly what {@code apk index}
 * computes, so indexes generated here are interchangeable with the ones produced by apk-tools.</p>
 */
public class AlpinePackageMetadata
{
  private static final int BLOCK = 512;

  private static final int MAX_MEMBERS = 8;

  private final String checksum;

  private final String name;

  private final String version;

  private final String arch;

  private final long fileSize;

  private final long installedSize;

  private final String description;

  private final String url;

  private final String license;

  private final String origin;

  private final String maintainer;

  private final long buildTime;

  private final String commit;

  private final int providerPriority;

  private final List<String> depends;

  private final List<String> provides;

  private final List<String> installIf;

  @SuppressWarnings("squid:S00107") // metadata value object
  public AlpinePackageMetadata(
      final String checksum,
      final String name,
      final String version,
      final String arch,
      final long fileSize,
      final long installedSize,
      final String description,
      final String url,
      final String license,
      final String origin,
      final String maintainer,
      final long buildTime,
      final String commit,
      final int providerPriority,
      final List<String> depends,
      final List<String> provides,
      final List<String> installIf)
  {
    this.checksum = checksum;
    this.name = name;
    this.version = version;
    this.arch = arch;
    this.fileSize = fileSize;
    this.installedSize = installedSize;
    this.description = description;
    this.url = url;
    this.license = license;
    this.origin = origin;
    this.maintainer = maintainer;
    this.buildTime = buildTime;
    this.commit = commit;
    this.providerPriority = providerPriority;
    this.depends = depends;
    this.provides = provides;
    this.installIf = installIf;
  }

  /**
   * Reads the metadata of an APK v2 package. Only the control gzip member is inflated, so the
   * (potentially large) data section is never decompressed.
   */
  public static AlpinePackageMetadata read(final InputStream input, final long fileSize) throws IOException {
    PushbackInputStream in = new PushbackInputStream(new BufferedInputStream(input, 65536), 65536);
    for (int member = 0; member < MAX_MEMBERS; member++) {
      int first = in.read();
      if (first < 0) {
        break;
      }
      int second = in.read();
      if (first != 0x1f || second != 0x8b) {
        throw new IllegalArgumentException("Not an APK v2 package: expected a gzip member");
      }
      in.unread(second);
      in.unread(first);

      GzipMember control = readMember(in);
      Map<String, List<String>> pkginfo = findPkginfo(control.data);
      if (pkginfo != null) {
        return fromPkginfo(pkginfo, control.sha1, fileSize);
      }
    }
    throw new IllegalArgumentException("Not an APK v2 package: .PKGINFO not found");
  }

  private static AlpinePackageMetadata fromPkginfo(
      final Map<String, List<String>> pkginfo,
      final byte[] identity,
      final long fileSize)
  {
    String name = first(pkginfo, "pkgname");
    String version = first(pkginfo, "pkgver");
    if (name == null || name.isEmpty() || version == null || version.isEmpty()) {
      throw new IllegalArgumentException("Not an APK v2 package: .PKGINFO lacks pkgname/pkgver");
    }
    return new AlpinePackageMetadata(
        "Q1" + Base64.getEncoder().encodeToString(identity),
        name,
        version,
        nullToEmpty(first(pkginfo, "arch")),
        fileSize,
        parseLong(first(pkginfo, "size")),
        nullToEmpty(first(pkginfo, "pkgdesc")),
        nullToEmpty(first(pkginfo, "url")),
        nullToEmpty(first(pkginfo, "license")),
        nullToEmpty(first(pkginfo, "origin")),
        nullToEmpty(first(pkginfo, "maintainer")),
        parseLong(first(pkginfo, "builddate")),
        nullToEmpty(first(pkginfo, "commit")),
        parseInt(first(pkginfo, "provider_priority")),
        pkginfo.getOrDefault("depend", Collections.emptyList()),
        pkginfo.getOrDefault("provides", Collections.emptyList()),
        pkginfo.getOrDefault("install_if", Collections.emptyList()));
  }

  private static String first(final Map<String, List<String>> values, final String key) {
    List<String> list = values.get(key);
    return list == null || list.isEmpty() ? null : list.get(0);
  }

  private static String nullToEmpty(final String value) {
    return value == null ? "" : value;
  }

  private static long parseLong(final String value) {
    try {
      return value == null || value.isEmpty() ? 0 : Long.parseLong(value.trim());
    }
    catch (NumberFormatException e) {
      return 0;
    }
  }

  private static int parseInt(final String value) {
    try {
      return value == null || value.isEmpty() ? 0 : Integer.parseInt(value.trim());
    }
    catch (NumberFormatException e) {
      return 0;
    }
  }

  /**
   * Renders the stanza written to {@code APKINDEX}, matching {@code apk_pkg_write_index_header()}.
   * The returned value ends with a blank line, exactly like apk-tools writes it.
   */
  public String toIndexEntry() {
    StringBuilder entry = new StringBuilder(256);
    entry.append("C:").append(checksum).append('\n');
    entry.append("P:").append(name).append('\n');
    entry.append("V:").append(version).append('\n');
    if (!arch.isEmpty()) {
      entry.append("A:").append(arch).append('\n');
    }
    entry.append("S:").append(fileSize).append('\n');
    entry.append("I:").append(installedSize).append('\n');
    entry.append("T:").append(description).append('\n');
    entry.append("U:").append(url).append('\n');
    entry.append("L:").append(license).append('\n');
    if (!origin.isEmpty()) {
      entry.append("o:").append(origin).append('\n');
    }
    if (!maintainer.isEmpty()) {
      entry.append("m:").append(maintainer).append('\n');
    }
    if (buildTime != 0) {
      entry.append("t:").append(buildTime).append('\n');
    }
    if (!commit.isEmpty()) {
      entry.append("c:").append(commit).append('\n');
    }
    if (providerPriority != 0) {
      entry.append("k:").append(providerPriority).append('\n');
    }
    appendDependencies(entry, "D:", depends);
    appendDependencies(entry, "p:", provides);
    appendDependencies(entry, "i:", installIf);
    entry.append('\n');
    return entry.toString();
  }

  private static void appendDependencies(final StringBuilder entry, final String field, final List<String> values) {
    if (values.isEmpty()) {
      return;
    }
    entry.append(field);
    for (int i = 0; i < values.size(); i++) {
      if (i > 0) {
        entry.append(' ');
      }
      entry.append(values.get(i));
    }
    entry.append('\n');
  }

  private static GzipMember readMember(final PushbackInputStream in) throws IOException {
    MessageDigest digest = sha1();
    readGzipHeader(in, digest);

    Inflater inflater = new Inflater(true);
    ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
    byte[] output = new byte[16384];
    byte[] chunk = new byte[16384];
    int chunkLength = 0;
    int hashed = 0;
    try {
      while (!inflater.finished()) {
        if (inflater.needsInput()) {
          chunkLength = in.read(chunk);
          if (chunkLength < 0) {
            throw new EOFException("Truncated gzip member");
          }
          hashed = 0;
          inflater.setInput(chunk, 0, chunkLength);
        }
        int produced;
        try {
          produced = inflater.inflate(output);
        }
        catch (DataFormatException e) {
          throw new IOException("Invalid gzip member", e);
        }
        if (produced > 0) {
          out.write(output, 0, produced);
        }
        else if (inflater.needsDictionary()) {
          throw new IOException("gzip preset dictionaries are not supported");
        }
        else if (!inflater.finished() && !inflater.needsInput()) {
          throw new IOException("Stalled gzip stream");
        }
        int consumed = chunkLength - inflater.getRemaining();
        if (consumed > hashed) {
          digest.update(chunk, hashed, consumed - hashed);
          hashed = consumed;
        }
      }

      // the raw inflater stops before the 8-byte gzip trailer (CRC32 + ISIZE)
      int leftover = chunkLength - hashed;
      if (leftover >= 8) {
        digest.update(chunk, hashed, 8);
        in.unread(chunk, hashed + 8, leftover - 8);
      }
      else {
        if (leftover > 0) {
          digest.update(chunk, hashed, leftover);
        }
        skipAndHash(in, digest, 8 - leftover);
      }
      return new GzipMember(out.toByteArray(), digest.digest());
    }
    finally {
      inflater.end();
    }
  }

  private static void readGzipHeader(final PushbackInputStream in, final MessageDigest digest) throws IOException {
    byte[] header = new byte[10];
    readFully(in, header, 0, header.length);
    digest.update(header);
    if ((header[0] & 0xff) != 0x1f || (header[1] & 0xff) != 0x8b || header[2] != 8) {
      throw new IOException("Not a gzip member");
    }
    int flags = header[3] & 0xff;
    if ((flags & 0x04) != 0) {
      byte[] extraLength = new byte[2];
      readFully(in, extraLength, 0, 2);
      digest.update(extraLength);
      int length = (extraLength[0] & 0xff) | ((extraLength[1] & 0xff) << 8);
      skipAndHash(in, digest, length);
    }
    if ((flags & 0x08) != 0) {
      skipZeroTerminated(in, digest);
    }
    if ((flags & 0x10) != 0) {
      skipZeroTerminated(in, digest);
    }
    if ((flags & 0x02) != 0) {
      skipAndHash(in, digest, 2);
    }
  }

  private static void readFully(final InputStream in, final byte[] buffer, final int offset, final int length)
      throws IOException
  {
    int read = 0;
    while (read < length) {
      int count = in.read(buffer, offset + read, length - read);
      if (count < 0) {
        throw new EOFException("Truncated gzip member");
      }
      read += count;
    }
  }

  private static void skipAndHash(final InputStream in, final MessageDigest digest, final int length) throws IOException {
    byte[] buffer = new byte[Math.max(1, length)];
    readFully(in, buffer, 0, length);
    digest.update(buffer, 0, length);
  }

  private static void skipZeroTerminated(final InputStream in, final MessageDigest digest) throws IOException {
    int value;
    do {
      value = in.read();
      if (value < 0) {
        throw new EOFException("Truncated gzip member");
      }
      digest.update((byte) value);
    }
    while (value != 0);
  }

  private static MessageDigest sha1() {
    try {
      return MessageDigest.getInstance("SHA-1");
    }
    catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static Map<String, List<String>> findPkginfo(final byte[] tar) {
    int offset = 0;
    while (offset + BLOCK <= tar.length) {
      String name = cString(tar, offset, 100);
      if (name.isEmpty()) {
        break;
      }
      long size = octal(tar, offset + 124, 12);
      int type = tar[offset + 156] & 0xff;
      // PAX headers are named "./PaxHeaders/<file>" and must not be mistaken for the file itself
      boolean extendedHeader = type == 'x' || type == 'g' || name.startsWith("./PaxHeaders/")
          || name.startsWith("PaxHeaders/");
      if (!extendedHeader && (".PKGINFO".equals(name) || "./.PKGINFO".equals(name))) {
        int start = offset + BLOCK;
        int length = (int) Math.min(size, tar.length - start);
        return parsePkginfo(new String(tar, start, length, StandardCharsets.UTF_8));
      }
      offset += BLOCK + (int) ((size + BLOCK - 1) / BLOCK) * BLOCK;
    }
    return null;
  }

  private static Map<String, List<String>> parsePkginfo(final String content) {
    Map<String, List<String>> values = new LinkedHashMap<>();
    for (String line : content.split("\n")) {
      String trimmed = line.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("#")) {
        continue;
      }
      int separator = trimmed.indexOf(" = ");
      if (separator <= 0) {
        continue;
      }
      String key = trimmed.substring(0, separator);
      String value = trimmed.substring(separator + 3).trim();
      values.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
    }
    return values;
  }

  private static String cString(final byte[] data, final int offset, final int maxLength) {
    int end = offset;
    int limit = offset + maxLength;
    while (end < limit && data[end] != 0) {
      end++;
    }
    return new String(data, offset, end - offset, StandardCharsets.UTF_8);
  }

  private static long octal(final byte[] data, final int offset, final int length) {
    long value = 0;
    for (int i = offset; i < offset + length; i++) {
      byte b = data[i];
      if (b == 0 || b == ' ') {
        if (value != 0) {
          break;
        }
        continue;
      }
      if (b < '0' || b > '7') {
        break;
      }
      value = value * 8 + (b - '0');
    }
    return value;
  }

  public String getChecksum() {
    return checksum;
  }

  public String getName() {
    return name;
  }

  public String getVersion() {
    return version;
  }

  public String getArch() {
    return arch;
  }

  public long getFileSize() {
    return fileSize;
  }

  public long getInstalledSize() {
    return installedSize;
  }

  public String getDescription() {
    return description;
  }

  public String getUrl() {
    return url;
  }

  public String getLicense() {
    return license;
  }

  public String getOrigin() {
    return origin;
  }

  public String getMaintainer() {
    return maintainer;
  }

  public long getBuildTime() {
    return buildTime;
  }

  public String getCommit() {
    return commit;
  }

  public int getProviderPriority() {
    return providerPriority;
  }

  public List<String> getDepends() {
    return depends;
  }

  public List<String> getProvides() {
    return provides;
  }

  public List<String> getInstallIf() {
    return installIf;
  }

  private static final class GzipMember
  {
    private final byte[] data;

    private final byte[] sha1;

    private GzipMember(final byte[] data, final byte[] sha1) {
      this.data = data;
      this.sha1 = sha1;
    }
  }
}
