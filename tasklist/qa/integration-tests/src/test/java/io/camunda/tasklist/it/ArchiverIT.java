/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.archiver.ArchiverUtil;
import io.camunda.tasklist.archiver.TaskArchiverJob;
import io.camunda.tasklist.exceptions.ArchiverException;
import io.camunda.tasklist.store.TaskStore;
import io.camunda.tasklist.util.CollectionUtil;
import io.camunda.tasklist.util.NoSqlHelper;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.webapps.schema.descriptors.tasklist.template.SnapshotTaskVariableTemplate;
import io.camunda.webapps.schema.descriptors.tasklist.template.TaskTemplate;
import io.camunda.webapps.schema.entities.tasklist.TaskEntity;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class ArchiverIT extends TasklistZeebeIntegrationTest {

  @Autowired private BeanFactory beanFactory;

  @Autowired private ArchiverUtil archiverUtil;

  @Autowired private TaskTemplate taskTemplate;

  @Autowired private SnapshotTaskVariableTemplate taskVariableTemplate;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private TaskStore taskStore;

  @Autowired private NoSqlHelper noSqlHelper;

  private TaskArchiverJob archiverJob;

  private final Random random = new Random();

  private DateTimeFormatter dateTimeFormatter;

  @Override
  @BeforeEach
  public void before() {
    super.before();
    dateTimeFormatter =
        DateTimeFormatter.ofPattern(tasklistProperties.getArchiver().getRolloverDateFormat())
            .withZone(ZoneId.systemDefault());
    archiverJob = beanFactory.getBean(TaskArchiverJob.class, partitionHolder.getPartitionIds());
    clearMetrics();
  }

  @Test
  public void testArchivingTasks() throws ArchiverException, IOException {
    final DateTimeFormatter sdf = DateTimeFormatter.ofPattern("YYYY-MM-dd");
    final Map<String, Integer> mapCount = new HashMap<>();

    final Instant currentTime = pinZeebeTime();

    // having
    // deploy process
    offsetZeebeTime(Duration.ofDays(-4));
    final String processId = "demoProcess";
    final String flowNodeBpmnId = "task1";
    deployProcessWithOneFlowNode(processId, flowNodeBpmnId);

    // start and finish instances 2 days ago
    final int count1 = random.nextInt(6) + 3;
    final Instant endDate1 = currentTime.minus(2, ChronoUnit.DAYS);
    final List<String> ids1 =
        startInstancesAndCompleteTasks(processId, flowNodeBpmnId, count1, endDate1);
    mapCount.put(dateTimeFormatter.format(endDate1), count1);

    // start and finish instances 1 day ago
    final int count2 = random.nextInt(6) + 3;
    final Instant endDate2 = currentTime.minus(1, ChronoUnit.DAYS);
    final List<String> ids2 =
        startInstancesAndCompleteTasks(processId, flowNodeBpmnId, count2, endDate2);
    mapCount.put(dateTimeFormatter.format(endDate2), count2);

    // start instances 1 day ago
    final int count3 = random.nextInt(6) + 3;
    final List<String> ids3 =
        startInstances(processId, flowNodeBpmnId, count3, currentTime.minus(1, ChronoUnit.DAYS));
    resetZeebeTime();

    // when
    final Map.Entry<String, Integer> result1 = archiverJob.archiveNextBatch().join();
    assertThat(mapCount.get(result1.getKey())).isEqualTo(result1.getValue());
    databaseTestExtension.refreshIndexesInElasticsearch();

    final Map.Entry<String, Integer> result2 = archiverJob.archiveNextBatch().join();
    assertThat(mapCount.get(result2.getKey())).isEqualTo(result2.getValue());
    databaseTestExtension.refreshIndexesInElasticsearch();

    assertThat(archiverJob.archiveNextBatch().join())
        .isEqualTo(
            Map.entry(
                "NothingToArchive",
                0)); // 3rd run should not move anything, as the rest of the tasks are not completed

    databaseTestExtension.refreshIndexesInElasticsearch();

    // then
    assertTasksInCorrectIndex(count1, ids1, endDate1);
    assertTasksInCorrectIndex(count2, ids2, endDate2);
    assertTasksInCorrectIndex(count3, ids3, null);

    assertAllInstancesInAlias(count1 + count2 + count3, ids1.get(0));
  }

  private void assertAllInstancesInAlias(final int count, final String id) throws IOException {
    assertThat(tester.getAllTasks().get("$.data.tasks.length()")).isEqualTo(String.valueOf(count));
    final String taskId = tester.getTaskById(id).get("$.data.task.id");
    assertThat(taskId).isEqualTo(id);
  }

  @Test
  public void testArchivingOnlyOneHourOldData() throws ArchiverException, IOException {
    final Instant currentTime = pinZeebeTime();

    // having
    // deploy process
    offsetZeebeTime(Duration.ofDays(-4));
    final String processId = "demoProcess";
    final String flowNodeBpmnId = "task1";
    deployProcessWithOneFlowNode(processId, flowNodeBpmnId);

    // start and finish instances 2 hours ago
    final int count1 = random.nextInt(6) + 3;
    final Instant endDate1 = currentTime.minus(2, ChronoUnit.HOURS);
    final List<String> ids1 =
        startInstancesAndCompleteTasks(processId, flowNodeBpmnId, count1, endDate1);

    // start and finish instances 50 minutes ago
    final int count2 = random.nextInt(6) + 3;
    final Instant endDate2 = currentTime.minus(50, ChronoUnit.MINUTES);
    final List<String> ids2 =
        startInstancesAndCompleteTasks(processId, flowNodeBpmnId, count2, endDate2);

    resetZeebeTime();

    // when
    assertThat(archiverJob.archiveNextBatch().join().getValue()).isEqualTo(count1);
    databaseTestExtension.refreshIndexesInElasticsearch();
    // 2rd run should not move anything, as the rest of the tasks are completed less then 1 hour ago
    assertThat(archiverJob.archiveNextBatch().join()).isEqualTo(Map.entry("NothingToArchive", 0));

    databaseTestExtension.refreshIndexesInElasticsearch();

    // then
    assertTasksInCorrectIndex(count1, ids1, endDate1);
    assertTasksInCorrectIndex(count2, ids2, null);
  }

  private void deployProcessWithOneFlowNode(final String processId, final String flowNodeBpmnId) {
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .userTask(flowNodeBpmnId)
            .endEvent()
            .done();
    tester.deployProcess(process, processId + ".bpmn").waitUntil().processIsDeployed();
  }

  private void assertTasksInCorrectIndex(
      final int tasksCount, final List<String> ids, final Instant endDate) throws IOException {
    assertTaskIndex(tasksCount, ids, endDate);
    assertDependentIndex(
        taskVariableTemplate.getFullQualifiedName(),
        SnapshotTaskVariableTemplate.TASK_ID,
        ids,
        endDate);
  }

  private void assertTaskIndex(final int tasksCount, final List<String> ids, final Instant endDate)
      throws IOException {
    final String destinationIndexName;
    if (endDate != null) {
      destinationIndexName =
          archiverUtil.getDestinationIndexName(
              taskTemplate.getFullQualifiedName(), dateTimeFormatter.format(endDate));
    } else {
      destinationIndexName =
          archiverUtil.getDestinationIndexName(taskTemplate.getFullQualifiedName(), "");
    }

    final List<TaskEntity> tasksResponse =
        noSqlHelper.getTasksFromIdAndIndex(
            destinationIndexName, Arrays.stream(CollectionUtil.toSafeArrayOfStrings(ids)).toList());

    assertThat(tasksResponse).hasSize(tasksCount);
    assertThat(tasksResponse).extracting(TaskTemplate.ID).containsExactlyInAnyOrderElementsOf(ids);
    if (endDate != null) {
      assertThat(tasksResponse)
          .extracting(TaskTemplate.COMPLETION_TIME)
          .allMatch(ed -> ((OffsetDateTime) ed).toInstant().equals(endDate));
    }
  }

  private void assertDependentIndex(
      final String mainIndexName,
      final String idFieldName,
      final List<String> ids,
      final Instant endDate)
      throws IOException {
    final String destinationIndexName;
    if (endDate != null) {
      destinationIndexName =
          archiverUtil.getDestinationIndexName(mainIndexName, dateTimeFormatter.format(endDate));
    } else {
      destinationIndexName = archiverUtil.getDestinationIndexName(mainIndexName, "");
    }

    final List<String> idsFromEls =
        noSqlHelper.getIdsFromIndex(idFieldName, destinationIndexName, ids);
    assertThat(idsFromEls).as(mainIndexName).isSubsetOf(ids);
  }

  private List<String> startInstancesAndCompleteTasks(
      final String processId,
      final String flowNodeBpmnId,
      final int count,
      final Instant currentTime) {
    assertThat(count).isGreaterThan(0);
    pinZeebeTime(currentTime);
    final List<String> ids = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      ids.add(
          tester
              .startProcessInstance(processId, "{\"var\": 123}")
              .waitUntil()
              .taskIsCreated(flowNodeBpmnId)
              .claimAndCompleteHumanTask(flowNodeBpmnId)
              .waitUntil()
              .taskIsCompleted(flowNodeBpmnId)
              .getTaskId());
    }
    return ids;
  }

  private List<String> startInstances(
      final String processId,
      final String flowNodeBpmnId,
      final int count,
      final Instant currentTime) {
    assertThat(count).isGreaterThan(0);
    pinZeebeTime(currentTime);
    final List<String> ids = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      ids.add(
          tester
              .startProcessInstance(processId, "{\"var\": 123}")
              .waitUntil()
              .taskIsCreated(flowNodeBpmnId)
              .getTaskId());
    }
    return ids;
  }
}
