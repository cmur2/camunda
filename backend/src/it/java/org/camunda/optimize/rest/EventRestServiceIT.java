/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import com.google.common.collect.ImmutableMap;
import lombok.SneakyThrows;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.query.event.EventCountDto;
import org.camunda.optimize.dto.optimize.query.event.EventCountRequestDto;
import org.camunda.optimize.dto.optimize.query.event.EventCountSuggestionsRequestDto;
import org.camunda.optimize.dto.optimize.query.event.EventDto;
import org.camunda.optimize.dto.optimize.query.event.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.EventTypeDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

public class EventRestServiceIT extends AbstractIT {

  private static final String START_EVENT_ID = "startEventID";
  private static final String FIRST_TASK_ID = "taskID_1";
  private static final String SECOND_TASK_ID = "taskID_2";
  private static final String THIRD_TASK_ID = "taskID_3";
  private static final String FOURTH_TASK_ID = "taskID_4";
  private static final String END_EVENT_ID = "endEventID";

  private EventDto backendKetchupEvent = createEventDtoWithProperties("backend", "ketchup", "signup-event");
  private EventDto frontendMayoEvent = createEventDtoWithProperties("frontend", "mayonnaise", "registered_event");
  private EventDto managementBbqEvent = createEventDtoWithProperties("management", "BBQ_sauce", "onboarded_event");
  private EventDto ketchupMayoEvent = createEventDtoWithProperties("ketchup", "mayonnaise", "blacklisted_event");
  private EventDto backendMayoEvent = createEventDtoWithProperties("BACKEND", "mayonnaise", "ketchupevent");
  private EventDto nullNullEvent = createEventDtoWithProperties(null, null, "ketchupevent");

  private final List<EventDto> eventTraceOne = createTraceFromEventList(
    "traceIdOne", Arrays.asList(backendKetchupEvent, frontendMayoEvent, managementBbqEvent,
                                ketchupMayoEvent, backendMayoEvent, nullNullEvent));
  private final List<EventDto> eventTraceTwo = createTraceFromEventList(
    "traceIdTwo", Arrays.asList(backendKetchupEvent, frontendMayoEvent, ketchupMayoEvent, backendMayoEvent, nullNullEvent));
  private final List<EventDto> eventTraceThree = createTraceFromEventList(
    "traceIdThree", Arrays.asList(backendKetchupEvent, backendMayoEvent));
  private final List<EventDto> eventTraceFour = createTraceFromEventList(
    "traceIdFour", Collections.singletonList(backendKetchupEvent));

  private final List<EventDto> allEventDtos =
    Stream.of(eventTraceOne, eventTraceTwo, eventTraceThree, eventTraceFour)
      .flatMap(Collection::stream)
      .collect(toList());

  private static String simpleDiagramXml;

  @BeforeAll
  public static void setup() {
    simpleDiagramXml = createProcessDefinitionXml();
  }

  @BeforeEach
  public void init() {
    eventClient.ingestEventBatch(allEventDtos);
    eventClient.processEventTracesAndStates();
  }

  @Test
  public void getEventCounts_usingGETendpoint() {
    // when
    List<EventCountDto> eventCountDtos = createGetEventCountsQueryWithRequestParameters(new EventCountRequestDto())
      .executeAndReturnList(EventCountDto.class, 200);

    // then all events are sorted using default group case-insensitive ordering
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(6)
      .containsExactly(
        toEventCountDto(nullNullEvent, 2L, false),
        toEventCountDto(backendKetchupEvent, 4L, false),
        toEventCountDto(backendMayoEvent, 3L, false),
        toEventCountDto(frontendMayoEvent, 2L, false),
        toEventCountDto(ketchupMayoEvent, 2L, false),
        toEventCountDto(managementBbqEvent, 1L, false)
      );
  }

  @Test
  public void getEventCounts_usingGETendpoint_usingSearchTermAndSortAndOrderParameters() {
    // given
    EventCountRequestDto eventCountRequestDto = EventCountRequestDto.builder()
      .searchTerm("etch")
      .orderBy("eventName")
      .sortOrder(SortOrder.DESC)
      .build();

    // when
    List<EventCountDto> eventCountDtos = createGetEventCountsQueryWithRequestParameters(eventCountRequestDto)
      .executeAndReturnList(EventCountDto.class, 200);

    // then matching events are returned with ordering parameters respected
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(4)
      .containsExactly(
        toEventCountDto(backendKetchupEvent, 4L, false),
        toEventCountDto(backendMayoEvent, 3L, false),
        toEventCountDto(nullNullEvent, 2L, false),
        toEventCountDto(ketchupMayoEvent, 2L, false)
      );
  }

