/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport.v870.processors.es;

import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATING;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.entities.FlowNodeInstanceEntity;
import io.camunda.tasklist.entities.FlowNodeType;
import io.camunda.tasklist.entities.listview.ListViewJoinRelation;
import io.camunda.tasklist.entities.listview.ProcessInstanceListViewEntity;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.schema.indices.FlowNodeInstanceIndex;
import io.camunda.tasklist.schema.templates.TasklistListViewTemplate;
import io.camunda.tasklist.util.ConversionUtils;
import io.camunda.tasklist.zeebeimport.v870.record.value.ProcessInstanceRecordValueImpl;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class ProcessInstanceZeebeRecordProcessorElasticSearch {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ProcessInstanceZeebeRecordProcessorElasticSearch.class);

  private static final Set<String> FLOW_NODE_STATES = new HashSet<>();

  private static final List<BpmnElementType> VARIABLE_SCOPE_TYPES =
      Arrays.asList(
          BpmnElementType.PROCESS,
          BpmnElementType.SUB_PROCESS,
          BpmnElementType.EVENT_SUB_PROCESS,
          BpmnElementType.SERVICE_TASK,
          BpmnElementType.USER_TASK,
          BpmnElementType.MULTI_INSTANCE_BODY);

  static {
    FLOW_NODE_STATES.add(ELEMENT_ACTIVATING.name());
  }

  @Autowired
  @Qualifier("tasklistObjectMapper")
  private ObjectMapper objectMapper;

  @Autowired private FlowNodeInstanceIndex flowNodeInstanceIndex;

  @Autowired private TasklistListViewTemplate tasklistListViewTemplate;

  public void processProcessInstanceRecord(final Record record, final BulkRequest bulkRequest)
      throws PersistenceException {

    final ProcessInstanceRecordValueImpl recordValue =
        (ProcessInstanceRecordValueImpl) record.getValue();
    if (isVariableScopeType(recordValue) && FLOW_NODE_STATES.contains(record.getIntent().name())) {
      final FlowNodeInstanceEntity flowNodeInstance = createFlowNodeInstance(record);
      bulkRequest.add(getFlowNodeInstanceQuery(flowNodeInstance));
      final IndexRequest processRequest = persistFlowNodeDataToListView(flowNodeInstance);
      if (processRequest != null) {
        bulkRequest.add(processRequest);
      }
    }
  }

  private FlowNodeInstanceEntity createFlowNodeInstance(final Record record) {
    final ProcessInstanceRecordValueImpl recordValue =
        (ProcessInstanceRecordValueImpl) record.getValue();
    final FlowNodeInstanceEntity entity = new FlowNodeInstanceEntity();
    entity.setId(ConversionUtils.toStringOrNull(record.getKey()));
    entity.setKey(record.getKey());
    entity.setPartitionId(record.getPartitionId());
    entity.setProcessInstanceId(String.valueOf(recordValue.getProcessInstanceKey()));
    entity.setParentFlowNodeId(String.valueOf(recordValue.getFlowScopeKey()));
    entity.setType(
        FlowNodeType.fromZeebeBpmnElementType(
            recordValue.getBpmnElementType() == null
                ? null
                : recordValue.getBpmnElementType().name()));
    entity.setPosition(record.getPosition());
    return entity;
  }

  private IndexRequest getFlowNodeInstanceQuery(final FlowNodeInstanceEntity entity)
      throws PersistenceException {
    try {
      LOGGER.debug("Flow node instance: id {}", entity.getId());

      return new IndexRequest(flowNodeInstanceIndex.getFullQualifiedName())
          .id(entity.getId())
          .source(objectMapper.writeValueAsString(entity), XContentType.JSON);
    } catch (final IOException e) {
      throw new PersistenceException(
          String.format(
              "Error preparing the query to index flow node instance [%s]", entity.getId()),
          e);
    }
  }

  private boolean isVariableScopeType(final ProcessInstanceRecordValueImpl recordValue) {
    final BpmnElementType bpmnElementType = recordValue.getBpmnElementType();
    if (bpmnElementType == null) {
      return false;
    }
    return VARIABLE_SCOPE_TYPES.contains(bpmnElementType);
  }

  private IndexRequest persistFlowNodeDataToListView(final FlowNodeInstanceEntity flowNodeInstance)
      throws PersistenceException {
    final ProcessInstanceListViewEntity processInstanceListViewEntity =
        new ProcessInstanceListViewEntity();

    if (flowNodeInstance.getType().equals(FlowNodeType.PROCESS)) {
      final ListViewJoinRelation listViewJoinRelation = new ListViewJoinRelation();
      processInstanceListViewEntity.setId(flowNodeInstance.getId());
      processInstanceListViewEntity.setPartitionId(flowNodeInstance.getPartitionId());
      processInstanceListViewEntity.setTenantId(flowNodeInstance.getTenantId());
      listViewJoinRelation.setName("process");
      processInstanceListViewEntity.setJoin(listViewJoinRelation);
      return getUpdateRequest(processInstanceListViewEntity);
    } else {
      return null;
    }
  }

  private IndexRequest getUpdateRequest(
      final ProcessInstanceListViewEntity processInstanceListViewEntity)
      throws PersistenceException {
    try {
      // Convert the entity to a JSON map using ObjectMapper
      final Map<String, Object> jsonMap =
          objectMapper.readValue(
              objectMapper.writeValueAsString(processInstanceListViewEntity), HashMap.class);

      final IndexRequest indexRequest =
          new IndexRequest()
              .index(tasklistListViewTemplate.getFullQualifiedName())
              .id(processInstanceListViewEntity.getId())
              .source(jsonMap, XContentType.JSON)
              .opType(DocWriteRequest.OpType.INDEX);

      return indexRequest;
    } catch (final IOException e) {
      throw new PersistenceException("Error preparing the query to index snapshot entity", e);
    }
  }
}
