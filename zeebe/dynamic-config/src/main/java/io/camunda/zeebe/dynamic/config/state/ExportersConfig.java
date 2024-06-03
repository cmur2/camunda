/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import com.google.common.collect.ImmutableMap;
import io.camunda.zeebe.dynamic.config.state.ExporterState.State;
import java.util.Map;
import java.util.Optional;

/**
 * Represents configuration or state of exporting in a partition that can be updated during runtime.
 *
 * @param exporters the state of each exporter in this partition
 */
public record ExportersConfig(Map<String, ExporterState> exporters) {

  public static ExportersConfig empty() {
    return new ExportersConfig(Map.of());
  }

  private ExportersConfig updateExporter(
      final String exporterName, final ExporterState exporterState) {
    final var newExporters =
        ImmutableMap.<String, ExporterState>builder()
            .putAll(exporters)
            .put(exporterName, exporterState)
            .buildKeepingLast(); // choose last one if there are duplicate keys
    return new ExportersConfig(newExporters);
  }

  public ExportersConfig disableExporter(final String exporterName) {
    return updateExporter(
        exporterName,
        new ExporterState(
            exporters.get(exporterName).metadataVersion(),
            ExporterState.State.DISABLED,
            Optional.empty()));
  }

  public ExportersConfig enableExporter(final String exporterName, final String initializeFrom) {
    final var newMetadataVersion =
        exporters.containsKey(exporterName) ? exporters.get(exporterName).metadataVersion() + 1 : 1;
    return updateExporter(
        exporterName,
        new ExporterState(newMetadataVersion, State.ENABLED, Optional.ofNullable(initializeFrom)));
  }

  public ExportersConfig enableExporter(final String exporterName) {
    return enableExporter(exporterName, null);
  }
}
