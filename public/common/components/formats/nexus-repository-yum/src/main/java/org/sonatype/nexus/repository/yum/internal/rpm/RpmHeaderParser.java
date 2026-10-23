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
package org.sonatype.nexus.repository.yum.internal.rpm;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.io.CountingInputStream;

/**
 * Minimal, dependency-free reader for the RPM lead and header structures.
 *
 * <p>Only the metadata required to build Yum/DNF repository metadata is extracted. The payload is never read.
 * The parser intentionally validates all offsets and lengths against the header data block so malformed packages
 * cannot trigger out-of-bounds reads.
 */
public final class RpmHeaderParser
{
  private static final int LEAD_SIZE = 96;

  private static final int HEADER_MAGIC = 0x8eade801;

  private static final int MAX_INDEX_ENTRIES = 100_000;

  private static final int MAX_HEADER_DATA = 64 * 1024 * 1024;

  // RPM header tags
  private static final int TAG_NAME = 1000;

  private static final int TAG_VERSION = 1001;

  private static final int TAG_RELEASE = 1002;

  private static final int TAG_EPOCH = 1003;

  private static final int TAG_SUMMARY = 1004;

  private static final int TAG_DESCRIPTION = 1005;

  private static final int TAG_BUILDTIME = 1006;

  private static final int TAG_BUILDHOST = 1007;

  private static final int TAG_SIZE = 1009;

  private static final int TAG_VENDOR = 1011;

  private static final int TAG_LICENSE = 1014;

  private static final int TAG_PACKAGER = 1015;

  private static final int TAG_GROUP = 1016;

  private static final int TAG_URL = 1020;

  private static final int TAG_ARCH = 1022;

  private static final int TAG_OLDFILENAMES = 1027;

  private static final int TAG_FILEMTIMES = 1034;

  private static final int TAG_SOURCERPM = 1044;

  private static final int TAG_ARCHIVESIZE = 1046;

  private static final int TAG_PROVIDENAME = 1047;

  private static final int TAG_REQUIREFLAGS = 1048;

  private static final int TAG_REQUIRENAME = 1049;

  private static final int TAG_REQUIREVERSION = 1050;

  private static final int TAG_CONFLICTFLAGS = 1053;

  private static final int TAG_CONFLICTNAME = 1054;

  private static final int TAG_CONFLICTVERSION = 1055;

  private static final int TAG_CHANGELOGTIME = 1080;

  private static final int TAG_CHANGELOGNAME = 1081;

  private static final int TAG_CHANGELOGTEXT = 1082;

  private static final int TAG_OBSOLETENAME = 1090;

  private static final int TAG_PROVIDEFLAGS = 1112;

  private static final int TAG_PROVIDEVERSION = 1113;

  private static final int TAG_OBSOLETEFLAGS = 1114;

  private static final int TAG_OBSOLETEVERSION = 1115;

  private static final int TAG_DIRINDEXES = 1116;

  private static final int TAG_BASENAMES = 1117;

  private static final int TAG_DIRNAMES = 1118;

  private static final int TYPE_INT8 = 2;

  private static final int TYPE_INT16 = 3;

  private static final int TYPE_INT32 = 4;

  private static final int TYPE_INT64 = 5;

  private static final int TYPE_STRING = 6;

  private static final int TYPE_STRING_ARRAY = 8;

  private static final int TYPE_I18NSTRING = 9;

  private RpmHeaderParser() {
    // static utility
  }

