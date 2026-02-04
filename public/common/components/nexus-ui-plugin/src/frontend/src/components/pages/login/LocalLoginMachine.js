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
import LoginPageStrings from '../../../constants/LoginPageStrings';
import FormUtils from '../../../interface/FormUtils';
import ValidationUtils from '../../../interface/ValidationUtils';
import ExtJS from '../../../interface/ExtJS';

const BLANK_FIELD_ERROR = ' ';

const localLoginMachine = FormUtils.buildFormMachine({
  id: 'LoginFormMachine',
  initial: 'loaded',
  stateAfterSave: 'loaded',
  config: (config) => {
    const loadedState = config.states.loaded || {};
    const loadedOn = loadedState.on || {};

    return {
      ...config,
      context: {
        ...config.context,
        data: { username: '', password: '' },
        pristineData: { username: '', password: '' },
        skipValidation: false
      },
      states: {
        ...config.states,
        loaded: {
          ...loadedState,
          on: {
            ...loadedOn,
            CLEAR_SAVE_ERROR: {
              actions: ['clearSaveError'],
              internal: true
            }
          }
        }
      }
    };
  }
}).withConfig({
  actions: {
    validate: assign({
      validationErrors: ({ data, skipValidation }) => {
        // Don't validate if we just had an auth error
        if (skipValidation) {
          return {};
        }
        return {
          username: ValidationUtils.isBlank(data?.username) ? LoginPageStrings.ERRORS.USERNAME_REQUIRED : undefined,
          password: ValidationUtils.isBlank(data?.password) ? LoginPageStrings.ERRORS.PASSWORD_REQUIRED : undefined
        };
      },
      skipValidation: () => false  
    }),

    setSaveError: assign({
      saveErrorData: ({ data }) => ({ ...(data ?? {}) }),
      saveError: (_, event) => {
        const error = event.data;

        if (error.response?.status === 403) {
          return LoginPageStrings.ERRORS.WRONG_CREDENTIALS;
        }
        return error.response?.status === 0
          ? LoginPageStrings.ERRORS.CONNECTION_FAILED
          : error.response?.data?.message || LoginPageStrings.ERRORS.AUTHENTICATION_FAILED;
      },
      saveErrors: (_, event) => {
        if (event.data?.response?.status === 403) {
          // Use empty string to show red border styling without error message text
          return {
            username: BLANK_FIELD_ERROR,
            password: BLANK_FIELD_ERROR
          };
        }
        return {};
      },
      data: ({ data }, event) => {
        if (event.data?.response?.status === 403) {
          return {
            ...data,
            password: ''
          };
        }
        return data;
      },
      validationErrors: ({ validationErrors }, event) => {
        if (event.data?.response?.status === 403) {
          return {};
        }
        return validationErrors;
      },
      skipValidation: (_, event) => event.data?.response?.status === 403
    }),

    clearSaveError: assign({
      saveErrorData: () => ({}),
      saveError: () => undefined,
      saveErrors: () => ({}),
      skipValidation: () => true
    }),

    logSaveError: (_, event) => {
      const error = event.data;
      let errorMessage;

      if (error.response?.status === 403) {
        errorMessage = LoginPageStrings.ERRORS.WRONG_CREDENTIALS;
      } else {
        errorMessage =
          error.response?.status === 0
            ? LoginPageStrings.ERRORS.CONNECTION_FAILED
            : error.response?.data?.message || LoginPageStrings.ERRORS.AUTHENTICATION_FAILED;
      }

      console.log(`Authentication Error: ${errorMessage}`);
    },

    logSaveSuccess: (_, event) => {
      const payload = event?.data?.response ?? event?.data;
      const serializedResponse = payload ? JSON.stringify(payload) : 'undefined';
      console.log(`Authentication Success: ${serializedResponse}`);
      // Suppress default success toast for login flow
    },

    setDirtyFlag: () => {
      // No-op: LocalLogin does not track dirty state
    },

    clearDirtyFlag: () => {
      // No-op: LocalLogin does not track dirty state
    }
  },

  services: {
    saveData: ({ data }) =>
      ExtJS.requestSession(data.username, data.password).then((result) => ({
        response: result.response,
        username: data.username
      }))
  }
});

export default localLoginMachine;
