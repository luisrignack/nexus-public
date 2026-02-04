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

import React from "react";
import {
  NxH4,
  NxStatefulNavigationDropdown,
  NxNavigationDropdown,
  useToggle,
  NxTextLink,
  NxDropdown, NxP
} from '@sonatype/react-shared-components';
import { ExtJS } from '@sonatype/nexus-ui-plugin';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';

export const DocumentationUTMparams = {
  utm_medium: 'product',
  utm_source: 'nexus_repo',
  utm_campaign: 'menu-docs'
};
export const KnowledgeBaseUTMparams = {
  utm_medium: 'product',
  utm_source: 'nexus_repo',
  utm_campaign: 'menu-knowledge'
};
export const SonatypeGuidesUTMparams = {
  utm_medium: 'product',
  utm_source: 'nexus_repo',
  utm_campaign: 'menu-guides'
};
export const CommunityUTMparams = {
  utm_medium: 'product',
  utm_source: 'nexus_repo',
  utm_campaign: 'menu-community'
};
export const IssueTrackerUTMparams = {
  utm_medium: 'product',
  utm_source: 'nexus_repo',
  utm_campaign: 'menu-issuetracker'
};
export const SupportUTMparams = {
  utm_medium: 'product',
  utm_source: 'nexus_repo',
  utm_campaign: 'menu-support'
};

export default function HelpMenu() {
  const [isOpen, onToggleCollapse] = useToggle(false);

  const version = ExtJS.useState(() => ExtJS.state().getVersion());

  return (
      <NxStatefulNavigationDropdown
          isOpen={isOpen}
          onToggleCollapse={onToggleCollapse}
          icon={faQuestionCircle}
          title="Help"
          className="nxrm-global-header-help-menu"
          data-analytics-id="nxrm-global-header-help-icon"
      >
        <NxNavigationDropdown.MenuHeader>
          <NxH4>
            Nexus Repository Manager
          </NxH4>
          <NxP>{version}</NxP>
        </NxNavigationDropdown.MenuHeader>

        <button
            onClick={ExtJS.showAbout}
            className="nx-dropdown-link"
            data-analytics-id="nxrm-global-header-help-about">
          About
        </button>

        <NxTextLink
            external
            href={`https://links.sonatype.com/products/nexus/docs/${version}?${new URLSearchParams(DocumentationUTMparams).toString()}`}
            className="nx-dropdown-link"
            data-analytics-id="nxrm-global-header-help-documentation"
        >
          Documentation
        </NxTextLink>

        <NxTextLink
            external
            href={`https://links.sonatype.com/products/nexus/kb?${new URLSearchParams(KnowledgeBaseUTMparams).toString()}`}
            className="nx-dropdown-link"
            data-analytics-id="nxrm-global-header-help-knowledge-base"
        >
          Knowledge Base
        </NxTextLink>

        <NxTextLink
            external
            href={`https://links.sonatype.com/products/nxrm3/guides?${new URLSearchParams(SonatypeGuidesUTMparams).toString()}`}
            className="nx-dropdown-link"
            data-analytics-id="nxrm-global-header-help-guides"
        >
          Sonatype Guides
        </NxTextLink>

        <NxTextLink
            external
            href={`https://links.sonatype.com/products/nexus/community?${new URLSearchParams(CommunityUTMparams).toString()}`}
            className="nx-dropdown-link"
            data-analytics-id="nxrm-global-header-help-community"
        >
          Community
        </NxTextLink>

        <NxTextLink
            external
            href={`https://links.sonatype.com/products/nexus/issues?${new URLSearchParams(IssueTrackerUTMparams).toString()}`}
            className="nx-dropdown-link"
            data-analytics-id="nxrm-global-header-help-issue-tracker"
        >
          Issue Tracker
        </NxTextLink>

        <NxDropdown.Divider />

        <NxTextLink
            external
            href={`https://links.sonatype.com/products/nexus/support?${new URLSearchParams(SupportUTMparams).toString()}`}
            className="nx-dropdown-link"
            data-analytics-id="nxrm-global-header-help-support"
        >
          Support
        </NxTextLink>
      </NxStatefulNavigationDropdown>);
}