  @Test
  public void getEventCounts() {
    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsQueryWithRequestParameters(new EventCountRequestDto())
      .executeAndReturnList(EventCountDto.class, 200);

    // then all events are sorted using default group case-insensitive ordering
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(6)
      .containsExactly(
        toEventCountDto(nullNullEvent, 2L, false),
        toEventCountDto(backendKetchupEvent, 4L, false),
        toEventCountDto(backendMayoEvent, 3L, false),
        toEventCountDto(frontendMayoEvent, 2L, false),
        toEventCountDto(ketchupMayoEvent, 2L, false),
        toEventCountDto(managementBbqEvent, 1L, false)
      );
  }

  @Test
  public void getEventCounts_noAuthentication() {
    // when
    Response response = createPostEventCountsQueryWithRequestParameters(new EventCountRequestDto())
      .withoutAuthentication()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  public void getEventCounts_usingSearchTerm() {
    // given
    EventCountRequestDto eventCountRequestDto = EventCountRequestDto.builder().searchTerm("etch").build();

    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsQueryWithRequestParameters(eventCountRequestDto)
      .executeAndReturnList(EventCountDto.class, 200);

    // then matching event counts are return using default group case-insensitive ordering
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(4)
      .containsExactly(
        toEventCountDto(nullNullEvent, 2L, false),
        toEventCountDto(backendKetchupEvent, 4L, false),
        toEventCountDto(backendMayoEvent, 3L, false),
        toEventCountDto(ketchupMayoEvent, 2L, false)
      );
  }

  @ParameterizedTest(name = "exact or prefix match are returned with search term {0}")
  @ValueSource(strings = {"registered_ev", "registered_event", "regISTERED_event"})
  public void getEventCounts_usingSearchTermLongerThanNGramMax(String searchTerm) {
    // given
    EventCountRequestDto eventCountRequestDto = EventCountRequestDto.builder().searchTerm(searchTerm).build();

    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsQueryWithRequestParameters(eventCountRequestDto)
      .executeAndReturnList(EventCountDto.class, 200);

    // then matching event counts are return using default group case-insensitive ordering
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(1)
      .containsExactly(toEventCountDto(frontendMayoEvent, 2L, false));
  }

  @Test
  public void getEventCounts_usingSortAndOrderParameters() {
    // given
    EventCountRequestDto eventCountRequestDto = EventCountRequestDto.builder()
      .orderBy("source")
      .sortOrder(SortOrder.DESC)
      .build();

    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsQueryWithRequestParameters(eventCountRequestDto)
      .executeAndReturnList(EventCountDto.class, 200);

    // then all matching event counts are return using sort and ordering provided
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(6)
      .containsExactly(
        toEventCountDto(ketchupMayoEvent, 2L, false),
        toEventCountDto(frontendMayoEvent, 2L, false),
        toEventCountDto(backendMayoEvent, 3L, false),
        toEventCountDto(backendKetchupEvent, 4L, false),
        toEventCountDto(managementBbqEvent, 1L, false),
        toEventCountDto(nullNullEvent, 2L, false)
      );
  }

  @Test
  public void getEventCounts_usingSortAndOrderParametersMatchingDefault() {
    // given
    EventCountRequestDto eventCountRequestDto = EventCountRequestDto.builder()
      .orderBy("group")
      .sortOrder(SortOrder.ASC)
      .build();

    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsQueryWithRequestParameters(eventCountRequestDto)
      .executeAndReturnList(EventCountDto.class, 200);

    // then all matching event counts are return using sort and ordering provided
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(6)
      .containsExactly(
        toEventCountDto(nullNullEvent, 2L, false),
        toEventCountDto(backendKetchupEvent, 4L, false),
        toEventCountDto(backendMayoEvent, 3L, false),
        toEventCountDto(frontendMayoEvent, 2L, false),
        toEventCountDto(ketchupMayoEvent, 2L, false),
        toEventCountDto(managementBbqEvent, 1L, false)
      );
  }

  @Test
  public void getEventCounts_usingInvalidSortAndOrderParameters() {
    // given
    EventCountRequestDto eventCountRequestDto = EventCountRequestDto.builder()
      .orderBy("notAField")
      .build();

    // when
    Response response = createPostEventCountsQueryWithRequestParameters(eventCountRequestDto).execute();

    // then validation exception is thrown
    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  public void getEventCounts_usingSearchTermAndSortAndOrderParameters() {
    // given
    EventCountRequestDto eventCountRequestDto = EventCountRequestDto.builder()
      .searchTerm("etch")
      .orderBy("eventName")
      .sortOrder(SortOrder.DESC)
      .build();

    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsQueryWithRequestParameters(eventCountRequestDto)
      .executeAndReturnList(EventCountDto.class, 200);

    // then matching events are returned with ordering parameters respected
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(4)
      .containsExactly(
        toEventCountDto(backendKetchupEvent, 4L, false),
        toEventCountDto(backendMayoEvent, 3L, false),
        toEventCountDto(nullNullEvent, 2L, false),
        toEventCountDto(ketchupMayoEvent, 2L, false)
      );
  }

  @Test
  public void getEventCounts_withSuggestionsForValidTargetNodeAndRelevantMappingsExist() {
    // given
    EventTypeDto previousMappedEvent = eventTypeFromEvent(backendKetchupEvent);
    EventTypeDto nextMappedEvent = eventTypeFromEvent(ketchupMayoEvent);

    // Suggestions request for flow node with event mapped before and after
    EventCountSuggestionsRequestDto eventCountSuggestionsRequestDto = EventCountSuggestionsRequestDto.builder()
      .targetFlowNodeId(SECOND_TASK_ID)
      .xml(simpleDiagramXml)
      .mappings(ImmutableMap.of(
        FIRST_TASK_ID, createEventMappingDto(null, previousMappedEvent),
        THIRD_TASK_ID, createEventMappingDto(nextMappedEvent, null)
      ))
      .build();

    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsQueryWithSuggestionsParameters(
      eventCountSuggestionsRequestDto)
      .executeAndReturnList(EventCountDto.class, 200);

    // then events that are sequenced with mapped events are first and marked as suggested
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(4)
      .containsExactly(
        toEventCountDto(backendMayoEvent, 3L, true),
        toEventCountDto(frontendMayoEvent, 2L, true),
        toEventCountDto(managementBbqEvent, 1L, true),
        toEventCountDto(nullNullEvent, 2L, false)
      );
  }

  @Test
  public void getEventCounts_withSuggestionsForValidTargetNodeAndRelevantMappingsExistWithNullFields() {
    // given
    EventTypeDto nextMappedEventWithNullProperties = eventTypeFromEvent(nullNullEvent);

    // Suggestions request for flow node with event mapped before and after
    EventCountSuggestionsRequestDto eventCountSuggestionsRequestDto = EventCountSuggestionsRequestDto.builder()
      .targetFlowNodeId(SECOND_TASK_ID)
      .xml(simpleDiagramXml)
      .mappings(ImmutableMap.of(THIRD_TASK_ID, createEventMappingDto(null, nextMappedEventWithNullProperties)))
      .build();

    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsQueryWithSuggestionsParameters(
      eventCountSuggestionsRequestDto)
      .executeAndReturnList(EventCountDto.class, 200);

    // then events that are sequenced with mapped events are first and marked as suggested
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(5)
      .containsExactly(
        toEventCountDto(backendMayoEvent, 3L, true),
        toEventCountDto(backendKetchupEvent, 4L, false),
        toEventCountDto(frontendMayoEvent, 2L, false),
        toEventCountDto(ketchupMayoEvent, 2L, false),
        toEventCountDto(managementBbqEvent, 1L, false)
      );
  }

  @Test
  public void getEventCounts_withSuggestionsForValidTargetNodeAndRelevantMappingsExist_onlyNearestNeighboursConsidered() {
    // given
    EventTypeDto nearestNextMappedEvent = eventTypeFromEvent(ketchupMayoEvent);
    EventTypeDto furthestNextMappedEvent = eventTypeFromEvent(backendMayoEvent);

    // Suggestions request for flow node with events mapped after in two nearest neighbours
    EventCountSuggestionsRequestDto eventCountSuggestionsRequestDto = EventCountSuggestionsRequestDto.builder()
      .targetFlowNodeId(FIRST_TASK_ID)
      .xml(simpleDiagramXml)
      .mappings(ImmutableMap.of(
        SECOND_TASK_ID, createEventMappingDto(nearestNextMappedEvent, null),
        THIRD_TASK_ID, createEventMappingDto(furthestNextMappedEvent, null)
      ))
      .build();

    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsQueryWithSuggestionsParameters(
      eventCountSuggestionsRequestDto)
      .executeAndReturnList(EventCountDto.class, 200);

    // then only the event in sequence before closest neighbour is suggested, non-suggestions use default ordering
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(4)
      .containsExactly(
        toEventCountDto(frontendMayoEvent, 2L, true),
        toEventCountDto(managementBbqEvent, 1L, true),
        toEventCountDto(nullNullEvent, 2L, false),
        toEventCountDto(backendKetchupEvent, 4L, false)
      );
  }

  @Test
  public void getEventCounts_withSuggestionsForValidTargetNodeAndRelevantMappingsExist_alreadyMappedEventsAreOmitted() {
    // given
    EventTypeDto nextMappedEvent = eventTypeFromEvent(nullNullEvent);
    EventTypeDto otherMappedEvent = eventTypeFromEvent(backendMayoEvent);

    // Suggestions request for flow node with event mapped after
    EventCountSuggestionsRequestDto eventCountSuggestionsRequestDto = EventCountSuggestionsRequestDto.builder()
      .targetFlowNodeId(SECOND_TASK_ID)
      .xml(simpleDiagramXml)
      .mappings(ImmutableMap.of(
        FIRST_TASK_ID, createEventMappingDto(otherMappedEvent, null),
        THIRD_TASK_ID, createEventMappingDto(nextMappedEvent, null)
      ))
      .build();

    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsQueryWithSuggestionsParameters(
      eventCountSuggestionsRequestDto)
      .executeAndReturnList(EventCountDto.class, 200);

    // then no suggestions returned as matching sequence event has already been mapped
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(4)
      .containsExactly(
        toEventCountDto(backendKetchupEvent, 4L, false),
        toEventCountDto(frontendMayoEvent, 2L, false),
        toEventCountDto(ketchupMayoEvent, 2L, false),
        toEventCountDto(managementBbqEvent, 1L, false)
      );
  }

  @Test
  public void getEventCounts_withSuggestionsForValidTargetNodeAndMappingsExist_usingCustomSorting() {
    // given
    EventTypeDto previousMappedEvent = eventTypeFromEvent(backendKetchupEvent);

    // Suggestions request for flow node with event mapped before
    EventCountSuggestionsRequestDto eventCountSuggestionsRequestDto = EventCountSuggestionsRequestDto.builder()
      .targetFlowNodeId(SECOND_TASK_ID)
      .xml(simpleDiagramXml)
      .mappings(ImmutableMap.of(FIRST_TASK_ID, createEventMappingDto(previousMappedEvent, null)))
      .build();
    EventCountRequestDto eventCountRequestDto = EventCountRequestDto.builder()
      .orderBy("source")
      .sortOrder(SortOrder.DESC)
      .build();

    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsQueryWithRequestAndSuggestionsParams(
      eventCountRequestDto, eventCountSuggestionsRequestDto)
      .executeAndReturnList(EventCountDto.class, 200);

    // then counts that are not suggestions respect custom ordering
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(5)
      .containsExactly(
        toEventCountDto(frontendMayoEvent, 2L, true),
        toEventCountDto(backendMayoEvent, 3L, true),
        toEventCountDto(ketchupMayoEvent, 2L, false),
        toEventCountDto(managementBbqEvent, 1L, false),
        toEventCountDto(nullNullEvent, 2L, false)
      );
  }

  @Test
  public void getEventCounts_withSuggestionsForValidTargetNodeAndRelevantMappingsExist_usingSearchTerm() {
    // given
    EventTypeDto previousMappedEvent = eventTypeFromEvent(backendKetchupEvent);

    // Suggestions request for flow node with event mapped before
    EventCountSuggestionsRequestDto eventCountSuggestionsRequestDto = EventCountSuggestionsRequestDto.builder()
      .targetFlowNodeId(SECOND_TASK_ID)
      .xml(simpleDiagramXml)
      .mappings(ImmutableMap.of(FIRST_TASK_ID, createEventMappingDto(previousMappedEvent, null)))
      .build();
    EventCountRequestDto eventCountRequestDto = EventCountRequestDto.builder().searchTerm("ayon").build();

    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsQueryWithRequestAndSuggestionsParams(
      eventCountRequestDto, eventCountSuggestionsRequestDto)
      .executeAndReturnList(EventCountDto.class, 200);

    // then only results matching search term are returned
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(3)
      .containsExactly(
        toEventCountDto(backendMayoEvent, 3L, true),
        toEventCountDto(frontendMayoEvent, 2L, true),
        toEventCountDto(ketchupMayoEvent, 2L, false)
      );
  }

  @Test
  public void getEventCounts_withSuggestionsForValidTargetNodeAndMappingsExist_searchTermDoesNotMatchSuggestions() {
    // given
    EventTypeDto previousMappedEvent = eventTypeFromEvent(backendKetchupEvent);

    // Suggestions request for flow node with event mapped before
    EventCountSuggestionsRequestDto eventCountSuggestionsRequestDto = EventCountSuggestionsRequestDto.builder()
      .targetFlowNodeId(SECOND_TASK_ID)
      .xml(simpleDiagramXml)
      .mappings(ImmutableMap.of(FIRST_TASK_ID, createEventMappingDto(previousMappedEvent, null)))
      .build();
    EventCountRequestDto eventCountRequestDto = EventCountRequestDto.builder().searchTerm("etch").build();

    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsQueryWithRequestAndSuggestionsParams(
      eventCountRequestDto, eventCountSuggestionsRequestDto)
      .executeAndReturnList(EventCountDto.class, 200);

    // then suggested and non-suggested counts are filtered out by search term
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(3)
      .containsExactly(
        toEventCountDto(backendMayoEvent, 3L, true),
        toEventCountDto(nullNullEvent, 2L, false),
        toEventCountDto(ketchupMayoEvent, 2L, false)
      );
  }

  @Test
  public void getEventCounts_withSuggestionsForValidTargetNodeNoMappingsExist() {
    // Suggestions request for flow node with event mapped after
    EventCountSuggestionsRequestDto eventCountSuggestionsRequestDto = EventCountSuggestionsRequestDto.builder()
      .targetFlowNodeId(SECOND_TASK_ID)
      .xml(simpleDiagramXml)
      .build();

    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsQueryWithSuggestionsParameters(
      eventCountSuggestionsRequestDto)
      .executeAndReturnList(EventCountDto.class, 200);

    // then all events are returned with no suggested using default group case-insensitive ordering
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(6)
      .containsExactly(
        toEventCountDto(nullNullEvent, 2L, false),
        toEventCountDto(backendKetchupEvent, 4L, false),
        toEventCountDto(backendMayoEvent, 3L, false),
        toEventCountDto(frontendMayoEvent, 2L, false),
        toEventCountDto(ketchupMayoEvent, 2L, false),
        toEventCountDto(managementBbqEvent, 1L, false)
      );
  }

  @Test
  public void getEventCounts_withSuggestionsForValidTargetNodeMappingsExist_mappingsGreaterThanConsideredDistance() {
    // given
    EventTypeDto previousMappedEvent = eventTypeFromEvent(backendKetchupEvent);

    // Suggestions request for flow node with event mapped before but greater than considered distance of 2
    EventCountSuggestionsRequestDto eventCountSuggestionsRequestDto = EventCountSuggestionsRequestDto.builder()
      .targetFlowNodeId(FOURTH_TASK_ID)
      .xml(simpleDiagramXml)
      .mappings(ImmutableMap.of(FIRST_TASK_ID, createEventMappingDto(previousMappedEvent, null)))
      .build();

    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsQueryWithSuggestionsParameters(
      eventCountSuggestionsRequestDto)
      .executeAndReturnList(EventCountDto.class, 200);

    // then all unmapped events are returned with no suggested using default group case-insensitive ordering
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(5)
      .containsExactly(
        toEventCountDto(nullNullEvent, 2L, false),
        toEventCountDto(backendMayoEvent, 3L, false),
        toEventCountDto(frontendMayoEvent, 2L, false),
        toEventCountDto(ketchupMayoEvent, 2L, false),
        toEventCountDto(managementBbqEvent, 1L, false)
        );
  }

  @Test
  public void getEventCounts_withSuggestionsForValidTargetNodeWithStartAndEndMappings_onlyClosestConsidered() {
    // given
    EventTypeDto previousMappedEndEvent = eventTypeFromEvent(backendKetchupEvent);
    EventTypeDto previousMappedStartEvent = eventTypeFromEvent(frontendMayoEvent);

    // Suggestions request for flow node with event mapped before as start and end event
    EventCountSuggestionsRequestDto eventCountSuggestionsRequestDto = EventCountSuggestionsRequestDto.builder()
      .targetFlowNodeId(THIRD_TASK_ID)
      .xml(simpleDiagramXml)
      .mappings(ImmutableMap.of(SECOND_TASK_ID, createEventMappingDto(previousMappedStartEvent, previousMappedEndEvent)))
      .build();

    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsQueryWithSuggestionsParameters(
      eventCountSuggestionsRequestDto)
      .executeAndReturnList(EventCountDto.class, 200);

    // then all unmapped events are returned and only event sequenced to the mapped end event is suggested
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(4)
      .containsExactly(
        toEventCountDto(backendMayoEvent, 3L, true),
        toEventCountDto(nullNullEvent, 2L, false),
        toEventCountDto(ketchupMayoEvent, 2L, false),
        toEventCountDto(managementBbqEvent, 1L, false)
        );
  }

  @Test
  public void getEventCounts_withSuggestionsForInvalidTargetNode() {
    // Suggestions request for flow node with ID that doesn't exist within xml
    EventCountSuggestionsRequestDto eventCountSuggestionsRequestDto = EventCountSuggestionsRequestDto.builder()
      .targetFlowNodeId("some_unknown_id")
      .xml(simpleDiagramXml)
      .mappings(ImmutableMap.of(FIRST_TASK_ID, createEventMappingDto(eventTypeFromEvent(backendKetchupEvent), null)))
      .build();

    // then the correct status code is returned
    createPostEventCountsQueryWithSuggestionsParameters(
      eventCountSuggestionsRequestDto)
      .executeAndReturnList(EventCountDto.class, 400);
  }

  @Test
  public void getEventCounts_withSuggestionsAndMappingsThatDoNotMatchXmlProvided() {
    // Suggestions request with mappings for node ID that doesn't exist within xml
    EventCountSuggestionsRequestDto eventCountSuggestionsRequestDto = EventCountSuggestionsRequestDto.builder()
      .targetFlowNodeId(SECOND_TASK_ID)
      .xml(simpleDiagramXml)
      .mappings(ImmutableMap.of("some_unknown_id", createEventMappingDto(eventTypeFromEvent(backendKetchupEvent), null)))
      .build();

    // then the correct status code is returned
    createPostEventCountsQueryWithSuggestionsParameters(
      eventCountSuggestionsRequestDto)
      .executeAndReturnList(EventCountDto.class, 400);
  }

  @ParameterizedTest(name = "event counts with suggestions is invalid with xml: {0}")
  @MethodSource("invalidParameters")
  public void getEventCounts_withSuggestionsAndInvalidXmlProvided(String xml) {
    // Suggestions request for node ID and no xml provided
    EventCountSuggestionsRequestDto eventCountSuggestionsRequestDto = EventCountSuggestionsRequestDto.builder()
      .targetFlowNodeId(SECOND_TASK_ID)
      .xml(xml)
      .mappings(Collections.emptyMap())
      .build();

    // then the correct status code is returned
    createPostEventCountsQueryWithSuggestionsParameters(
      eventCountSuggestionsRequestDto)
      .executeAndReturnList(EventCountDto.class, 400);
  }

  @ParameterizedTest(name = "event counts with suggestions is invalid with targetFlowNodeId: {0}")
  @MethodSource("invalidParameters")
  public void getEventCounts_withSuggestionsAndInvalidFlowNodeIdProvided(String flowNodeId) {
    // Suggestions request for invalid flowNodeId
    EventCountSuggestionsRequestDto eventCountSuggestionsRequestDto = EventCountSuggestionsRequestDto.builder()
      .targetFlowNodeId(flowNodeId)
      .xml(simpleDiagramXml)
      .mappings(Collections.emptyMap())
      .build();

    // then the correct status code is returned
    createPostEventCountsQueryWithSuggestionsParameters(
      eventCountSuggestionsRequestDto)
      .executeAndReturnList(EventCountDto.class, 400);
  }

  private static Stream<String> invalidParameters() {
    return Stream.of("", "   ", null);
  }

  private EventTypeDto eventTypeFromEvent(EventDto event) {
    return EventTypeDto.builder()
      .group(event.getGroup())
      .source(event.getSource())
      .eventName(event.getEventName())
      .build();
  }

  private EventCountDto toEventCountDto(EventDto event, Long count, boolean suggested) {
    return EventCountDto.builder()
      .group(event.getGroup())
      .source(event.getSource())
      .eventName(event.getEventName())
      .count(count)
      .suggested(suggested)
      .build();
  }

  private EventMappingDto createEventMappingDto(EventTypeDto startEventDto, EventTypeDto endEventDto) {
    return EventMappingDto.builder()
      .start(startEventDto)
      .end(endEventDto)
      .build();
  }

  private List<EventDto> createTraceFromEventList(String traceId, List<EventDto> events) {
    AtomicInteger incrementCounter= new AtomicInteger(0);
    long currentTimestamp = System.currentTimeMillis();
    return events.stream()
      .map(event -> createEventDtoWithProperties(event.getGroup(), event.getSource(), event.getEventName()))
      .peek(eventDto -> eventDto.setTraceId(traceId))
      .peek(eventDto -> eventDto.setTimestamp(currentTimestamp + (1000 * incrementCounter.getAndIncrement())))
      .collect(toList());
  }

  private EventDto createEventDtoWithProperties(final String group, final String source, final String eventName) {
    return eventClient.createEventDto()
      .toBuilder()
      .group(group)
      .source(source)
      .eventName(eventName)
      .build();
  }

  @SneakyThrows
  private static String createProcessDefinitionXml() {
    final BpmnModelInstance bpmnModel = Bpmn.createExecutableProcess("aProcess")
      .camundaVersionTag("aVersionTag")
      .name("aProcessName")
      .startEvent(START_EVENT_ID)
      .userTask(FIRST_TASK_ID)
      .userTask(SECOND_TASK_ID)
      .userTask(THIRD_TASK_ID)
      .userTask(FOURTH_TASK_ID)
      .endEvent(END_EVENT_ID)
      .done();
    final ByteArrayOutputStream xmlOutput = new ByteArrayOutputStream();
    Bpmn.writeModelToStream(xmlOutput, bpmnModel);
    return new String(xmlOutput.toByteArray(), StandardCharsets.UTF_8);
  }

  private OptimizeRequestExecutor createPostEventCountsQueryWithSuggestionsParameters(EventCountSuggestionsRequestDto eventCountSuggestionsRequestDto) {
    return createPostEventCountsQueryWithRequestAndSuggestionsParams(null, eventCountSuggestionsRequestDto);
  }

  private OptimizeRequestExecutor createPostEventCountsQueryWithRequestParameters(EventCountRequestDto eventCountRequestDto) {
    return createPostEventCountsQueryWithRequestAndSuggestionsParams(eventCountRequestDto, null);
  }

  private OptimizeRequestExecutor createPostEventCountsQueryWithRequestAndSuggestionsParams(EventCountRequestDto eventCountRequestDto,
                                                                                            EventCountSuggestionsRequestDto eventCountSuggestionsRequestDto) {
    return embeddedOptimizeExtension.getRequestExecutor()
      .buildPostEventCountRequest(eventCountRequestDto, eventCountSuggestionsRequestDto);
  }

  private OptimizeRequestExecutor createGetEventCountsQueryWithRequestParameters(EventCountRequestDto eventCountRequestDto) {
    return embeddedOptimizeExtension.getRequestExecutor()
      .buildGetEventCountRequest(eventCountRequestDto);
  }

}
