/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.schema.manager;

import static io.camunda.webapps.schema.descriptors.AbstractIndexDescriptor.formatIndexPrefix;
import static io.camunda.webapps.schema.descriptors.ComponentNames.TASK_LIST;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.os.RetryOpenSearchClient;
import io.camunda.tasklist.property.TasklistOpenSearchProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.IndexMapping;
import io.camunda.tasklist.schema.IndexMapping.IndexMappingProperty;
import io.camunda.tasklist.schema.indices.AbstractIndexDescriptor;
import io.camunda.tasklist.schema.indices.IndexDescriptor;
import io.camunda.tasklist.schema.templates.AbstractTemplateDescriptor;
import io.camunda.tasklist.schema.templates.TemplateDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.tasklist.TasklistIndexDescriptor;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.http.util.EntityUtils;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.jsonb.JsonbJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch.cluster.PutComponentTemplateRequest;
import org.opensearch.client.opensearch.indices.Alias;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.opensearch.client.opensearch.indices.PutIndexTemplateRequest;
import org.opensearch.client.opensearch.indices.PutIndexTemplateRequest.Builder;
import org.opensearch.client.opensearch.indices.PutMappingRequest;
import org.opensearch.client.opensearch.indices.put_index_template.IndexTemplateMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

@Component("tasklistSchemaManager")
@Profile("!test")
@Conditional(OpenSearchCondition.class)
public class OpenSearchSchemaManager implements SchemaManager {

  public static final String SETTINGS = "settings";
  public static final String MAPPINGS = "properties";
  public static final String TASKLIST_DELETE_ARCHIVED_INDICES = "tasklist_delete_archived_indices";
  private static final Logger LOGGER = LoggerFactory.getLogger(OpenSearchSchemaManager.class);

  @Autowired protected TasklistProperties tasklistProperties;

  @Autowired protected RetryOpenSearchClient retryOpenSearchClient;

  @Autowired
  @Qualifier("tasklistOsRestClient")
  private RestClient opensearchRestClient;

  @Autowired private List<AbstractIndexDescriptor> tasklistIndexDescriptors;

  @Autowired(required = false)
  private List<TasklistIndexDescriptor> commonIndexDescriptors;

  @Autowired private List<TemplateDescriptor> tasklistTemplateDescriptors;

  @Autowired(required = false)
  private List<IndexTemplateDescriptor> commonTemplateDescriptors;

  @Autowired
  @Qualifier("tasklistOsClient")
  private OpenSearchClient openSearchClient;

  @Autowired
  @Qualifier("tasklistObjectMapper")
  private ObjectMapper objectMapper;

  @Override
  public void createSchema() {
    if (tasklistProperties.getArchiver().isIlmEnabled()) {
      createIndexLifeCyclesIfNotExist();
    }
    createDefaults();
    createTemplates();
    createIndices();
  }

  @Override
  public IndexMapping getExpectedIndexFields(final IndexDescriptor indexDescriptor) {
    final InputStream description =
        OpenSearchSchemaManager.class.getResourceAsStream(
            indexDescriptor.getSchemaClasspathFilename());
    try {
      final String currentVersionSchema =
          StreamUtils.copyToString(description, StandardCharsets.UTF_8);
      final TypeReference<HashMap<String, Object>> type = new TypeReference<>() {};
      final Map<String, Object> mappings =
          (Map<String, Object>) objectMapper.readValue(currentVersionSchema, type).get("mappings");
      final Map<String, Object> properties = (Map<String, Object>) mappings.get("properties");
      final String dynamic = (String) mappings.get("dynamic");
      return new IndexMapping()
          .setIndexName(indexDescriptor.getIndexName())
          .setDynamic(dynamic)
          .setProperties(
              properties.entrySet().stream()
                  .map(
                      entry ->
                          new IndexMappingProperty()
                              .setName(entry.getKey())
                              .setTypeDefinition(entry.getValue()))
                  .collect(Collectors.toSet()));
    } catch (final IOException e) {
      throw new TasklistRuntimeException(e);
    }
  }

