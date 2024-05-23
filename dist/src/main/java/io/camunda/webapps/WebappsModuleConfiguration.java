/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps;

import io.camunda.webapps.WebappsModuleConfiguration.WebappsProperties;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/** Configuration for shared spring components used by webapps */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = "io.camunda.webapps")
@EnableAutoConfiguration
@EnableConfigurationProperties(WebappsProperties.class)
public class WebappsModuleConfiguration {
  @ConfigurationProperties("camunda.webapps")
  public static record WebappsProperties(
      List<String> enabledApps, @NotNull String defaultApp, boolean loginDelegated) {}
}
