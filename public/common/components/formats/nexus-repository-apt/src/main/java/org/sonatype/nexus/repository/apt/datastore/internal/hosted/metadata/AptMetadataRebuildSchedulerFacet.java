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
package org.sonatype.nexus.repository.apt.datastore.internal.hosted.metadata;

import java.time.Duration;
import java.util.Date;
import java.util.Optional;

import jakarta.inject.Inject;

import org.sonatype.nexus.common.cooperation2.Cooperation2;
import org.sonatype.nexus.common.cooperation2.Cooperation2Factory;
import org.sonatype.nexus.common.event.EventAware;
import org.sonatype.nexus.common.time.Clock;
import org.sonatype.nexus.repository.Facet.Exposed;
import org.sonatype.nexus.repository.FacetSupport;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.RepositoryTaskSupport;
import org.sonatype.nexus.repository.apt.AptFormat;
import org.sonatype.nexus.repository.apt.internal.AptProperties;
import org.sonatype.nexus.repository.content.Asset;
import org.sonatype.nexus.repository.content.event.asset.AssetCreatedEvent;
import org.sonatype.nexus.repository.content.event.asset.AssetDeletedEvent;
import org.sonatype.nexus.repository.content.event.asset.AssetPurgedEvent;
import org.sonatype.nexus.repository.content.event.asset.AssetUpdatedEvent;
import org.sonatype.nexus.repository.content.event.component.ComponentPurgedEvent;
import org.sonatype.nexus.repository.types.HostedType;
import org.sonatype.nexus.scheduling.TaskConfiguration;
import org.sonatype.nexus.scheduling.TaskInfo;
import org.sonatype.nexus.scheduling.TaskScheduler;
import org.sonatype.nexus.scheduling.schedule.Once;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.Subscribe;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Facet responsible for scheduling APT metadata rebuilds in response to repository events.
 * Uses TaskScheduler for cluster-safe, observable, and cancellable scheduling.
 */