  @Override
  public Map<String, IndexMapping> getIndexMappings(final String indexNamePattern)
      throws IOException {
    final Map<String, IndexMapping> mappings = new HashMap<>();

    final Request request = new Request("GET", "/" + indexNamePattern + "/_mapping/");
    final Response response = opensearchRestClient.performRequest(request);
    final String responseBody = EntityUtils.toString(response.getEntity());

    // Initialize ObjectMapper instance
    final ObjectMapper objectMapper = new ObjectMapper();

    // Parse the JSON response body
    final Map<String, Map<String, Map<String, Object>>> parsedResponse =
        objectMapper.readValue(responseBody, new TypeReference<>() {});

    // Iterate over the parsed JSON to build the mappings
    for (final Map.Entry<String, Map<String, Map<String, Object>>> indexEntry :
        parsedResponse.entrySet()) {
      final String indexName = indexEntry.getKey();
      final Map<String, Object> indexMappingData = indexEntry.getValue().get("mappings");
      final String dynamicSetting = (String) indexMappingData.get("dynamic");

      // Extract the properties
      final Map<String, Object> propertiesData =
          (Map<String, Object>) indexMappingData.get("properties");
      final Set<IndexMapping.IndexMappingProperty> propertiesSet = new HashSet<>();

      for (final Map.Entry<String, Object> propertyEntry : propertiesData.entrySet()) {
        final IndexMapping.IndexMappingProperty property =
            new IndexMapping.IndexMappingProperty()
                .setName(propertyEntry.getKey())
                .setTypeDefinition(propertyEntry.getValue());
        propertiesSet.add(property);
      }

      // Create IndexMapping object
      final IndexMapping indexMapping =
          new IndexMapping()
              .setIndexName(indexName)
              .setDynamic(dynamicSetting)
              .setProperties(propertiesSet);

      // Add to mappings map
      mappings.put(indexName, indexMapping);
    }

    return mappings;
  }

  @Override
  public String getIndexPrefix() {
    return tasklistProperties.getOpenSearch().getIndexPrefix();
  }

  @Override
  public void updateSchema(final Map<IndexDescriptor, Set<IndexMappingProperty>> newFields) {
    for (final Map.Entry<IndexDescriptor, Set<IndexMappingProperty>> indexNewFields :
        newFields.entrySet()) {
      if (indexNewFields.getKey() instanceof TemplateDescriptor) {
        LOGGER.info(
            "Update template: " + ((TemplateDescriptor) indexNewFields.getKey()).getTemplateName());
        final TemplateDescriptor templateDescriptor = (TemplateDescriptor) indexNewFields.getKey();
        final String json = readTemplateJson(templateDescriptor.getSchemaClasspathFilename());
        final PutIndexTemplateRequest indexTemplateRequest =
            prepareIndexTemplateRequest(templateDescriptor, json);
        putIndexTemplate(indexTemplateRequest);
      }

      final Map<String, Property> properties;
      try (final JsonParser jsonParser =
          JsonProvider.provider()
              .createParser(
                  new StringReader(
                      IndexMappingProperty.toJsonString(
                          indexNewFields.getValue(), objectMapper)))) {
        final JsonpMapper jsonpMapper = openSearchClient._transport().jsonpMapper();
        properties =
            JsonpDeserializer.stringMapDeserializer(Property._DESERIALIZER)
                .deserialize(jsonParser, jsonpMapper);
      }
      final PutMappingRequest request =
          new PutMappingRequest.Builder()
              .index(indexNewFields.getKey().getAlias())
              .properties(properties)
              .build();
      LOGGER.info(
          String.format(
              "Index alias: %s. New fields will be added: %s",
              indexNewFields.getKey().getAlias(), indexNewFields.getValue()));
      retryOpenSearchClient.putMapping(request);
    }
  }

  @Override
  public void createIndex(final IndexDescriptor indexDescriptor) {
    final String indexFilename = indexDescriptor.getSchemaClasspathFilename();
    final InputStream indexDescription =
        OpenSearchSchemaManager.class.getResourceAsStream(indexFilename);

    final JsonpMapper mapper = openSearchClient._transport().jsonpMapper();
    final JsonParser parser = mapper.jsonProvider().createParser(indexDescription);

    final CreateIndexRequest request =
        new CreateIndexRequest.Builder()
            .mappings(IndexTemplateMapping._DESERIALIZER.deserialize(parser, mapper).mappings())
            .aliases(indexDescriptor.getAlias(), new Alias.Builder().isWriteIndex(false).build())
            .settings(getIndexSettings())
            .index(indexDescriptor.getFullQualifiedName())
            .build();

    createIndex(request, indexDescriptor.getFullQualifiedName());
  }

