/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.process.test.impl.runtime;

public class ContainerRuntimeEnvs {

  // Camunda
  public static final String CAMUNDA_ENV_SPRING_PROFILES_ACTIVE = "SPRING_PROFILES_ACTIVE";
  public static final String CAMUNDA_ENV_ZEEBE_ELASTICSEARCH_CLASSNAME =
      "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_CLASSNAME";
  public static final String CAMUNDA_ENV_ZEEBE_ELASTICSEARCH_ARGS_URL =
      "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_URL";
  public static final String CAMUNDA_ENV_ZEEBE_ELASTICSEARCH_ARGS_BULK_SIZE =
      "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_BULK_SIZE";
  public static final String CAMUNDA_ENV_ZEEBE_CLOCK_CONTROLLED = "ZEEBE_CLOCK_CONTROLLED";

  public static final String CAMUNDA_ENV_OPERATE_ZEEBE_GATEWAYADDRESS =
      "CAMUNDA_OPERATE_ZEEBE_GATEWAYADDRESS";
  public static final String CAMUNDA_ENV_OPERATE_ZEEBEELASTICSEARCH_URL =
      "CAMUNDA_OPERATE_ZEEBEELASTICSEARCH_URL";
  public static final String CAMUNDA_ENV_OPERATE_IMPORTER_READERBACKOFF =
      "CAMUNDA_OPERATE_IMPORTER_READERBACKOFF";
  public static final String CAMUNDA_ENV_OPERATE_CSRF_PREVENTION_ENABLED =
      "CAMUNDA_OPERATE_CSRF_PREVENTION_ENABLED";

  public static final String CAMUNDA_ENV_TASKLIST_ZEEBE_GATEWAYADDRESS =
      "CAMUNDA_TASKLIST_ZEEBE_GATEWAYADDRESS";
  public static final String CAMUNDA_ENV_TASKLIST_ZEEBE_RESTADDRESS =
      "CAMUNDA_TASKLIST_ZEEBE_RESTADDRESS";
  public static final String CAMUNDA_ENV_TASKLIST_ELASTICSEARCH_URL =
      "CAMUNDA_TASKLIST_ELASTICSEARCH_URL";
  public static final String CAMUNDA_ENV_TASKLIST_ZEEBEELASTICSEARCH_URL =
      "CAMUNDA_TASKLIST_ZEEBEELASTICSEARCH_URL";
  public static final String CAMUNDA_ENV_TASKLIST_CSRF_PREVENTION_ENABLED =
      "CAMUNDA_TASKLIST_CSRFPREVENTIONENABLED";
  public static final String CAMUNDA_ENV_TASKLIST_IMPORTER_READERBACKOFF =
      "CAMUNDA_TASKLIST_IMPORTER_READERBACKOFF";

  // Elasticsearch
  public static final String CAMUNDA_ENV_OPERATE_ELASTICSEARCH_URL =
      "CAMUNDA_OPERATE_ELASTICSEARCH_URL";
  public static final String ELASTICSEARCH_ENV_XPACK_SECURITY_ENABLED = "xpack.security.enabled";

  // Connectors
  public static final String CONNECTORS_ENV_ZEEBE_CLIENT_BROKER_GATEWAY_ADDRESS =
      "ZEEBE_CLIENT_BROKER_GATEWAY-ADDRESS";
  public static final String CONNECTORS_ENV_ZEEBE_CLIENT_SECURITY_PLAINTEXT =
      "ZEEBE_CLIENT_SECURITY_PLAINTEXT";
  public static final String CONNECTORS_ENV_CAMUNDA_OPERATE_CLIENT_URL =
      "CAMUNDA_OPERATE_CLIENT_URL";
  public static final String CONNECTORS_ENV_CAMUNDA_OPERATE_CLIENT_USERNAME =
      "CAMUNDA_OPERATE_CLIENT_USERNAME";
  public static final String CONNECTORS_ENV_CAMUNDA_OPERATE_CLIENT_PASSWORD =
      "CAMUNDA_OPERATE_CLIENT_PASSWORD";
}
