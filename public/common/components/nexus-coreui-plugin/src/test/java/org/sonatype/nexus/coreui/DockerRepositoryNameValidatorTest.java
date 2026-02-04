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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;

import org.sonatype.goodies.testsupport.TestSupport;

import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/**
 * Tests for Docker repository name validation in RepositoryXO
 */
public class DockerRepositoryNameValidatorTest
    extends TestSupport
{
  private Validator validator;

  @Before
  public void setUp() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  @Test
  public void testDockerHosted_lowercaseName_isValid() {
    RepositoryXO xo = createRepository("my-docker-repo", "docker-hosted", "docker");
    Set<ConstraintViolation<RepositoryXO>> violations = validator.validate(xo);
    assertThat("Lowercase Docker repository name should be valid", violations, is(empty()));
  }

  @Test
  public void testDockerHosted_uppercaseName_isInvalid() {
    RepositoryXO xo = createRepository("MY-DOCKER-REPO", "docker-hosted", "docker");
    Set<ConstraintViolation<RepositoryXO>> violations = validator.validate(xo);
    assertThat("Uppercase Docker repository name should be invalid", violations, is(not(empty())));
  }

  @Test
  public void testDockerHosted_mixedCaseName_isInvalid() {
    RepositoryXO xo = createRepository("MyDockerRepo", "docker-hosted", "docker");
    Set<ConstraintViolation<RepositoryXO>> violations = validator.validate(xo);
    assertThat("Mixed case Docker repository name should be invalid", violations, is(not(empty())));

    boolean hasDockerValidation = violations.stream()
        .anyMatch(v -> v.getMessage().contains("Docker repository names must be lowercase"));
    assertThat("Should have Docker lowercase validation error", hasDockerValidation, is(true));
  }

  @Test
  public void testDockerProxy_mixedCaseName_isInvalid() {
    RepositoryXO xo = createRepository("MyDockerProxy", "docker-proxy", "docker");
    Set<ConstraintViolation<RepositoryXO>> violations = validator.validate(xo);
    boolean hasDockerValidation = violations.stream()
        .anyMatch(v -> v.getMessage().contains("Docker repository names must be lowercase"));
    assertThat("Mixed case Docker proxy repository name should have Docker validation error", hasDockerValidation,
        is(true));
  }

  @Test
  public void testDockerGroup_mixedCaseName_isInvalid() {
    RepositoryXO xo = createRepository("MyDockerGroup", "docker-group", "docker");
    Set<ConstraintViolation<RepositoryXO>> violations = validator.validate(xo);
    boolean hasDockerValidation = violations.stream()
        .anyMatch(v -> v.getMessage().contains("Docker repository names must be lowercase"));
    assertThat("Mixed case Docker group repository name should have Docker validation error", hasDockerValidation,
        is(true));
  }

  @Test
  public void testNonDockerRepository_mixedCaseName_isValid() {
    RepositoryXO xo = createRepository("MyMavenRepo", "maven2-hosted", "maven2");
    Set<ConstraintViolation<RepositoryXO>> violations = validator.validate(xo);
    assertThat("Mixed case non-Docker repository name should be valid (validation only for Docker)", violations,
        is(empty()));
  }

  @Test
  public void testDockerRepository_withFormatOnly_mixedCaseName_isInvalid() {
    RepositoryXO xo = createRepository("MyDockerRepo", null, "docker");
    Set<ConstraintViolation<RepositoryXO>> violations = validator.validate(xo);
    boolean hasDockerValidation = violations.stream()
        .anyMatch(v -> v.getMessage().contains("Docker repository names must be lowercase"));
    assertThat("Mixed case Docker repository (format=docker, no recipe) should have Docker validation error",
        hasDockerValidation, is(true));
  }

  @Test
  public void testDockerRepository_withRecipeOnly_mixedCaseName_isInvalid() {
    RepositoryXO xo = createRepository("MyDockerRepo", "docker-hosted", null);
    Set<ConstraintViolation<RepositoryXO>> violations = validator.validate(xo);
    boolean hasDockerValidation = violations.stream()
        .anyMatch(v -> v.getMessage().contains("Docker repository names must be lowercase"));
    assertThat("Mixed case Docker repository (recipe=docker-hosted, no format) should have Docker validation error",
        hasDockerValidation, is(true));
  }

  @Test
  public void testDockerRepository_withNumbersAndHyphens_lowercase_isValid() {
    RepositoryXO xo = createRepository("my-docker-repo-123", "docker-hosted", "docker");
    Set<ConstraintViolation<RepositoryXO>> violations = validator.validate(xo);
    assertThat("Lowercase Docker repository name with numbers and hyphens should be valid", violations, is(empty()));
  }

  @Test
  public void testDockerRepository_withSingleUppercaseLetter_isInvalid() {
    RepositoryXO xo = createRepository("myDockerrepo", "docker-hosted", "docker");
    Set<ConstraintViolation<RepositoryXO>> violations = validator.validate(xo);
    boolean hasDockerValidation = violations.stream()
        .anyMatch(v -> v.getMessage().contains("Docker repository names must be lowercase"));
    assertThat("Docker repository name with single uppercase letter should have Docker validation error",
        hasDockerValidation, is(true));
  }

  @Test
  public void testNullName_isValid() {
    RepositoryXO xo = createRepository(null, "docker-hosted", "docker");
    Set<ConstraintViolation<RepositoryXO>> violations = validator.validate(xo);
    // Null name will be caught by other validators (NotEmpty), not by Docker name validator
    assertThat("Null name should not trigger Docker validation",
        violations.stream().noneMatch(v -> v.getMessage().contains("lowercase")), is(true));
  }

  @Test
  public void testNullRepositoryXO_isValid() {
    // This test verifies the validator handles null gracefully
    DockerRepositoryNameValidator validator = new DockerRepositoryNameValidator();
    boolean result = validator.isValid(null, null);
    assertThat("Null RepositoryXO should be valid (handled gracefully)", result, is(true));
  }

  @Test
  public void testDockerRepository_caseInsensitiveFormatCheck() {
    RepositoryXO xo = createRepository("MyDockerRepo", "docker-hosted", "DOCKER");
    Set<ConstraintViolation<RepositoryXO>> violations = validator.validate(xo);
    boolean hasDockerValidation = violations.stream()
        .anyMatch(v -> v.getMessage().contains("Docker repository names must be lowercase"));
    assertThat("Format check should be case-insensitive, mixed case name should have Docker validation error",
        hasDockerValidation, is(true));
  }

  @Test
  public void testDockerRepository_caseInsensitiveRecipeCheck() {
    RepositoryXO xo = createRepository("MyDockerRepo", "DOCKER-hosted", "maven2");
    Set<ConstraintViolation<RepositoryXO>> violations = validator.validate(xo);
    boolean hasDockerValidation = violations.stream()
        .anyMatch(v -> v.getMessage().contains("Docker repository names must be lowercase"));
    assertThat("Recipe check should be case-insensitive, mixed case name should have Docker validation error",
        hasDockerValidation, is(true));
  }

  private RepositoryXO createRepository(String name, String recipe, String format) {
    RepositoryXO xo = new RepositoryXO();
    xo.setName(name);
    xo.setRecipe(recipe);
    xo.setFormat(format);
    xo.setOnline(true);

    // Attributes must not be empty - add a dummy entry
    HashMap<String, Map<String, Object>> attributes = new HashMap<>();
    attributes.put("storage", new HashMap<>());
    xo.setAttributes(attributes);
    return xo;
  }
}