  private PutIndexTemplateRequest prepareIndexTemplateRequest(
      final TemplateDescriptor templateDescriptor, final String json) {
    final var templateSettings = templateSettings(templateDescriptor);
    final var templateBuilder =
        new IndexTemplateMapping.Builder()
            .aliases(templateDescriptor.getAlias(), new Alias.Builder().build());

    try {

      final var indexAsJSONNode = objectMapper.readTree(new StringReader(json));

      final var customSettings = getCustomSettings(templateSettings, indexAsJSONNode);
      final var mappings = getMappings(indexAsJSONNode.get(MAPPINGS));

      final IndexTemplateMapping template =
          templateBuilder.mappings(mappings).settings(customSettings).build();

      final PutIndexTemplateRequest request =
          new Builder()
              .name(templateDescriptor.getTemplateName())
              .indexPatterns(templateDescriptor.getIndexPattern())
              .template(template)
              .composedOf(settingsTemplateName())
              .build();
      return request;
    } catch (final Exception ex) {
      throw new TasklistRuntimeException(ex);
    }
  }

  private TypeMapping getMappings(final JsonNode mappingsAsJSON) {
    final JsonbJsonpMapper jsonpMapper = new JsonbJsonpMapper();
    final JsonParser jsonParser =
        JsonProvider.provider().createParser(new StringReader(mappingsAsJSON.toPrettyString()));
    return TypeMapping._DESERIALIZER.deserialize(jsonParser, jsonpMapper);
  }

  public void createIndexLifeCyclesIfNotExist() {
    if (retryOpenSearchClient.getLifecyclePolicy(TASKLIST_DELETE_ARCHIVED_INDICES).isPresent()) {
      LOGGER.info("{} ISM policy already exists", TASKLIST_DELETE_ARCHIVED_INDICES);
      return;
    }
    LOGGER.info("Creating ISM Policy for deleting archived indices");

    final Request request =
        new Request("PUT", "/_plugins/_ism/policies/" + TASKLIST_DELETE_ARCHIVED_INDICES);

    final JsonObject deleteJson =
        Json.createObjectBuilder().add("delete", Json.createObjectBuilder().build()).build();
    final JsonArray actionsDelete = Json.createArrayBuilder().add(deleteJson).build();
    final JsonObject deleteState =
        Json.createObjectBuilder()
            .add("name", Json.createValue("delete"))
            .add("actions", actionsDelete)
            .build();
    final JsonObject openCondition =
        Json.createObjectBuilder()
            .add(
                "min_index_age",
                Json.createValue(
                    tasklistProperties.getArchiver().getIlmMinAgeForDeleteArchivedIndices()))
            .build();
    final JsonObject openTransition =
        Json.createObjectBuilder()
            .add("state_name", Json.createValue("delete"))
            .add("conditions", openCondition)
            .build();
    final JsonArray transitionOpenActions = Json.createArrayBuilder().add(openTransition).build();
    final JsonObject openActionJson =
        Json.createObjectBuilder().add("open", Json.createObjectBuilder().build()).build();
    final JsonArray openActions = Json.createArrayBuilder().add(openActionJson).build();
    final JsonObject openState =
        Json.createObjectBuilder()
            .add("name", Json.createValue("open"))
            .add("actions", openActions)
            .add("transitions", transitionOpenActions)
            .build();
    final JsonArray statesJson = Json.createArrayBuilder().add(openState).add(deleteState).build();
    final JsonObject policyJson =
        Json.createObjectBuilder()
            .add("policy_id", Json.createValue(TASKLIST_DELETE_ARCHIVED_INDICES))
            .add(
                "description",
                Json.createValue("Policy to delete archived indices older than configuration"))
            .add("default_state", Json.createValue("open"))
            .add("states", statesJson)
            .build();
    final JsonObject requestJson = Json.createObjectBuilder().add("policy", policyJson).build();

    request.setJsonEntity(requestJson.toString());
    try {
      final Response response = opensearchRestClient.performRequest(request);
    } catch (final IOException e) {
      throw new TasklistRuntimeException(e);
    }
  }

