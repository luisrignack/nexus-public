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

/*global Ext, NX*/

/*global NX*/

/**
 * 'panelMessage' factory.
 */
Ext.define('NX.coreui.view.formfield.factory.FormfieldReconcilePlanInformationFactory', {
  singleton: true,
  alias: ['nx.formfield.factory.reconcile.planInformation'],
  requires: [
      'NX.util.DateFormat',
      'NX.I18n'
  ],

  create: function(formField) {
    const item = {
      xtype: 'container',
      itemId: "reconcilePlanInformation",
      layout: {
        type: 'vbox',
        align: 'stretch'
      },
      margin: '5 0 10 0',
      itemCls: "x-field",
      items: [
        {
          xtype: 'container',
          region: 'left',
          layout: {
            type: 'hbox',
            align: 'left',
            pack: 'left'
          },
          items: [
            {
              xtype: 'label',
              text: NX.I18n.get('Task_TaskSettingsForm_PlanInformation_Plans_Qty'),
              width: 100
            },
            {
              xtype: 'label',
              itemId: 'labelPlansQty',
              width: 'auto',
            }
          ]
        },
        {
          xtype: 'container',
          region: 'left',
          layout: {
            type: 'hbox',
            align: 'left',
            pack: 'left'
          },
          items: [
            {
              xtype: 'label',
              text: NX.I18n.get('Task_TaskSettingsForm_PlanInformation_BlobStore_Qty'),
              width: 100,
            },
            {
              xtype: 'label',
              itemId: 'labelBlobStoresQty',
              style: {
                width: 'auto',
                left: '100px'
              }
            }
          ]
        },
        {
          xtype: 'container',
          region: 'left',
          layout: {
            type: 'hbox',
            align: 'left',
            pack: 'left'
          },
          items: [
            {
              xtype: 'label',
              text: NX.I18n.get('Task_TaskSettingsForm_PlanInformation_Repositories_Qty'),
              width: 100,
            },
            {
              xtype: 'label',
              itemId: 'labelRepositoriesQty',
              style: {
                width: 'auto',
                left: '100px'
              }
            }
          ]
        },
        {
          xtype: 'container',
          region: 'left',
          layout: {
            type: 'hbox',
            align: 'left',
            pack: 'left'
          },
          items: [
            {
              xtype: 'label',
              text: NX.I18n.get('Task_TaskSettingsForm_PlanInformation_StartDate'),
              width: 100,
            },
            {
              xtype: 'label',
              itemId: 'labelStartDate',
              style: {
                width: 'auto',
                left: '100px'
              }
            }
          ]
        },
        {
          xtype: 'container',
          region: 'left',
          layout: {
            type: 'hbox',
            align: 'left',
            pack: 'left'
          },
          items: [
            {
              xtype: 'label',
              text: NX.I18n.get('Task_TaskSettingsForm_PlanInformation_EndDate'),
              width: 100,
            },
            {
              xtype: 'label',
              itemId: 'labelEndDate',
              style: {
                width: 'auto',
                left: '100px'
              }
            }
          ]
        }
      ],
      validate: function() {
      },
      setValues: function(values) {
        const me = this;
        const asFormattedDateTime = function(date, defaultValue) {
          if(date) {
            const format = NX.util.DateFormat.forName('datetime')['longVariant1'];
            return NX.util.DateFormat.timestamp(date, format);
          } else return defaultValue;
        }

        me.down('#labelPlansQty').setText(values.planCount || NX.I18n.get('Task_TaskSettingsForm_PlanInformation_Plans_Empty'));
        me.down('#labelBlobStoresQty').setText(values.blobStoreCount || NX.I18n.get('Task_TaskSettingsForm_PlanInformation_BlobStore_Empty'));
        me.down('#labelRepositoriesQty').setText(values.repositoryCount || NX.I18n.get('Task_TaskSettingsForm_PlanInformation_Repositories_Empty'));
        me.down('#labelStartDate').setText(asFormattedDateTime(values.startDate, NX.I18n.get('Task_TaskSettingsForm_PlanInformation_StartDate_Empty')));
        me.down('#labelEndDate').setText(asFormattedDateTime(values.endDate, NX.I18n.get('Task_TaskSettingsForm_PlanInformation_EndDate_Empty')));
      }
    };

    return item;
  }
});
