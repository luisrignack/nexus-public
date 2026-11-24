/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 */
package org.sonatype.nexus.internal.security.saml;

/**
 * Property names of the {@code saml} capability.
 */
public final class SamlCapabilityProperties
{
  public static final String ENABLED = "enabled";

  public static final String ENTITY_ID = "entityId";

  public static final String IDP_METADATA = "idpMetadata";

  public static final String USERNAME_ATTRIBUTE = "usernameAttribute";

  public static final String FIRST_NAME_ATTRIBUTE = "firstNameAttribute";

  public static final String LAST_NAME_ATTRIBUTE = "lastNameAttribute";

  public static final String EMAIL_ATTRIBUTE = "emailAttribute";

  public static final String GROUPS_ATTRIBUTE = "groupsAttribute";

  public static final String VALIDATE_RESPONSE_SIGNATURE = "validateResponseSignature";

  public static final String VALIDATE_ASSERTION_SIGNATURE = "validateAssertionSignature";

  private SamlCapabilityProperties() {
    // utility class
  }
}