  private void createDefaults() {
    final TasklistOpenSearchProperties elsConfig = tasklistProperties.getOpenSearch();

    final String settingsTemplateName = settingsTemplateName();
    LOGGER.info(
        "Create default settings '{}' with {} shards and {} replicas per index.",
        settingsTemplateName,
        elsConfig.getNumberOfShards(),
        elsConfig.getNumberOfReplicas());

    final IndexSettings settings = getIndexSettings();
    retryOpenSearchClient.createComponentTemplate(
        new PutComponentTemplateRequest.Builder()
            .name(settingsTemplateName)
            // .settings(settings)
            .template(t -> t.settings(settings))
            .build());
  }

  private IndexSettings getIndexSettings() {
    final TasklistOpenSearchProperties osConfig = tasklistProperties.getOpenSearch();
    return new IndexSettings.Builder()
        .numberOfShards(String.valueOf(osConfig.getNumberOfShards()))
        .numberOfReplicas(String.valueOf(osConfig.getNumberOfReplicas()))
        .build();
  }

  private String settingsTemplateName() {
    final TasklistOpenSearchProperties osConfig = tasklistProperties.getOpenSearch();
    return String.format("%s%s_template", formatIndexPrefix(osConfig.getIndexPrefix()), TASK_LIST);
  }

  private void createTemplates() {
    tasklistTemplateDescriptors.forEach(this::createTemplate);
    // Note: While migrating the entities and index descriptors
    // to the harmonized webapps-schema module, this intermediate
    // HACK is required to ensure that the necessary templates are
    // created so that the integration tests can run.
    // Once all entities and index descriptors have been moved,
    // this code snippet will be deleted and adjusted as necessary!
    Optional.ofNullable(commonTemplateDescriptors)
        .ifPresent(
            l ->
                l.stream()
                    .map(
                        t ->
                            new AbstractTemplateDescriptor() {

                              @Override
                              public String getSchemaClasspathFilename() {
                                return t.getMappingsClasspathFilename();
                              }

                              @Override
                              protected String getIndexPrefix() {
                                return tasklistProperties.getOpenSearch().getIndexPrefix();
                              }

                              @Override
                              public String getIndexName() {
                                return t.getIndexName();
                              }

                              @Override
                              public String getAlias() {
                                return t.getAlias();
                              }

                              @Override
                              public String getTemplateName() {
                                return t.getTemplateName();
                              }

                              @Override
                              public String getIndexPattern() {
                                return t.getIndexPattern();
                              }

                              @Override
                              public String getVersion() {
                                return t.getVersion();
                              }

                              @Override
                              public String getFullQualifiedName() {
                                return t.getFullQualifiedName();
                              }
                            })
                    .forEach(this::createTemplate));
  }

  private void createTemplate(final TemplateDescriptor templateDescriptor) {
    final IndexTemplateMapping template = getTemplateFrom(templateDescriptor);

    putIndexTemplate(
        new PutIndexTemplateRequest.Builder()
            .indexPatterns(List.of(templateDescriptor.getIndexPattern()))
            .template(template)
            .name(templateDescriptor.getTemplateName())
            .composedOf(List.of(settingsTemplateName()))
            .build());

    // This is necessary, otherwise tasklist won't find indexes at startup
    final String indexName = templateDescriptor.getFullQualifiedName();
    createIndex(new CreateIndexRequest.Builder().index(indexName).build(), indexName);
  }

  private void putIndexTemplate(final PutIndexTemplateRequest request, final boolean overwrite) {
    final boolean created = retryOpenSearchClient.createTemplate(request, overwrite);
    if (created) {
      LOGGER.debug("Template [{}] was successfully created", request.name());
    } else {
      LOGGER.debug("Template [{}] was NOT created", request.name());
    }
  }

  private void putIndexTemplate(final PutIndexTemplateRequest request) {
    final boolean created = retryOpenSearchClient.createTemplate(request);
    if (created) {
      LOGGER.debug("Template [{}] was successfully created", request.name());
    } else {
      LOGGER.debug("Template [{}] was NOT created", request.name());
    }
  }

  private IndexTemplateMapping getTemplateFrom(final TemplateDescriptor templateDescriptor) {
    final String templateFilename = templateDescriptor.getSchemaClasspathFilename();

    final InputStream templateConfig =
        OpenSearchSchemaManager.class.getResourceAsStream(templateFilename);

    final JsonpMapper mapper = openSearchClient._transport().jsonpMapper();
    final JsonParser parser = mapper.jsonProvider().createParser(templateConfig);

    return new IndexTemplateMapping.Builder()
        .mappings(IndexTemplateMapping._DESERIALIZER.deserialize(parser, mapper).mappings())
        .aliases(templateDescriptor.getAlias(), new Alias.Builder().build())
        .build();
  }

