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
package org.sonatype.nexus.content.npm.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Singleton;

import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.content.facet.ContentFacet;
import org.sonatype.nexus.repository.content.fluent.FluentComponent;
import org.sonatype.nexus.repository.npm.internal.NpmCoordinatesHelper;

import org.springframework.stereotype.Component;

/**
 * Stores and resolves npm dist-tags.
 *
 * <p>Dist-tags are kept as a component attribute ({@code dist-tags}) so they survive restarts and are
 * visible to the browse/search facets. A component may carry any number of tags, but a tag points to a
 * single version; moving a tag removes it from the previous owner.</p>
 */
@Component
@Singleton
public class NpmDistTagsManager
    extends ComponentSupport
{
  static final String DIST_TAGS_ATTRIBUTE = "dist-tags";

  /**
   * Returns the effective dist-tags for a package, adding {@code latest} when it is not explicitly set.
   */
  public Map<String, String> get(final Repository repository, final String packagePath) {
    Map<String, String> tags = new LinkedHashMap<>();
    List<FluentComponent> components = components(repository, packagePath);

    for (FluentComponent component : components) {
      String version = component.version();
      if (version == null || version.isEmpty()) {
        continue;
      }
      for (String tag : tagsOf(component)) {
        tags.putIfAbsent(tag, version);
      }
    }

    if (!tags.containsKey("latest") && !components.isEmpty()) {
      String latest = NpmCoordinatesHelper.highestStableVersion(versions(components));
      if (latest == null) {
        latest = NpmCoordinatesHelper.highestVersion(versions(components));
      }
      if (latest != null) {
        tags.put("latest", latest);
      }
    }
    return tags;
  }

  /**
   * Points {@code tag} to {@code version}. Returns {@code false} when the version does not exist.
   */
  public boolean set(final Repository repository, final String packagePath, final String tag, final String version) {
    List<FluentComponent> components = components(repository, packagePath);
    FluentComponent target = null;
    for (FluentComponent component : components) {
      if (version.equals(component.version())) {
        target = component;
        break;
      }
    }
    if (target == null) {
      return false;
    }

    for (FluentComponent component : components) {
      Set<String> tags = tagsOf(component);
      if (component == target) {
        if (tags.add(tag)) {
          component.withAttribute(DIST_TAGS_ATTRIBUTE, new ArrayList<>(tags));
        }
      }
      else if (tags.remove(tag)) {
        setTags(component, tags);
      }
    }
    return true;
  }

  /**
   * Removes {@code tag} from every version of the package.
   */
  public boolean delete(final Repository repository, final String packagePath, final String tag) {
    boolean changed = false;
    for (FluentComponent component : components(repository, packagePath)) {
      Set<String> tags = tagsOf(component);
      if (tags.remove(tag)) {
        setTags(component, tags);
        changed = true;
      }
    }
    return changed;
  }

  /**
   * Replaces the complete tag set of {@code version}.
   */
  public boolean replace(
      final Repository repository,
      final String packagePath,
      final String version,
      final Collection<String> newTags)
  {
    List<FluentComponent> components = components(repository, packagePath);
    FluentComponent target = null;
    for (FluentComponent component : components) {
      if (version.equals(component.version())) {
        target = component;
        break;
      }
    }
    if (target == null) {
      return false;
    }

    for (FluentComponent component : components) {
      Set<String> tags = tagsOf(component);
      if (component == target) {
        tags.addAll(newTags);
      }
      else {
        tags.removeAll(newTags);
      }
      setTags(component, tags);
    }
    return true;
  }

  /**
   * Removes tags that point at versions which no longer exist and derives {@code latest} when it is missing.
   * Returns the effective tags after the repair.
   */
  public Map<String, String> repair(final Repository repository, final String packagePath) {
    List<FluentComponent> components = components(repository, packagePath);
    Set<String> versions = new LinkedHashSet<>(versions(components));
    for (FluentComponent component : components) {
      Set<String> tags = tagsOf(component);
      if (tags.retainAll(versions)) {
        setTags(component, tags);
      }
    }
    return get(repository, packagePath);
  }

  private List<FluentComponent> components(final Repository repository, final String packagePath) {
    ContentFacet contentFacet = repository.facet(ContentFacet.class);
    String namespace = NpmCoordinatesHelper.getComponentGroup(packagePath);
    String name = NpmCoordinatesHelper.getComponentName(packagePath);
    List<FluentComponent> components = new ArrayList<>();
    for (String version : contentFacet.components().versions(namespace, name)) {
      contentFacet.components().name(name).namespace(namespace).version(version).find().ifPresent(components::add);
    }
    return components;
  }

  private static Collection<String> versions(final List<FluentComponent> components) {
    List<String> versions = new ArrayList<>(components.size());
    for (FluentComponent component : components) {
      if (component.version() != null && !component.version().isEmpty()) {
        versions.add(component.version());
      }
    }
    return versions;
  }

  private static Set<String> tagsOf(final FluentComponent component) {
    Set<String> tags = new LinkedHashSet<>();
    Object value = component.attributes().get(DIST_TAGS_ATTRIBUTE);
    if (value instanceof Collection) {
      for (Object item : (Collection<?>) value) {
        if (item != null) {
          tags.add(item.toString());
        }
      }
    }
    else if (value != null) {
      tags.add(value.toString());
    }
    return tags;
  }

  private static void setTags(final FluentComponent component, final Set<String> tags) {
    if (tags.isEmpty()) {
      component.withoutAttribute(DIST_TAGS_ATTRIBUTE);
    }
    else {
      component.withAttribute(DIST_TAGS_ATTRIBUTE, new ArrayList<>(tags));
    }
  }
}
