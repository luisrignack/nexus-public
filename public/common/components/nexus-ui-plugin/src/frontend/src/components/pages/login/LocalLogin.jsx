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

import { faExclamationCircle } from '@fortawesome/free-solid-svg-icons';
import {
  NxButton,
  NxFieldset,
  NxFontAwesomeIcon,
  NxFormGroup,
  NxLoadingSpinner,
  NxStatefulForm,
  NxTextInput
} from '@sonatype/react-shared-components';
import { useMachine } from '@xstate/react';
import { useRouter } from '@uirouter/react';
import PropTypes from 'prop-types';
import React, { useEffect } from 'react';
import { ExtJS } from '@sonatype/nexus-ui-plugin';
import LoginPageStrings from '../../../constants/LoginPageStrings';
import { RouteNames } from '../../../constants/RouteNames';
import FormUtils from '../../../interface/FormUtils';
import LoginFormMachine from './LocalLoginMachine';

const { LOGIN_BUTTON_LOADING, USERNAME_LABEL, PASSWORD_LABEL, LOGIN_BUTTON } = LoginPageStrings;

/**
 * Local authentication form component that handles username/password login.
 *
 * @param {Object} props - Component props
 * @param {boolean} props.primaryButton - REQUIRED: If true, login button uses primary variant and username field will be focused on mount
 * @param {Function} props.onError - Optional callback to report server errors to parent component
 */
export default function LocalLogin({ primaryButton, onError }) {
  const router = useRouter();

  const handleLoginSuccess = async ({ username }) => {
    onError?.(null);
    console.log(`User ${username} authenticated successfully`);
    try {
      await ExtJS.waitForNextPermissionChange();
      const returnTo = router.globals.params.returnTo;
      if (returnTo) {
        const originalReturnTo = atob(returnTo);
        // `router.urlService.url` does set and navigate to the returnTo url
        router.urlService.url(originalReturnTo);
      } else {
        router.stateService.go('browse.welcome');
      }
    } catch (ex) {
      console.warn('redirection unsuccessful: ', ex);
      router.stateService.go(RouteNames.MISSING_ROUTE);
    }
  };

  const [current, send] = useMachine(LoginFormMachine, {
    actions: {
      onSaveSuccess: async (context, event) => {
        const loginData = {
          username: event.data?.username ?? context.data.username,
          response: event.data?.response ?? event.data
        };
        await handleLoginSuccess(loginData);
      },
      logSaveError: (_, event) => {
        console.error('Login failed:', event.data);
        const error = event.data;
        const status = error.response?.status;
        if (onError && status !== 403) {
          const errorMessage = status === 0
            ? LoginPageStrings.ERRORS.CONNECTION_FAILED
            : error.response?.data?.message || LoginPageStrings.ERRORS.AUTHENTICATION_FAILED;
          onError(errorMessage);
        }
      }
    }
  });

  const formProps = FormUtils.formProps(current, send);

  const { saveError: serverError, saveErrorData, saveErrors = {} } = current.context;
  const isLoading = current.matches('saving');
  const hasAuthError = Boolean(saveErrors.username && saveErrors.password);
  const authErrorOverride = hasAuthError ? { validationErrors: ' ' } : {};

  const shouldShowInlineError = serverError && hasAuthError;

  const handleFieldChange = (fieldName) => (value) => {
    if (serverError) {
      send({ type: 'CLEAR_SAVE_ERROR' });
    }
    FormUtils.handleUpdate(fieldName, send)(value);
  };

  const handleSubmit = () => {
    formProps.onSubmit();
  };

  useEffect(() => {
    if (!serverError) {
      onError?.(null);
    }
    if (serverError && saveErrorData?.username) {
      const inputElement = document.getElementById('username');
      if (inputElement) {
        inputElement.focus();
      }
    }
  }, [serverError, saveErrorData?.username, onError]);

  const statefulFormProps = {
    ...formProps,
    className: formProps.className ? `${formProps.className} login-form` : 'login-form',
    submitBtnText: LOGIN_BUTTON,
    submitBtnClasses: [formProps.submitBtnClasses, 'login-form__hidden-submit'].filter(Boolean).join(' '),
    submitError: null,
    submitMaskState: null,
    onSubmit: handleSubmit
  };

  return (
    <NxStatefulForm {...statefulFormProps}>
      <NxFieldset>
        <NxFormGroup label={USERNAME_LABEL} className="username-field" isRequired>
          <NxTextInput
            {...FormUtils.fieldProps('username', current)}
            {...authErrorOverride}
            onChange={handleFieldChange('username')}
            placeholder={USERNAME_LABEL}
            disabled={isLoading}
            inputAttributes={{
              id: 'username',
              'data-analytics-id': 'login-username-input',
              'aria-required': 'true',
              autoFocus: primaryButton
            }}
          />
        </NxFormGroup>

        <NxFormGroup label={PASSWORD_LABEL} isRequired>
          <NxTextInput type="password"
            {...FormUtils.fieldProps('password', current)}
            {...authErrorOverride}
            onChange={handleFieldChange('password')}
            placeholder={PASSWORD_LABEL}
            disabled={isLoading}
            inputAttributes={{
              id: 'password',
              'data-analytics-id': 'login-password-input',
              'aria-required': 'true'
            }}
          />
        </NxFormGroup>

        <div className="server-error-container">
          {shouldShowInlineError && (
            <div className="server-error-message">
              <NxFontAwesomeIcon icon={faExclamationCircle} />
              {serverError}
            </div>
          )}
        </div>

        <NxButton
          type="submit"
          variant={primaryButton ? 'primary' : null}
          className="login-button"
          disabled={isLoading}
          data-analytics-id="nxrm-login-local"
          data-testid="login-primary-button"
        >
          {isLoading ? <NxLoadingSpinner>{LOGIN_BUTTON_LOADING}</NxLoadingSpinner> : LOGIN_BUTTON}
        </NxButton>
      </NxFieldset>
    </NxStatefulForm>
  );
}

LocalLogin.propTypes = {
  primaryButton: PropTypes.bool.isRequired,
  onError: PropTypes.func
};