  private InputStream readJSONFile(final String filename) {
    final Map<String, Object> result;
    try (final InputStream inputStream =
        OpenSearchSchemaManager.class.getResourceAsStream(filename)) {
      if (inputStream != null) {
        return inputStream;
      } else {
        throw new TasklistRuntimeException("Failed to find " + filename + " in classpath ");
      }
    } catch (final IOException e) {
      throw new TasklistRuntimeException("Failed to load file " + filename + " from classpath ", e);
    }
  }

  private void createIndex(final CreateIndexRequest createIndexRequest, final String indexName) {
    final boolean created = retryOpenSearchClient.createIndex(createIndexRequest);
    if (created) {
      LOGGER.debug("Index [{}] was successfully created", indexName);
    } else {
      LOGGER.debug("Index [{}] was NOT created", indexName);
    }
  }

  private void createIndices() {
    tasklistIndexDescriptors.forEach(this::createIndex);
    // Note: While migrating the entities and index descriptors
    // to the harmonized webapps-schema module, this intermediate
    // HACK is required to ensure that the necessary templates are
    // created so that the integration tests can run.
    // Once all entities and index descriptors have been moved,
    // this code snippet will be deleted and adjusted as necessary!
    Optional.ofNullable(commonIndexDescriptors)
        .ifPresent(
            l ->
                l.stream()
                    .map(
                        i ->
                            new AbstractIndexDescriptor() {

                              @Override
                              public String getIndexName() {
                                return i.getIndexName();
                              }

                              @Override
                              public String getAlias() {
                                return i.getAlias();
                              }

                              @Override
                              public String getFullQualifiedName() {
                                return i.getFullQualifiedName();
                              }

                              @Override
                              public String getSchemaClasspathFilename() {
                                return i.getMappingsClasspathFilename();
                              }

                              @Override
                              protected String getIndexPrefix() {
                                return tasklistProperties.getOpenSearch().getIndexPrefix();
                              }

                              @Override
                              public String getVersion() {
                                return i.getVersion();
                              }
                            })
                    .forEach(this::createIndex));
  }

  private IndexSettings templateSettings(final TemplateDescriptor indexDescriptor) {
    final var shards =
        tasklistProperties
            .getOpenSearch()
            .getNumberOfShardsPerIndex()
            .get(indexDescriptor.getIndexName());

    final var replicas =
        tasklistProperties
            .getOpenSearch()
            .getNumberOfReplicasPerIndex()
            .get(indexDescriptor.getIndexName());

    if (shards != null || replicas != null) {
      final var indexSettingsBuilder = new IndexSettings.Builder();

      if (shards != null) {
        indexSettingsBuilder.numberOfShards(shards.toString());
      }

      if (replicas != null) {
        indexSettingsBuilder.numberOfReplicas(replicas.toString());
      }

      return indexSettingsBuilder.build();
    }
    return null;
  }

  private IndexSettings getCustomSettings(
      final IndexSettings defaultSettings, final JsonNode indexAsJSONNode) {
    final JsonbJsonpMapper jsonpMapper = new JsonbJsonpMapper();
    if (indexAsJSONNode.has(SETTINGS)) {
      final var settingsJSON = indexAsJSONNode.get(SETTINGS);
      final JsonParser jsonParser =
          JsonProvider.provider().createParser(new StringReader(settingsJSON.toPrettyString()));
      final var updatedSettings = IndexSettings._DESERIALIZER.deserialize(jsonParser, jsonpMapper);
      return new IndexSettings.Builder()
          .index(defaultSettings)
          .analysis(updatedSettings.analysis())
          .build();
    }
    return defaultSettings;
  }

  private static String readTemplateJson(final String classPathResourceName) {
    try {
      // read settings and mappings
      final InputStream description =
          OpenSearchSchemaManager.class.getResourceAsStream(classPathResourceName);
      final String json = StreamUtils.copyToString(description, StandardCharsets.UTF_8);
      return json;
    } catch (final Exception e) {
      throw new TasklistRuntimeException(
          "Exception occurred when reading template JSON: " + e.getMessage(), e);
    }
  }
}
