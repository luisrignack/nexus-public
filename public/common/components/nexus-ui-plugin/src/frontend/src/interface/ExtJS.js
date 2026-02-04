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
import {useEffect, useState} from 'react';

/**
 * @since 3.22
 */
export default class ExtJS {
  /**
   * Open a success message notification
   * @param text
   */
  static showSuccessMessage(text) {
    NX.Messages.success(text)
  }

  /**
   * Open an error message notification
   * @param text
   */
  static showErrorMessage(text) {
    NX.Messages.error(text);
  }

  /**
   *@returns a complete url for the current nexus instance
   */
  static urlOf(path) {
    return NX.util.Url.urlOf(path);
  }

  /**
   * @returns an absolute path when given a relative path.
   */
  static absolutePath(path) {
    return NX.util.Url.absolutePath(path); 
  }

  /**
   *@returns a complete url for the PRO-LICENSE.html
   */
  static proLicenseUrl() {
    return ExtJS.urlOf('/PRO-LICENSE.html');
  }

  /**
   * Set the global dirty status to prevent accidental navigation
   * @param key - a unique key for the view that is dirty
   * @param isDirty - whether the view is dirty or not
   */
  static setDirtyStatus(key, isDirty) {
    window.dirty = window.dirty || [];

    if (isDirty && window.dirty.indexOf(key) === -1) {
      window.dirty.push(key);
    }
    else if (!isDirty) {
      window.dirty = window.dirty.filter(it => it !== key);
    }
  }

  /**
   * @return {location: {pathname: string }}
   */
  static useHistory({basePath}) {
    const [path, setPath] = useState(Ext.History.getToken());

    useEffect(() => {
      // When the unmount is occurring due to a route change, Ext seems to already commit
      // to firing the change handler even though the useEffect cleanup function fires before it does.
      // This causes React memory leak warnings if the state mutator gets called at that point. So we need this
      // extra variable to check whether the unmount has in fact occurred
      let unmounted = false;

      function _setPath(p) {
        if (!unmounted) {
          setPath(p);
        }
      }

      Ext.History.on('change', _setPath);
      return () => {
        unmounted = true;
        Ext.History.un('change', _setPath);
      };
    }, []);

    return {
      location: {
        pathname: path.replace(basePath, '')
      }
    };
  }

  static requestConfirmation({title, message, yesButtonText = 'Yes', noButtonText = 'No'}) {
    const options = {
      buttonText: {
        yes: yesButtonText,
        no: noButtonText
      }
    };

    return new Promise((resolve, reject) => NX.Dialogs.askConfirmation(title, message, resolve, {
      ...options,
      onNoFn: reject
    }));
  }

  /**
   * Create a Promise that will fetch an authentication token using the
   * username and password supplied
   * @param username
   * @param password
   * @returns {Promise}
   */
  static fetchAuthenticationToken(username, password) {
    const b64u = NX.util.Base64.encode(username);
    const b64p = NX.util.Base64.encode(password);
    return new Promise((resolve) => {
      NX.direct.rapture_Security.authenticationToken(b64u, b64p, resolve);
    });
  }

  /**
   * Request a session for supplied credentials, reusing the ExtJS security flow
   * @param {string} username
   * @param {string} password
   * @returns {Promise}
   */
  static requestSession(username, password) {
    const b64u = NX.util.Base64.encode(username);
    const b64p = NX.util.Base64.encode(password);

    return new Promise((resolve, reject) => {
      NX.Security.requestSession(b64u, b64p).then(
        (response) => {
          const normalizedResponse = ExtJS.normalizeAjaxResponse(response);

          // Set user state on successful authentication
          if (normalizedResponse.status >= 200 && normalizedResponse.status < 300) {
            NX.State.setUser({id: username});
          }

          resolve({response: normalizedResponse});
        },
        (error) => reject({response: ExtJS.normalizeAjaxResponse(error)})
      );
    });
  }

  static normalizeAjaxResponse(response) {
    if (response?.response) {
      response = response.response;
    }

    if (!response) {
      return {status: undefined, data: undefined};
    }

    let data;

    if (response.responseJSON !== undefined) {
      data = response.responseJSON;
    }
    else if (response.responseText !== undefined) {
      const text = response.responseText;
      if (text) {
        try {
          data = JSON.parse(text);
        }
        catch {
          data = text;
        }
      }
    }
    else if (response.data !== undefined) {
      data = response.data;
    }

    const normalized = {
      status: response.status
    };

    if (data !== undefined) {
      normalized.data = data;
    }

    return normalized;
  }

  /**
   * Prompt the user to re-authenticate to fetch an authentication token
   * @param message prompt shown to user
   * @returns {Promise}
   */
  static requestAuthenticationToken(message) {
    return new Promise((resolve, reject) => {
      NX.Security.doWithAuthenticationToken(
          message,
          {
            success: function(authToken) {
              return resolve(authToken);
            },
            failure: function() {
              return reject();
            }
          }
      );
    });
  }

  /**
   * @deprecated - Use a link with the download attribute instead
   */
  static downloadUrl(url) {
    NX.util.DownloadHelper.downloadUrl(url);
  }

  static state() {
    return NX.State;
  }

  static formatDate(date, format) {
    return Ext.Date.format(date, format);
  }

  /**
   * @returns {boolean} true if the edition is PRO
   */
  static isProEdition() {
    return ExtJS.state().getEdition() === 'PRO';
  }

  /**
   * @param permission {string}
   * @returns {boolean} true if the user has the requested permission
   */
  static checkPermission(permission) {
    return NX.Permissions.check(permission)
  }

