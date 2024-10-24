/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors.usermanagement.index;

import io.camunda.webapps.schema.descriptors.usermanagement.RoleManagementIndexDescriptor;

public class RoleIndex extends RoleManagementIndexDescriptor {
  public static final String INDEX_NAME = "roles";
  public static final String INDEX_VERSION = "8.7.0";

  public static final String ROLEKEY = "roleKey";
  public static final String NAME = "name";
  public static final String ENTITYKEY = "entityKey";

  public RoleIndex(final String indexPrefix, final boolean isElasticsearch) {
    super(indexPrefix, isElasticsearch);
  }

  @Override
  public String getVersion() {
    return INDEX_VERSION;
  }

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }
}
