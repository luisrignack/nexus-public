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
import React from 'react';
import {render, screen, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import {ExtJS} from '@sonatype/nexus-ui-plugin';

import UIStrings from '../../../../constants/UIStrings';
import SslCertificatesAlreadyExistsModal from './SslCertificatesAlreadyExistsModal';

const {
  SSL_CERTIFICATES: {
    ADD_FORM: {
      MODAL: {HEADER, CONTENT, VIEW_BUTTON}
    }
  },
  SETTINGS: {CANCEL_BUTTON_LABEL}
} = UIStrings;

const stateServiceGoMock = jest.fn();

jest.mock('@sonatype/nexus-ui-plugin', () => ({
  ...jest.requireActual('@sonatype/nexus-ui-plugin'),
  ExtJS: {
    setDirtyStatus: jest.fn(),
    state: jest.fn().mockReturnValue({
      getValue: jest.fn()
    })
  },
}));

jest.mock('@uirouter/react', () => ({
  ...jest.requireActual('@uirouter/react'),
  useRouter: () => ({
    stateService: {
      go: stateServiceGoMock,
    }
  })
}));

const selectors = {
  modal: () => screen.getByRole('dialog'),
  header: () => within(selectors.modal()).getByText(HEADER),
  content: () => within(selectors.modal()).getByText(CONTENT),
  cancelButton: () => within(selectors.modal()).getByText(CANCEL_BUTTON_LABEL),
  viewButton: () => within(selectors.modal()).getByText(VIEW_BUTTON),
};

describe('SslCertificatesAlreadyExistsModal', function () {
  const certificateId = 'test-cert-id';
  const mockCancel = jest.fn();

  const renderModal = () => {
    return render(
      <SslCertificatesAlreadyExistsModal
        certificateId={certificateId}
        cancel={mockCancel}
      />
    );
  };

  it('render data', async function () {
    renderModal();

    expect(selectors.modal()).toBeVisible();
    expect(selectors.header()).toBeInTheDocument();
    expect(selectors.content()).toBeInTheDocument();
  });

  it('click on view button', async function () {
    renderModal();
    userEvent.click(selectors.viewButton());

    expect(ExtJS.setDirtyStatus).toHaveBeenCalledWith('SslCertificatesAddFormMachine', false);
    expect(stateServiceGoMock).toHaveBeenCalledWith(
      'admin.security.sslcertificates.edit',
      {itemId: encodeURIComponent(certificateId)}
    );
  });

  it('click on cancel button', async function () {
    renderModal();
    userEvent.click(selectors.cancelButton());

    expect(ExtJS.setDirtyStatus).toHaveBeenCalledWith('SslCertificatesAddFormMachine', false);
    expect(mockCancel).toHaveBeenCalled();
  });
});