@Component
@Exposed
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class AptMetadataRebuildSchedulerFacet
    extends FacetSupport
    implements EventAware.Asynchronous
{
  private final TaskScheduler taskScheduler;

  private final Cooperation2Factory.Builder cooperationBuilder;

  private Cooperation2 cooperation;

  private final Duration rebuildDelay;

  private final Clock clock;

  @Inject
  public AptMetadataRebuildSchedulerFacet(
      final TaskScheduler taskScheduler,
      final Cooperation2Factory cooperationFactory,
      final Clock clock,
      @Value("${nexus.apt.metadata.rebuild.debounce:2s}") final Duration rebuildDelay,
      @Value("${nexus.apt.metadata.cooperation.enabled:true}") final boolean cooperationEnabled,
      @Value("${nexus.apt.metadata.cooperation.majorTimeout:0s}") final Duration majorTimeout,
      @Value("${nexus.apt.metadata.cooperation.minorTimeout:30s}") final Duration minorTimeout,
      @Value("${nexus.apt.metadata.cooperation.threadsPerKey:100}") final int threadsPerKey)
  {
    this.taskScheduler = checkNotNull(taskScheduler);
    this.clock = checkNotNull(clock);

    checkArgument(!rebuildDelay.isNegative(), "nexus.apt.metadata.rebuild.debounce must be positive");
    this.rebuildDelay = rebuildDelay;

    this.cooperationBuilder = checkNotNull(cooperationFactory).configure()
        .enabled(cooperationEnabled)
        .majorTimeout(majorTimeout)
        .minorTimeout(minorTimeout)
        .threadsPerKey(threadsPerKey);
  }

  @Override
  protected void doStart() {
    String repositoryName = getRepository().getName();
    cooperation = cooperationBuilder.build(getClass(), repositoryName);
    log.info("AptMetadataRebuildSchedulerFacet started for repository: {}", repositoryName);
  }

  /**
   * Event handler for ComponentPurgedEvent.
   * Triggered when components are purged (e.g., by cleanup policies).
   * Removes package metadata from KV store before scheduling rebuild.
   */
  @Subscribe
  @AllowConcurrentEvents
  public void on(final ComponentPurgedEvent event) {
    String repositoryName = getRepository().getName();
    log.debug("ComponentPurgedEvent received - checking if for repository {}", repositoryName);

    Optional<Repository> eventRepository = event.getRepository();
    if (eventRepository.isPresent()) {
      Repository repository = eventRepository.get();
      log.debug("Event repository: {}, This repository: {}", repository.getName(), repositoryName);
      if (repository.getName().equals(repositoryName) && isAptHostedRepository(repository)) {
        log.info("ComponentPurgedEvent for repository {} - removing metadata", repository.getName());

        // Remove package metadata from KV store for purged components
        if (event.getAssets() != null && !event.getAssets().isEmpty()) {
          log.debug("Removing metadata for {} purged assets", event.getAssets().size());
          AptHostedMetadataFacet metadataFacet = repository.facet(AptHostedMetadataFacet.class);
          event.getAssets().forEach(asset -> {
            if (isDebAsset(asset)) {
              log.debug("Removing metadata for purged asset: {}", asset.path());
              metadataFacet.removePackageMetadata(asset);
            }
          });
        }
        else {
          log.debug("ComponentPurgedEvent has no asset list - cannot remove metadata from KV store. Component IDs: {}",
              event.getComponentIds());
        }

        log.info("ComponentPurgedEvent for repository {} - scheduling metadata rebuild", repository.getName());
        maybeScheduleRebuild();
      }
    }
    else {
      log.warn("ComponentPurgedEvent received but repository is not present - cannot determine which repository");
    }
  }

  /**
   * Event handler for AssetPurgedEvent.
   * This event is fired when assets WITHOUT components are purged in bulk.
   * For .deb packages (which have components), ComponentPurgedEvent is used instead.
   * This handler is mainly for metadata files cleanup.
   */
  @Subscribe
  @AllowConcurrentEvents
  public void on(final AssetPurgedEvent event) {
    String repositoryName = getRepository().getName();
    log.debug("AssetPurgedEvent received - checking if for repository {}", repositoryName);

    Optional<Repository> eventRepository = event.getRepository();
    if (eventRepository.isPresent()) {
      Repository repository = eventRepository.get();
      log.debug("Event repository: {}, This repository: {}", repository.getName(), repositoryName);
      if (repository.getName().equals(repositoryName) && isAptHostedRepository(repository)) {
        log.info("AssetPurgedEvent for repository {} - scheduling metadata rebuild", repository.getName());
        maybeScheduleRebuild();
      }
    }
    else {
      log.warn("AssetPurgedEvent received but repository is not present - cannot determine which repository");
    }
  }

  /**
   * Event handler for AssetCreatedEvent.
   */
  @Subscribe
  @AllowConcurrentEvents
  public void on(final AssetCreatedEvent event) {
    handleAssetEvent("AssetCreatedEvent", event.getRepository(), event.getAsset());
  }

  /**
   * Event handler for AssetDeletedEvent.
   */
  @Subscribe
  @AllowConcurrentEvents
  public void on(final AssetDeletedEvent event) {
    handleAssetEvent("AssetDeletedEvent", event.getRepository(), event.getAsset());
  }

  /**
   * Event handler for AssetUpdatedEvent.
   */
  @Subscribe
  @AllowConcurrentEvents
  public void on(final AssetUpdatedEvent event) {
    handleAssetEvent("AssetUpdatedEvent", event.getRepository(), event.getAsset());
  }

  private void handleAssetEvent(final String eventType, final Optional<Repository> eventRepository, final Asset asset) {
    eventRepository.ifPresent(repository -> {
      if (repository.getName().equals(getRepository().getName()) &&
          isAptHostedRepository(repository) && isDebAsset(asset)) {
        log.info("{} for repository {} - scheduling metadata rebuild", eventType, repository.getName());
        maybeScheduleRebuild();
      }
    });
  }

  /**
   * Schedule a metadata rebuild task if one is not already scheduled.
   * Uses Cooperation2 to ensure only one scheduler runs at a time.
   */
  public void maybeScheduleRebuild() {
    String repositoryName = getRepository().getName();
    log.debug("Maybe scheduling rebuild for APT repository {}", repositoryName);

    try {
      TaskInfo taskInfo = cooperation.on(this::scheduleBuild)
          .checkFunction(this::getScheduledTask)
          .performWorkOnFail(false)
          .cooperate(repositoryName);

      log.debug("Found or scheduled task {}", taskInfo);
    }
    catch (Exception e) {
      if (log.isDebugEnabled()) {
        log.warn("Failed to schedule rebuild of metadata for repository {}", repositoryName, e);
      }
      else {
        log.warn("Failed to schedule rebuild of metadata for repository {} cause: {}",
            repositoryName, e.getMessage());
      }
    }
  }

  private TaskInfo scheduleBuild() {
    log.debug("Attempting to schedule task for APT repository {}", getRepository().getName());

    // Check for existing waiting task
    Optional<TaskInfo> waitingTask = getScheduledTask();
    if (waitingTask.isPresent()) {
      log.debug("Found existing waiting rebuild task - keeping it");
      return waitingTask.get();
    }

    // No waiting task - create a new one
    // If a task is currently running, the new task will run after it completes,
    // ensuring changes that occur during the current rebuild are captured by a subsequent rebuild.
    return createTask();
  }

  private TaskInfo createTask() {
    String repositoryName = getRepository().getName();
    log.debug("Creating new rebuild task for APT repository {}", repositoryName);
    TaskConfiguration taskConfiguration =
        taskScheduler.createTaskConfigurationInstance(AutomatedAptMetadataRebuildTaskDescriptor.TYPE_ID);

    taskConfiguration.setName("Metadata rebuild for " + repositoryName);
    taskConfiguration.setString(RepositoryTaskSupport.REPOSITORY_NAME_FIELD_ID,
        repositoryName);

    long time = clock.clusterTime().plus(rebuildDelay).toInstant().toEpochMilli();
    return taskScheduler.scheduleTask(taskConfiguration, new Once(new Date(time)));
  }

  /**
   * Find an existing waiting task for this repository.
   */
  private Optional<TaskInfo> getScheduledTask() {
    return taskScheduler.listsTasks()
        .stream()
        .filter(task -> AutomatedAptMetadataRebuildTaskDescriptor.TYPE_ID.equals(task.getTypeId()))
        .filter(this::isForSameRepository)
        .filter(this::isTaskWaiting)
        .findAny();
  }

  /**
   * Check whether a task is for this repository.
   */
  private boolean isForSameRepository(final TaskInfo task) {
    return getRepository().getName()
        .equals(task
            .getConfiguration()
            .getString(RepositoryTaskSupport.REPOSITORY_NAME_FIELD_ID));
  }

  /**
   * Check whether the task is waiting to run (not done and not currently running).
   */
  private boolean isTaskWaiting(final TaskInfo task) {
    return taskScheduler.toExternalTaskState(task).getState().isWaiting();
  }

  private static boolean isDebAsset(final Asset asset) {
    return AptProperties.DEB.equals(asset.kind());
  }

  private static boolean isAptHostedRepository(final Repository repository) {
    return AptFormat.NAME.equals(repository.getFormat().getValue()) &&
        HostedType.NAME.equals(repository.getType().getValue());
  }
}