  /**
   * Parses the RPM header and returns the package metadata.
   *
   * @param input       RPM stream; only the header is consumed
   * @param location    repository path of the package (may start with {@code /})
   * @param sha256      SHA-256 of the complete package, used as Yum's {@code pkgid}
   * @param packageSize size of the complete package in bytes
   */
  public static RpmPackage parse(
      final InputStream input,
      final String location,
      final String sha256,
      final long packageSize) throws IOException
  {
    CountingInputStream counting = new CountingInputStream(input);

    byte[] lead = readFully(counting, LEAD_SIZE);
    if ((lead[0] & 0xff) != 0xed || (lead[1] & 0xff) != 0xab
        || (lead[2] & 0xff) != 0xee || (lead[3] & 0xff) != 0xdb) {
      throw new IOException("Not an RPM package (bad lead magic)");
    }

    // signature header: consume it, including its 8-byte alignment padding
    readHeader(counting);
    long afterSignature = counting.getCount();
    skipFully(counting, (8 - (afterSignature % 8)) % 8);

    long headerStart = counting.getCount();
    Header header = readHeader(counting);
    long headerEnd = counting.getCount();

    String name = required(header, TAG_NAME, "name");
    String version = required(header, TAG_VERSION, "version");
    String release = required(header, TAG_RELEASE, "release");
    String arch = defaultIfBlank(header.firstString(TAG_ARCH), "noarch");

    List<String> files = files(header);
    List<RpmDependency> provides = dependencies(header, TAG_PROVIDENAME, TAG_PROVIDEFLAGS, TAG_PROVIDEVERSION);
    List<RpmDependency> requires = dependencies(header, TAG_REQUIRENAME, TAG_REQUIREFLAGS, TAG_REQUIREVERSION);
    List<RpmDependency> conflicts = dependencies(header, TAG_CONFLICTNAME, TAG_CONFLICTFLAGS, TAG_CONFLICTVERSION);
    List<RpmDependency> obsoletes = dependencies(header, TAG_OBSOLETENAME, TAG_OBSOLETEFLAGS, TAG_OBSOLETEVERSION);

    String normalizedLocation = location.startsWith("/") ? location.substring(1) : location;

    return new RpmPackage(
        name,
        header.firstInt(TAG_EPOCH, 0),
        version,
        release,
        arch,
        defaultIfBlank(header.firstString(TAG_SUMMARY), ""),
        defaultIfBlank(header.firstString(TAG_DESCRIPTION), ""),
        defaultIfBlank(header.firstString(TAG_PACKAGER), ""),
        defaultIfBlank(header.firstString(TAG_URL), ""),
        defaultIfBlank(header.firstString(TAG_LICENSE), ""),
        defaultIfBlank(header.firstString(TAG_VENDOR), ""),
        defaultIfBlank(header.firstString(TAG_GROUP), ""),
        defaultIfBlank(header.firstString(TAG_BUILDHOST), ""),
        defaultIfBlank(header.firstString(TAG_SOURCERPM), ""),
        header.firstInt(TAG_BUILDTIME, 0),
        header.firstInt(TAG_SIZE, 0),
        header.firstInt(TAG_ARCHIVESIZE, 0),
        headerStart,
        headerEnd,
        normalizedLocation,
        sha256,
        packageSize,
        files,
        provides,
        requires,
        conflicts,
        obsoletes,
        changelogs(header));
  }

  private static Header readHeader(final InputStream input) throws IOException {
    byte[] intro = readFully(input, 16);
    int magic = ((intro[0] & 0xff) << 24)
        | ((intro[1] & 0xff) << 16)
        | ((intro[2] & 0xff) << 8)
        | (intro[3] & 0xff);
    if (magic != HEADER_MAGIC) {
      throw new IOException(String.format("Bad RPM header magic 0x%08x", magic));
    }

    int indexCount = readInt32(intro, 8);
    int dataSize = readInt32(intro, 12);
    if (indexCount < 0 || indexCount > MAX_INDEX_ENTRIES) {
      throw new IOException("Invalid RPM header index size: " + indexCount);
    }
    if (dataSize < 0 || dataSize > MAX_HEADER_DATA) {
      throw new IOException("Invalid RPM header data size: " + dataSize);
    }

    byte[] index = readFully(input, indexCount * 16);
    byte[] data = readFully(input, dataSize);
    return new Header(index, data);
  }

  private static List<String> files(final Header header) {
    List<String> dirs = header.strings(TAG_DIRNAMES);
    List<String> bases = header.strings(TAG_BASENAMES);
    long[] indexes = header.ints(TAG_DIRINDEXES);
    List<String> files = new ArrayList<>();

    if (!bases.isEmpty() && !dirs.isEmpty() && indexes.length > 0) {
      int count = Math.min(bases.size(), indexes.length);
      for (int i = 0; i < count; i++) {
        String base = bases.get(i);
        int dirIndex = (int) indexes[i];
        if (base == null || base.isEmpty() || dirIndex < 0 || dirIndex >= dirs.size()) {
          continue;
        }
        String dir = dirs.get(dirIndex);
        if (dir == null) {
          continue;
        }
        files.add(dir.endsWith("/") ? dir + base : dir + "/" + base);
      }
    }
    else {
      // RPM v3 style: OLDFILENAMES stores complete paths
      files.addAll(header.strings(TAG_OLDFILENAMES));
    }

    return files;
  }

  private static List<RpmDependency> dependencies(
      final Header header,
      final int nameTag,
      final int flagsTag,
      final int versionTag)
  {
    List<String> names = header.strings(nameTag);
    long[] flags = header.ints(flagsTag);
    List<String> versions = header.strings(versionTag);
    List<RpmDependency> result = new ArrayList<>(names.size());

    for (int i = 0; i < names.size(); i++) {
      String name = names.get(i);
      if (name == null || name.isEmpty()) {
        continue;
      }
      int flag = i < flags.length ? (int) flags[i] : 0;
      String evr = i < versions.size() ? versions.get(i) : null;
      long epoch = 0;
      String version = "";
      String release = "";

      if (evr != null && !evr.isEmpty()) {
        String remainder = evr;
        int colon = remainder.indexOf(':');
        if (colon > 0) {
          try {
            epoch = Long.parseLong(remainder.substring(0, colon));
            remainder = remainder.substring(colon + 1);
          }
          catch (NumberFormatException e) {
            // Some dependencies use names containing a colon; keep the whole value as the version.
            remainder = evr;
            epoch = 0;
          }
        }
        int dash = remainder.lastIndexOf('-');
        if (dash > 0) {
          version = remainder.substring(0, dash);
          release = remainder.substring(dash + 1);
        }
        else {
          version = remainder;
        }
      }

      result.add(new RpmDependency(name, flag, epoch, version, release));
    }
    return result;
  }

