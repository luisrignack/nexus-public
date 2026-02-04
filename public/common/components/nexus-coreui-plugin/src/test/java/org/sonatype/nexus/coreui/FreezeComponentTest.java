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
package org.sonatype.nexus.coreui;

import javax.validation.Validator;

import org.sonatype.goodies.testsupport.Test5Support;
import org.sonatype.nexus.bootstrap.validation.ValidationConfiguration;
import org.sonatype.nexus.common.app.FreezeService;
import org.sonatype.nexus.testcommon.extensions.AuthenticationExtension;
import org.sonatype.nexus.testcommon.extensions.AuthenticationExtension.WithUser;
import org.sonatype.nexus.testcommon.validation.ValidationExtension;
import org.sonatype.nexus.testcommon.validation.ValidationExtension.ValidationExecutor;

import org.hibernate.validator.internal.engine.constraintvalidation.ConstraintValidatorFactoryImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(ValidationExtension.class)
@ExtendWith(AuthenticationExtension.class)
@WithUser
class FreezeComponentTest
    extends Test5Support
{
  @ValidationExecutor
  private final Validator validator =
      new ValidationConfiguration().validatorFactory(new ConstraintValidatorFactoryImpl()).getValidator();

  @Mock
  FreezeService freezeService;

  @InjectMocks
  FreezeComponent underTest;

  @Test
  void read() throws Exception {
    when(freezeService.isFrozen()).thenReturn(true);
    FreezeStatusXO freezeStatusXO = underTest.read();
    assertThat(freezeStatusXO.isFrozen(), is(true));
  }

  @Test
  void testUpdateRelease() throws Exception {
    FreezeStatusXO freezeStatusXO = new FreezeStatusXO();
    freezeStatusXO.setFrozen(false);

    underTest.update(freezeStatusXO);

    verify(freezeService).cancelFreeze();
  }

  @Test
  void testUpdateFreeze() throws Exception {
    FreezeStatusXO freezeStatusXO = new FreezeStatusXO();
    freezeStatusXO.setFrozen(true);

    underTest.update(freezeStatusXO);

    verify(freezeService).requestFreeze(isA(String.class));
  }
}
