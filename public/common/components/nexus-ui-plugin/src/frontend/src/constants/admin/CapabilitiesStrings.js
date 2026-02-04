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

import { faServer } from '@fortawesome/free-solid-svg-icons';

export default {
  CAPABILITIES: {
    MENU: {
      text: 'Capabilities',
      description: 'Manage system capabilities for integrations and automation features.',
      icon: faServer,
    },
    LIST: {
      CREATE_BUTTON: 'Create Capability',
      EMPTY_LIST: 'No capabilities were found.',
      COLUMNS: {
        TYPE: 'Type',
        STATE: 'State',
        CATEGORY: 'Category',
        REPOSITORY: 'Repository',
        DESCRIPTION: 'Description',
        NOTES: 'Notes',
      },
    },
    CREATE: {
      TITLE: 'Create Capability',
      DESCRIPTION: 'Create system capabilities for integrations and automation features.',
      FORM: {
        TITLE: 'Select Capability',
        SELECT_TYPE: 'Select Type',
      },
    },
    EDIT: {
      FORM: {
        TITLE: 'Capability Settings',
        CAPABILITY_STATE: 'Capability State',
        ENABLED_LABEL: 'Enable',
        NOTES: 'Notes',
        NOTES_HELP_TEXT: 'Optional notes about configured capability',
      },
      LABELS: {
        TYPE: 'Type',
        DESCRIPTION: 'Description',
        STATE: 'State',
        CATEGORY: 'Category',
        STATUS: 'Status',
        ABOUT: 'About',
      },
    },
  },
};