  private static List<RpmChangelog> changelogs(final Header header) {
    long[] times = header.ints(TAG_CHANGELOGTIME);
    List<String> names = header.strings(TAG_CHANGELOGNAME);
    List<String> texts = header.strings(TAG_CHANGELOGTEXT);
    int count = Math.min(times.length, Math.min(names.size(), texts.size()));
    List<RpmChangelog> result = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      result.add(new RpmChangelog(times[i], defaultIfBlank(names.get(i), ""), defaultIfBlank(texts.get(i), "")));
    }
    return result;
  }

  private static String required(final Header header, final int tag, final String field) throws IOException {
    String value = header.firstString(tag);
    if (value == null || value.isEmpty()) {
      throw new IOException("RPM header is missing required field: " + field);
    }
    return value;
  }

  private static String defaultIfBlank(final String value, final String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private static byte[] readFully(final InputStream input, final int length) throws IOException {
    if (length == 0) {
      return new byte[0];
    }
    byte[] buffer = new byte[length];
    int offset = 0;
    while (offset < length) {
      int read = input.read(buffer, offset, length - offset);
      if (read < 0) {
        throw new IOException("Unexpected end of RPM while reading header");
      }
      offset += read;
    }
    return buffer;
  }

  private static void skipFully(final InputStream input, final long length) throws IOException {
    long remaining = length;
    while (remaining > 0) {
      long skipped = input.skip(remaining);
      if (skipped <= 0) {
        if (input.read() < 0) {
          throw new IOException("Unexpected end of RPM while skipping padding");
        }
        skipped = 1;
      }
      remaining -= skipped;
    }
  }

  private static int readInt32(final byte[] bytes, final int offset) {
    return ((bytes[offset] & 0xff) << 24)
        | ((bytes[offset + 1] & 0xff) << 16)
        | ((bytes[offset + 2] & 0xff) << 8)
        | (bytes[offset + 3] & 0xff);
  }

  private static final class Header
  {
    private final byte[] index;

    private final byte[] data;

    private Header(final byte[] index, final byte[] data) {
      this.index = index;
      this.data = data;
    }

    private Entry entry(final int tag) {
      for (int i = 0; i < index.length; i += 16) {
        if (readInt32(index, i) == tag) {
          return new Entry(
              readInt32(index, i + 4),
              readInt32(index, i + 8),
              readInt32(index, i + 12));
        }
      }
      return null;
    }

    String firstString(final int tag) {
      List<String> values = strings(tag);
      return values.isEmpty() ? null : values.get(0);
    }

    List<String> strings(final int tag) {
      Entry entry = entry(tag);
      List<String> result = new ArrayList<>();
      if (entry == null || (entry.type != TYPE_STRING && entry.type != TYPE_STRING_ARRAY
          && entry.type != TYPE_I18NSTRING)) {
        return result;
      }
      int position = entry.offset;
      for (int i = 0; i < entry.count; i++) {
        if (position < 0 || position >= data.length) {
          break;
        }
        int end = position;
        while (end < data.length && data[end] != 0) {
          end++;
        }
        result.add(new String(data, position, end - position, StandardCharsets.UTF_8));
        position = end + 1;
      }
      return result;
    }

    long[] ints(final int tag) {
      Entry entry = entry(tag);
      if (entry == null || (entry.type != TYPE_INT32 && entry.type != TYPE_INT64
          && entry.type != TYPE_INT16 && entry.type != TYPE_INT8)) {
        return new long[0];
      }
      int width = switch (entry.type) {
        case TYPE_INT64 -> 8;
        case TYPE_INT32 -> 4;
        case TYPE_INT16 -> 2;
        default -> 1;
      };
      long[] result = new long[entry.count];
      for (int i = 0; i < entry.count; i++) {
        int position = entry.offset + i * width;
        if (position < 0 || position + width > data.length) {
          break;
        }
        long value = 0;
        for (int b = 0; b < width; b++) {
          value = (value << 8) | (data[position + b] & 0xffL);
        }
        result[i] = value;
      }
      return result;
    }

    long firstInt(final int tag, final long fallback) {
      long[] values = ints(tag);
      return values.length == 0 ? fallback : values[0];
    }
  }

  private static final class Entry
  {
    private final int type;

    private final int offset;

    private final int count;

    private Entry(final int type, final int offset, final int count) {
      this.type = type;
      this.offset = offset;
      this.count = count;
    }
  }
}