  /**
   * @returns {{id: string, authenticated: boolean, administrator: boolean, authenticatedRealms: string[]} | undefined}
   */
  static useUser() {
    return ExtJS.useState(() => NX.State.getUser());
  }

  /**
   * @returns {{version: string, edition: string}}
   */
  static useStatus() {
    return ExtJS.useState(() => NX.State.getValue('status'));
  }

  /**
   * @returns {{daysToExpiry: number}}
   */
  static useLicense() {
    return ExtJS.useState(() => NX.State.getValue('license'));
  }

  // don't use directly, use useIsVisible, where possible
  static useVisiblityWithChanges(isVisibleMethod) {
    const [isVisible, setIsVisible] = useState(isVisibleMethod());

    useEffect(() => {
      function handleChange() {
        const newValue = isVisibleMethod();
        if (isVisible !== newValue) {
          setIsVisible(newValue);
        }
      }

      const permissionsController = Ext.getApplication().getController('Permissions');
      permissionsController.on('changed', handleChange);

      const stateController = Ext.getApplication().getController('State');
      stateController.on('changed', handleChange);

      return () => {
        // cleanup code
        permissionsController.un('changed', handleChange);
        stateController.un('changed', handleChange);
      }
    }, [isVisible]);

    return isVisible;
  }

  /**
   * A hook that automatically re-evaluates whenever any state is changed
   * @param getValue - A function to get the value from the state subsystem
   * @returns {unknown}
   */
  static useState(getValue) {
    const [value, setValue] = useState(getValue());

    useEffect(() => {
      function handleChange() {
        const newValue = getValue();
        if (value !== newValue) {
          setValue(newValue);
        }
      }

      const state = Ext.getApplication().getStore('State');
      state.on('datachanged', handleChange);
      return () => state.un('datachanged', handleChange);
    }, [value]);

    return value;
  }

  /**
   * A hook to add search criteria to the search subsystem
   * @param {*} criterias 
   */
  static useCriteria(criterias) {
    const state = Ext.getApplication().getStore('SearchCriteria');
    const models = state.add(criterias);

    useEffect(() => {
      return () => {
        state.remove(models);
      }
    }, []);
  }

  /**
   * A hook to use a search filter model
   * @param {*} filter 
   * @returns 
   */
  static useSearchFilterModel(filter) {
    return Ext.getApplication().getModel("NX.coreui.model.SearchFilter").create(filter);
  }

  /**
   * A hook that automatically re-evaluates whenever any state is changed
   * @param getValue - A function to get the value from the state subsystem
   * @returns {unknown}
   */
    static usePermission(getValue, dependencies) {
      const [value, setValue] = useState(getValue());
  
      useEffect(() => {
        function handleChange() {
          const newValue = getValue();
          if (value !== newValue) {
            setValue(newValue);
          }
        }

      const permissionsController = Ext.getApplication().getController('Permissions');
      permissionsController.on('changed', handleChange);

      const stateController = Ext.getApplication().getController('State');
      stateController.on('userchanged', handleChange);
      return () => {
        permissionsController.un('changed', handleChange);
        stateController.un('userchanged', handleChange);
        }
      }, [value, ...(dependencies ?? [])]);
  
      return value;
    }

  static hasUser() {
    return NX.Security.hasUser();
  }

  static signOut() {
    NX.Security.signOut();
  }

  static showAbout() {
    Ext.widget('nx-aboutwindow')
  }

  static refresh() {
    Ext.getApplication().getController('Refresh').refresh();
  }

  static search(query) {
    Ext.getApplication().getController('Search').onQuickSearch(null, query);
  }

  static isExtJsRendered() {
    return document.getElementsByClassName('nxrm-ext-js-wrapper').length > 0;
  }

  /**
   * This function will wait for the ExtJS application to be fully loaded before executing the callback
   * @param callback
   */
  static waitForExtJs(callback) {
    const interval = setInterval(() => {
      if (Ext.getApplication() && NX.Permissions.permissions !== undefined) {
        clearInterval(interval);
        callback();
      }
    }, 1);
  }

  static calculateTimeout() {
    const uiSettings = ExtJS.state().getValue('uiSettings', {});
    const statusInterval = ExtJS.hasUser()
      ? (uiSettings.statusIntervalAuthenticated || 5)
      : (uiSettings.statusIntervalAnonymous || 60);

    return Math.max(statusInterval * 2 * 1000, 2000);
  }

  /**
   * Wait for the next permission change event from ExtJS.
   * Useful after login to ensure permissions are updated before navigation.
   * 
   * The timeout is calculated based on the UI settings status interval to account for network latency
   * and server load. Uses 2x the status interval as a buffer, with a minimum of 2 seconds.
   * 
   * @returns {Promise<void>} Resolves when permissions change, rejects after timeout
   */
  static waitForNextPermissionChange() {
    return new Promise((resolve, reject) => {
      const handleChange = () => {
        console.debug('received permission changes');
        clearTimeout(timeout);
        resolve();
      };

      console.debug('setting up event handler to wait for permission changes');
      const permissionsController = Ext.getApplication().getController('Permissions');
      permissionsController.on("changed", handleChange);

      const timeoutMs = ExtJS.calculateTimeout();

      const timeout = setTimeout(() => {
        console.debug('removing event handler, permission changes have timed out');
        permissionsController.un('changed', handleChange);
        reject(new Error('timed out waiting for permissions to update'));
      }, timeoutMs);
    });
  }
}
