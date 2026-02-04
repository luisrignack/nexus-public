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
import PropTypes from "prop-types";

import { ExtJsContainer } from "../../../widgets/ExtJsContainer/ExtJsContainer";
import { ExtJS } from "@sonatype/nexus-ui-plugin";
export default function SearchFeatureExt({
  title,
  icon,
  criterias,
  filter,
  showsMaliciousRiskBanner
}) {
  const keywordFilter = ExtJS.useSearchFilterModel(filter);
  const extParams = { searchFilter: keywordFilter, bookmarkEnding: "" };

  if (criterias) {
    ExtJS.useCriteria(criterias);
  }

  return (
    <ExtJsContainer
      title={title}
      icon={icon}
      className="nxrm-search"
      extView="NX.coreui.view.search.SearchFeature"
      extParams={extParams}
      showsMaliciousRiskBanner={showsMaliciousRiskBanner}
    />
  );
}

SearchFeatureExt.defaultProps = {
  filter: {
    id: "keyword",
    name: "Keyword",
    text: "Keyword",
    description: "Search for components by keyword",
    readOnly: true,
    criterias: [{ id: "keyword" }],
  },
};

SearchFeatureExt.propTypes = {
  criterias: PropTypes.arrayOf(
    PropTypes.shape({
      id: PropTypes.string,
      group: PropTypes.string,
      config: PropTypes.shape({
        format: PropTypes.string,
        fieldLabel: PropTypes.string,
        width: PropTypes.number,
      }),
    })
  ),
  filter: PropTypes.shape({
    id: PropTypes.string,
    name: PropTypes.string,
    text: PropTypes.string,
    description: PropTypes.string,
    readOnly: PropTypes.bool,
    criterias: PropTypes.arrayOf(
      PropTypes.shape({
        id: PropTypes.string,
        value: PropTypes.string,
        hidden: PropTypes.bool,
      })
    ),
  }).isRequired,
  showsMaliciousRiskBanner: PropTypes.bool,
};

SearchFeatureExt.propTypes = {
  title: PropTypes.string.isRequired,
  icon: PropTypes.object.isRequired,
  criterias: PropTypes.arrayOf(PropTypes.object),
  filter: PropTypes.object,
  showsMaliciousRiskBanner: PropTypes.bool
};
