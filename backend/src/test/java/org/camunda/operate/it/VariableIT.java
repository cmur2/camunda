/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.it;

import java.util.List;
import java.util.function.Predicate;
import org.camunda.operate.entities.ActivityInstanceEntity;
import org.camunda.operate.es.reader.ActivityInstanceReader;
import org.camunda.operate.rest.dto.VariableDto;
import org.camunda.operate.util.MockMvcTestRule;
import org.camunda.operate.util.OperateZeebeIntegrationTest;
import org.camunda.operate.util.ConversionUtils;
import org.camunda.operate.util.ZeebeTestUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import io.zeebe.client.ZeebeClient;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.operate.rest.WorkflowInstanceRestService.WORKFLOW_INSTANCE_URL;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class VariableIT extends OperateZeebeIntegrationTest {

  @Autowired
  @Qualifier("activityIsActiveCheck")
  private Predicate<Object[]> activityIsActiveCheck;

  @Autowired
  @Qualifier("variableExistsCheck")
  private Predicate<Object[]> variableExistsCheck;
  
  @Autowired
  @Qualifier("variableEqualsCheck")
  private Predicate<Object[]> variableEqualsCheck;

  @Autowired
  private ActivityInstanceReader activityInstanceReader;

  @Rule
  public MockMvcTestRule mockMvcTestRule = new MockMvcTestRule();

  private ZeebeClient zeebeClient;

  private MockMvc mockMvc;

  @Before
  public void init() {
    super.before();
    zeebeClient = super.getClient();
    this.mockMvc = mockMvcTestRule.getMockMvc();
  }

  protected String getVariablesURL(String workflowInstanceId) {
    return String.format(WORKFLOW_INSTANCE_URL + "/%s/variables", workflowInstanceId);
  }

  protected String getVariablesURL(Long workflowInstanceId, Long scopeKey) {
    return String.format(WORKFLOW_INSTANCE_URL + "/%s/variables?scopeId=%s", workflowInstanceId, scopeKey);
  }

  @Test
  public void testVariablesLoaded() throws Exception {
    // having
    String processId = "demoProcess";
    BpmnModelInstance workflow = Bpmn.createExecutableProcess(processId)
      .startEvent("start")
        .serviceTask("task1").zeebeTaskType("task1")
        .subProcess("subProcess")
          .zeebeInput("var1", "subprocessVarIn")
        .embeddedSubProcess()
        .startEvent()
          .serviceTask("task2").zeebeTaskType("task2")
          .zeebeInput("subprocessVarIn", "taskVarIn")
          .zeebeOutput("taskVarOut", "varOut")
        .endEvent()
        .subProcessDone()
        .serviceTask("task3").zeebeTaskType("task3")
      .endEvent()
      .done();
    deployWorkflow(workflow, processId + ".bpmn");

    //TC 1 - when workflow instance is started
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"var1\": \"initialValue\", \"otherVar\": 123}");
    elasticsearchTestRule.processAllRecordsAndWait(activityIsActiveCheck, workflowInstanceKey, "task1");
    elasticsearchTestRule.processAllRecordsAndWait(variableExistsCheck, workflowInstanceKey, workflowInstanceKey, "otherVar");

    //then
    final Long workflowInstanceId = workflowInstanceKey;

    List<VariableDto> variables = getVariables(workflowInstanceId);
    assertThat(variables).hasSize(2);
    assertVariable(variables, "var1","\"initialValue\"");
    assertVariable(variables, "otherVar","123");

    //TC2 - when subprocess and task with input mapping are activated
    completeTask(workflowInstanceKey, "task1", null);
    elasticsearchTestRule.processAllRecordsAndWait(activityIsActiveCheck, workflowInstanceKey, "task2");
    elasticsearchTestRule.processAllRecordsAndWait(variableExistsCheck, workflowInstanceKey, workflowInstanceKey, "taskVarIn");


    //then
    variables = getVariables(workflowInstanceId,"subProcess");
    assertThat(variables).hasSize(1);
    assertVariable(variables, "subprocessVarIn","\"initialValue\"");

    variables = getVariables(workflowInstanceId,"task2");
    assertThat(variables).hasSize(1);
    assertVariable(variables, "taskVarIn","\"initialValue\"");

    //TC3 - when activity with output mapping is completed
    completeTask(workflowInstanceKey, "task2", "{\"taskVarOut\": \"someResult\", \"otherTaskVar\": 456}");
    elasticsearchTestRule.processAllRecordsAndWait(activityIsActiveCheck, workflowInstanceKey, "task3");
    elasticsearchTestRule.processAllRecordsAndWait(variableExistsCheck, workflowInstanceKey, workflowInstanceKey, "otherTaskVar");

    //then
    variables = getVariables(workflowInstanceId,"task2");
    assertThat(variables).hasSize(3);
    assertVariable(variables, "taskVarIn","\"initialValue\"");
    assertVariable(variables, "taskVarOut","\"someResult\"");
    assertVariable(variables, "otherTaskVar","456");
    variables = getVariables(workflowInstanceId,"subProcess");
    assertThat(variables).hasSize(1);
    assertVariable(variables, "subprocessVarIn","\"initialValue\"");
    variables = getVariables(workflowInstanceId);
    assertThat(variables).hasSize(3);
    assertVariable(variables, "var1","\"initialValue\"");
    assertVariable(variables, "varOut","\"someResult\"");
    assertVariable(variables, "otherVar","123");

    //TC4 - when variables are updated
    ZeebeTestUtil.updateVariables(zeebeClient, workflowInstanceId, "{\"var1\": \"updatedValue\" , \"newVar\": 555 }");
    //elasticsearchTestRule.processAllEvents(2, ImportValueType.VARIABLE);
    elasticsearchTestRule.processAllRecordsAndWait(variableEqualsCheck, workflowInstanceKey,workflowInstanceKey,"var1","\"updatedValue\"");
    elasticsearchTestRule.processAllRecordsAndWait(variableEqualsCheck, workflowInstanceKey,workflowInstanceKey,"newVar","555");
    
    variables = getVariables(workflowInstanceId);
    assertThat(variables).hasSize(4);
    assertVariable(variables, "var1","\"updatedValue\"");
    assertVariable(variables, "otherVar","123");
    assertVariable(variables, "varOut","\"someResult\"");
    assertVariable(variables, "newVar","555");

    //TC5 - when task is completed with new payload and workflow instance is finished
    completeTask(workflowInstanceKey, "task3", "{\"task3Completed\": true}");
    elasticsearchTestRule.processAllRecordsAndWait(variableExistsCheck, workflowInstanceKey, workflowInstanceKey, "task3Completed");

    //then
    variables = getVariables(workflowInstanceId);
    assertThat(variables).hasSize(5);
    assertVariable(variables, "var1","\"updatedValue\"");
    assertVariable(variables, "otherVar","123");
    assertVariable(variables, "varOut","\"someResult\"");
    assertVariable(variables, "newVar","555");
    assertVariable(variables, "task3Completed","true");

  }

  @Test
  public void testVariablesRequestFailOnEmptyScopeId() throws Exception {
    MvcResult mvcResult = mockMvc.perform(get(getVariablesURL("id")))
      .andExpect(status().isBadRequest())
      .andReturn();

    assertThat(mvcResult.getResolvedException().getMessage()).isEqualTo("Required String parameter 'scopeId' is not present");
  }

  private void assertVariable(List<VariableDto> variables, String name, String value) {
    assertThat(variables).filteredOn(v -> v.getName().equals(name)).hasSize(1)
      .allMatch(v -> v.getValue().equals(value));
  }

  protected List<VariableDto> getVariables(Long workflowInstanceId) throws Exception {
    MvcResult mvcResult = mockMvc
      .perform(get(getVariablesURL(workflowInstanceId, workflowInstanceId)))
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();
    return mockMvcTestRule.listFromResponse(mvcResult, VariableDto.class);
  }

  protected List<VariableDto> getVariables(Long workflowInstanceId, String activityId) throws Exception {
    final List<ActivityInstanceEntity> allActivityInstances = activityInstanceReader.getAllActivityInstances(workflowInstanceId);
    final Long task1Id = findActivityInstanceId(allActivityInstances, activityId);
    MvcResult mvcResult = mockMvc
      .perform(get(getVariablesURL(workflowInstanceId, task1Id)))
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();
    return mockMvcTestRule.listFromResponse(mvcResult, VariableDto.class);
  }

  protected Long findActivityInstanceId(List<ActivityInstanceEntity> allActivityInstances, String activityId) {
    assertThat(allActivityInstances).filteredOn(ai -> ai.getActivityId().equals(activityId)).hasSize(1);
    return Long.valueOf(allActivityInstances.stream().filter(ai -> ai.getActivityId().equals(activityId)).findFirst().get().getId());
  }

}
