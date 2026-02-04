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
import { assign } from 'xstate';
import ExtAPIUtils from '../../../interface/ExtAPIUtils';
import ListMachineUtils from '../../../interface/ListMachineUtils';
import APIConstants from '../../../constants/APIConstants';

const { ACTION, METHODS } = APIConstants.EXT.CAPABILITY;

export default ListMachineUtils.buildListMachine({
  id: 'CapabilitiesListMachine',
  sortField: 'typeName',
  sortDirection: 'asc',
  sortableFields: ['typeName', 'state', 'category', 'repository', 'description', 'notes'],
}).withConfig({
  actions: {
    setData: assign({
      data: (_, { data }) => data.map(item => ({
        ...item,
        category: item.tags?.Category || '',
        repository: item.tags?.Repository || ''
      })),
      pristineData: (_, { data }) => data.map(item => ({
        ...item,
        category: item.tags?.Category || '',
        repository: item.tags?.Repository || ''
      })),
    }),
    filterData: assign({
      data: ({ filter, pristineData }) => {
        if (!filter) {
          return pristineData;
        }
        const lowerFilter = filter.toLowerCase();
        return pristineData.filter(capability =>
          ListMachineUtils.hasAnyMatches([
            capability.typeName,
            capability.description,
            capability.notes,
            capability.tags?.Category,
            capability.tags?.Repository,
            capability.state
          ], lowerFilter)
        );
      }
    }),
  },
  services: {
    fetchData: () => ExtAPIUtils.extAPIRequest(ACTION, METHODS.READ).then(ExtAPIUtils.checkForErrorAndExtract),
  },
  on: {
    RETRY: {
      target: 'loading',
    },
  },
});
