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

import SearchFeatureExt from './SearchFeatureExt';
import UIStrings from "../../../../constants/UIStrings";

export default function SearchMavenExt() {
  const CRITERIA = UIStrings.SEARCH.MAVEN.CRITERIA;

  return (
    <SearchFeatureExt
      title={UIStrings.SEARCH.MAVEN.MENU.text}
      icon={UIStrings.SEARCH.MAVEN.MENU.icon}
      criterias={[
        {
          id: 'attributes.maven2.groupId',
          group: CRITERIA.GROUP,
          config: {
            format: 'maven2',
            fieldLabel: CRITERIA.FIELD_LABEL.GROUP_ID,
            width: 250
          }
        },
        {
          id: 'attributes.maven2.artifactId',
          group: CRITERIA.GROUP,
          config: {
            format: 'maven2',
            fieldLabel: CRITERIA.FIELD_LABEL.ARTIFACT_ID,
            width: 250
          }
        },
        {
          id: 'attributes.maven2.baseVersion',
          group: CRITERIA.GROUP,
          config: {
            format: 'maven2',
            fieldLabel: CRITERIA.FIELD_LABEL.BASE_VERSION,
            width: 250
          }
        },
        {
          id: 'assets.attributes.maven2.classifier',
          group: CRITERIA.GROUP,
          config: {
            format: 'maven2',
            fieldLabel: CRITERIA.FIELD_LABEL.CLASSIFIER,
          }
        },
        {
          id: 'assets.attributes.maven2.extension',
          group: CRITERIA.GROUP,
          config: {
            format: 'maven2',
            fieldLabel: CRITERIA.FIELD_LABEL.EXTENSION,
          }
        }
      ]}
      filter={{
        id: "maven2",
        name: "Maven",
        text: UIStrings.SEARCH.MAVEN.MENU.text,
        description: UIStrings.SEARCH.MAVEN.MENU.description,
        readOnly: true,
        criterias: [
          { id: "format", value: "maven2", hidden: true },
          { id: "attributes.maven2.groupId" },
          { id: "attributes.maven2.artifactId" },
          { id: "version" },
          { id: "attributes.maven2.baseVersion" },
          { id: "assets.attributes.maven2.classifier" },
          { id: "assets.attributes.maven2.extension" },
        ],
      }}
    />
  );
}
