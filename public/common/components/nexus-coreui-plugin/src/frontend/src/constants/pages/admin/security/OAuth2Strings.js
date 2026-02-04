/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Open Source Version is distributed with Sencha Ext JS pursuant to a FLOSS Exception agreed upon
 * between Sonatype, Inc. and Sencha Inc. Sencha Ext JS is licensed under GPL v3 and cannot be redistributed as part of a
 * closed source work.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
import { faIdCard } from '@fortawesome/free-solid-svg-icons';

export default {
  OAUTH2_CONFIGURATION: {
    MENU: {
      text: 'OAuth 2.0',
      description: 'Configure your OAuth 2.0 Settings',
      icon: faIdCard
    },
    FIELDS: {
      IDP_JWKS_URL: 'JSON Web Key URL',
      IDP_JWS_ALGORITHM: 'JWT Signature Algorithm',
      IDP_JWKS: 'JSON Web Keys',
      USERNAME_CLAIM: 'Username Claim',
      FIRST_NAME_CLAIM: 'First Name Claim',
      LAST_NAME_CLAIM: 'Last Name Claim',
      EMAIL_CLAIM: 'Email Claim',
      GROUPS_CLAIM: 'Groups Claim',
      EXACT_MATCH_CLAIMS: 'Exact Match Claims',
      CLIENT_ID: 'Client ID',
      CLIENT_SECRET: 'Client Secret',
      IDP_AUTHORIZATION_URL: 'Authorization URL',
      IDP_LOGOUT_URL: 'Logout URL',
      IDP_TOKEN_URL: 'Token URL',
      ADVANCED_SETTINGS: 'Advanced Settings',
      AUTHORIZATION_CUSTOM_PARAMS: 'Authorization Custom Parameters',
      TOKEN_REQUEST_CUSTOM_PARAMS: 'Token Request Custom Parameters',
      OPTIONAL_JWKS: 'Optional: Provide either JSON Web Keys or a URL to fetch the keys from the Identity Provider'
    },
    MESSAGES: {
      LOAD_ERROR: 'An error occurred while attempting to load the OAuth 2.0 configuration',
      SAVE_ERROR: 'An error occurred while attempting to save the OAuth 2.0 configuration',
      SAVE_SUCCESS: 'OAuth 2.0 configuration updated'
    },
    LABELS: {
      FIELDS: 'OAuth 2.0 Field Mappings',
      OAUTH_2_0_CONFIGURATION: "OAuth 2.0 Configuration",
      OIDC_SETTINGS: 'OIDC Settings',
      JWT_SETTINGS: 'JWT Settings',
      CLAIM_MAPPINGS: 'Claim Mappings',
      SUBLABELS: {
        REQUIRED_FIELDS: 'Required fields are marked with an asterisk',
        CLIENT_ID: 'The client ID registered with your identity provider.',
        CLIENT_SECRET: 'The client secret registered with your identity provider.',
        IDP_AUTHORIZATION_URL: "The authorization endpoint from your identity provider's .well-known configuration.",
        IDP_LOGOUT_URL: "The logout or end-session endpoint from your identity provider's .well-known configuration.",
        IDP_TOKEN_URL: "The token endpoint from your identity provider's .well-known configuration.",
        IDP_JWKS_URL: "The JWKS URI from your identity provider's .well-known configuration.",
        USERNAME_CLAIM: "The claim in the token that contains the username.",
        FIRST_NAME_CLAIM: "The claim in the token that contains the user's first name.",
        LAST_NAME_CLAIM: "The claim in the token that contains the user's last name.",
        EMAIL_CLAIM: "The claim in the token that contains the user's email address.",
        GROUPS_CLAIM: "The claim in the token that contains the user's groups.",
        IDP_JWS_ALGORITHM: "The signing algorithm used for ID tokens (e.g., RS256).",
        IDP_JWKS: "Paste the JWKS JSON here if not using the JWKS URI.",
        AUTHORIZATION_CUSTOM_PARAMS: "Custom parameters to include in the authorization request (JSON format).",
        TOKEN_REQUEST_CUSTOM_PARAMS: "Custom parameters to include in the token request (JSON format).",
        EXACT_MATCH_CLAIMS: "Claims that must match exactly (JSON format)."
      }
    }
  }
};