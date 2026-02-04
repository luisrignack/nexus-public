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
package org.sonatype.nexus.common.collect.json;

import java.io.IOException;
import java.util.Map;

import org.sonatype.nexus.common.collect.NestedAttributesMap;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

/**
 * Custom Jackson deserializer for {@link NestedAttributesMap} that deserializes from a simple map structure,
 * complementing the {@link NestedAttributesMapSerializer}.
 *
 * @since 3.75
 */
public class NestedAttributesMapDeserializer
    extends JsonDeserializer<NestedAttributesMap>
{
  private static final TypeReference<Map<String, Object>> MAP_TYPE_REF = new TypeReference<Map<String, Object>>()
  {
  };

  @Override
  public NestedAttributesMap deserialize(
      final JsonParser p,
      final DeserializationContext ctxt) throws IOException
  {
    if (p.currentToken() == null || p.currentToken().isScalarValue() && p.getText() == null) {
      return null;
    }

    // Deserialize as a Map and wrap in a NestedAttributesMap
    Map<String, Object> backing = p.readValueAs(MAP_TYPE_REF);
    return new NestedAttributesMap("attributes", backing);
  }
}
