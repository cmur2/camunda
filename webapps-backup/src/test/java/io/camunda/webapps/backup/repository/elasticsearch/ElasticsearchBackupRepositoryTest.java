/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.backup.repository.elasticsearch;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.webapps.backup.BackupStateDto;
import io.camunda.webapps.backup.Metadata;
import io.camunda.webapps.backup.repository.BackupRepositoryProps;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.elasticsearch.action.admin.cluster.snapshots.get.GetSnapshotsResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.SnapshotClient;
import org.elasticsearch.snapshots.SnapshotId;
import org.elasticsearch.snapshots.SnapshotInfo;
import org.elasticsearch.snapshots.SnapshotState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ElasticsearchBackupRepositoryTest {

  private final String repositoryName = "repo1";
  private final long backupId = 555;
  private final String snapshotName = "camunda_operate_" + backupId + "_8.6_part_1_of_6";
  private final long incompleteCheckTimeoutLengthSeconds = 5 * 60L; // 5 minutes
  private final long incompleteCheckTimeoutLength = incompleteCheckTimeoutLengthSeconds * 1000;
  @Mock private RestHighLevelClient esClient;
  @Mock private SnapshotClient snapshotClient;
  @Spy private ObjectMapper objectMapper = new ObjectMapper();

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private BackupRepositoryProps backupProps;

  private ElasticsearchBackupRepository backupRepository;

  @BeforeEach
  public void setup() {
    backupRepository =
        Mockito.spy(
            new ElasticsearchBackupRepository(
                esClient, objectMapper, backupProps, new TestSnapshotProvider()));
  }

  @Test
  public void testWaitingForSnapshotWithTimeout() {
    final int timeout = 1;
    final SnapshotState snapshotState = SnapshotState.IN_PROGRESS;

    // mock calls to `findSnapshot` and `operateProperties`
    final SnapshotInfo snapshotInfo = Mockito.mock(SnapshotInfo.class, Mockito.RETURNS_DEEP_STUBS);
    when(snapshotInfo.state()).thenReturn(snapshotState);
    when(snapshotInfo.snapshotId().getName()).thenReturn(snapshotName);
    when(backupProps.snapshotTimeout()).thenReturn(timeout);
    doReturn(List.of(snapshotInfo))
        .when(backupRepository)
        .findSnapshots(ArgumentMatchers.any(), ArgumentMatchers.any());

    final boolean finished =
        backupRepository.isSnapshotFinishedWithinTimeout(repositoryName, snapshotName);

    assertThat(finished).isFalse();
    Mockito.verify(backupRepository, Mockito.atLeast(5)).findSnapshots(repositoryName, backupId);
  }

  @Test
  public void testWaitingForSnapshotTillCompleted() {
    final int timeout = 0;

    // mock calls to `findSnapshot` and `operateProperties`
    final SnapshotInfo snapshotInfo = Mockito.mock(SnapshotInfo.class, Mockito.RETURNS_DEEP_STUBS);
    when(snapshotInfo.state())
        .thenReturn(SnapshotState.IN_PROGRESS)
        .thenReturn(SnapshotState.IN_PROGRESS)
        .thenReturn(SnapshotState.SUCCESS);
    when(snapshotInfo.snapshotId().getName()).thenReturn(snapshotName);
    when(backupProps.snapshotTimeout()).thenReturn(timeout);
    doReturn(List.of(snapshotInfo))
        .when(backupRepository)
        .findSnapshots(ArgumentMatchers.any(), ArgumentMatchers.any());

    final boolean finished =
        backupRepository.isSnapshotFinishedWithinTimeout(repositoryName, snapshotName);

    assertThat(finished).isTrue();
    Mockito.verify(backupRepository, Mockito.times(3)).findSnapshots(repositoryName, backupId);
  }

  @Test
  public void testWaitingForSnapshotWithoutTimeout() {
    final int timeout = 0;
    final SnapshotState snapshotState = SnapshotState.IN_PROGRESS;

    // mock calls to `findSnapshot` and `operateProperties`
    final SnapshotInfo snapshotInfo = Mockito.mock(SnapshotInfo.class, Mockito.RETURNS_DEEP_STUBS);
    when(snapshotInfo.state()).thenReturn(snapshotState);
    when(snapshotInfo.snapshotId().getName()).thenReturn(snapshotName);
    when(backupProps.snapshotTimeout()).thenReturn(timeout);
    doReturn(List.of(snapshotInfo))
        .when(backupRepository)
        .findSnapshots(ArgumentMatchers.any(), ArgumentMatchers.any());

    // we expect infinite loop, so we call snapshotting in separate thread
    final ExecutorService executor = Executors.newSingleThreadExecutor();
    final Future<?> future =
        executor.submit(
            () -> {
              backupRepository.isSnapshotFinishedWithinTimeout(repositoryName, snapshotName);
            });

    try {
      // Set a timeout of 2 seconds for the function to complete
      future.get(2, TimeUnit.SECONDS);
    } catch (final TimeoutException e) {
      // expected
      return;
    } catch (final InterruptedException | ExecutionException e) {
      // ignore
    } finally {
      // Shutdown the executor
      executor.shutdownNow();
    }

    fail("Expected to continue waiting for snapshotting to finish.");
  }

  @Test
  void shouldCreateRepository() {
    assertThat(backupRepository).isNotNull();
  }

  @Test
  void shouldReturnBackupStateCompleted() throws IOException {
    final var snapshotClient = Mockito.mock(SnapshotClient.class);
    final var firstSnapshotInfo = Mockito.mock(SnapshotInfo.class);
    final var snapshotResponse = Mockito.mock(GetSnapshotsResponse.class);

    // Set up Snapshot client
    when(esClient.snapshot()).thenReturn(snapshotClient);
    // Set up Snapshot details
    final Map<String, Object> metadata =
        objectMapper.convertValue(new Metadata(1L, "1", 1, 1), Map.class);
    when(firstSnapshotInfo.userMetadata()).thenReturn(metadata);
    when(firstSnapshotInfo.snapshotId()).thenReturn(new SnapshotId("snapshot-name", "uuid"));
    when(firstSnapshotInfo.state()).thenReturn(SnapshotState.SUCCESS);

    // Set up Snapshot response
    when(snapshotResponse.getSnapshots()).thenReturn(List.of(firstSnapshotInfo));
    when(snapshotClient.get(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(snapshotResponse);

    // Test
    final var backupState = backupRepository.getBackupState("repository-name", 5L);
    assertThat(backupState.getState()).isEqualTo(BackupStateDto.COMPLETED);
  }

  @Test
  void shouldReturnBackupStateIncomplete() throws IOException {
    final var snapshotClient = Mockito.mock(SnapshotClient.class);
    final var firstSnapshotInfo = Mockito.mock(SnapshotInfo.class);
    final var snapshotResponse = Mockito.mock(GetSnapshotsResponse.class);
    final var lastSnapshotInfo = Mockito.mock(SnapshotInfo.class);

    // Set up Snapshot client
    when(esClient.snapshot()).thenReturn(snapshotClient);
    // Set up operate properties
    when(backupProps.incompleteCheckTimeoutInSeconds())
        .thenReturn(incompleteCheckTimeoutLengthSeconds);
    // Set up first Snapshot details
    final Map<String, Object> metadata =
        objectMapper.convertValue(new Metadata(1L, "1", 1, 3), Map.class);
    when(firstSnapshotInfo.userMetadata()).thenReturn(metadata);
    when(firstSnapshotInfo.snapshotId()).thenReturn(new SnapshotId("snapshot-name", "uuid"));
    when(firstSnapshotInfo.state()).thenReturn(SnapshotState.SUCCESS);
    when(firstSnapshotInfo.startTime()).thenReturn(23L);

    // Set up last Snapshot details
    when(lastSnapshotInfo.snapshotId()).thenReturn(new SnapshotId("snapshot-name", "uuid"));
    when(lastSnapshotInfo.endTime()).thenReturn(23L + 6 * 60 * 1_000);
    when(lastSnapshotInfo.state()).thenReturn(SnapshotState.SUCCESS);

    // Set up Snapshot response
    when(snapshotResponse.getSnapshots()).thenReturn(List.of(firstSnapshotInfo, lastSnapshotInfo));
    when(snapshotClient.get(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(snapshotResponse);

    // Test
    final var backupState = backupRepository.getBackupState("repository-name", 5L);
    org.assertj.core.api.Assertions.assertThat(backupState.getState())
        .isEqualTo(BackupStateDto.INCOMPLETE);
  }

  @Test
  void shouldReturnBackupStateIncompleteWhenLastSnapshotEndTimeIsTimedOut() throws IOException {
    final var snapshotClient = Mockito.mock(SnapshotClient.class);
    final var firstSnapshotInfo = Mockito.mock(SnapshotInfo.class);
    final var snapshotResponse = Mockito.mock(GetSnapshotsResponse.class);
    final var lastSnapshotInfo = Mockito.mock(SnapshotInfo.class);
    final long now = Instant.now().toEpochMilli();

    // Set up Snapshot client
    when(esClient.snapshot()).thenReturn(snapshotClient);
    // Set up operate properties
    when(backupProps.incompleteCheckTimeoutInSeconds())
        .thenReturn(incompleteCheckTimeoutLengthSeconds);
    // Set up first Snapshot details
    final Map<String, Object> metadata =
        objectMapper.convertValue(new Metadata(1L, "1", 1, 3), Map.class);
    when(firstSnapshotInfo.userMetadata()).thenReturn(metadata);
    when(firstSnapshotInfo.snapshotId()).thenReturn(new SnapshotId("snapshot-name", "uuid"));
    when(firstSnapshotInfo.state()).thenReturn(SnapshotState.SUCCESS);
    when(firstSnapshotInfo.startTime()).thenReturn(now - 4_000);

    // Set up last Snapshot details
    when(lastSnapshotInfo.snapshotId()).thenReturn(new SnapshotId("snapshot-name", "uuid"));
    when(lastSnapshotInfo.state()).thenReturn(SnapshotState.SUCCESS);
    when(lastSnapshotInfo.startTime()).thenReturn(now - (incompleteCheckTimeoutLength + 4_000));
    when(lastSnapshotInfo.endTime()).thenReturn(now - (incompleteCheckTimeoutLength + 2_000));

    // Set up Snapshot response
    when(snapshotResponse.getSnapshots()).thenReturn(List.of(firstSnapshotInfo, lastSnapshotInfo));
    when(snapshotClient.get(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(snapshotResponse);

    // Test
    final var backupState = backupRepository.getBackupState("repository-name", 5L);
    assertThat(backupState.getState()).isEqualTo(BackupStateDto.INCOMPLETE);
  }

  @Test
  void shouldReturnBackupStateProgress() throws IOException {
    final var snapshotClient = Mockito.mock(SnapshotClient.class);
    final var firstSnapshotInfo = Mockito.mock(SnapshotInfo.class);
    final var lastSnapshotInfo = Mockito.mock(SnapshotInfo.class);
    final var snapshotResponse = Mockito.mock(GetSnapshotsResponse.class);
    final long now = Instant.now().toEpochMilli();

    // Set up Snapshot client
    when(esClient.snapshot()).thenReturn(snapshotClient);
    // Set up operate properties
    when(backupProps.incompleteCheckTimeoutInSeconds())
        .thenReturn(incompleteCheckTimeoutLengthSeconds);
    // Set up Snapshot details
    final Map<String, Object> metadata =
        objectMapper.convertValue(new Metadata(1L, "1", 1, 3), Map.class);
    when(firstSnapshotInfo.userMetadata()).thenReturn(metadata);
    when(firstSnapshotInfo.snapshotId())
        .thenReturn(new SnapshotId("first-snapshot-name", "uuid-first"));
    when(lastSnapshotInfo.snapshotId())
        .thenReturn(new SnapshotId("last-snapshot-name", "uuid-last"));
    when(firstSnapshotInfo.state()).thenReturn(SnapshotState.SUCCESS);
    when(lastSnapshotInfo.state()).thenReturn(SnapshotState.SUCCESS);
    when(firstSnapshotInfo.startTime()).thenReturn(now - 4_000);
    when(lastSnapshotInfo.startTime()).thenReturn(now - 200);
    when(lastSnapshotInfo.endTime()).thenReturn(now - 5);
    // Set up Snapshot response
    when(snapshotResponse.getSnapshots()).thenReturn(List.of(firstSnapshotInfo, lastSnapshotInfo));
    when(snapshotClient.get(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(snapshotResponse);

    // Test
    final var backupState = backupRepository.getBackupState("repository-name", 5L);
    assertThat(backupState.getState()).isEqualTo(BackupStateDto.IN_PROGRESS);
  }
}
