/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.util;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matcher;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

public class MetricAssert {
  
  public static final String ENDPOINT = "/actuator/prometheus";
  
  public static void assertThatMetricsAreDisabledFrom(MockMvc mockMvc) throws Exception {
    MockHttpServletRequestBuilder request = get(ENDPOINT);
    mockMvc.perform(request)
        .andExpect(status().is(404));
  }
  
  public static void assertThatMetricsFrom(MockMvc mockMvc,Matcher<? super String> matcher) throws Exception {
    MockHttpServletRequestBuilder request = get(ENDPOINT);
    mockMvc.perform(request).andExpect(status().isOk())
    .andExpect(content().string(matcher));
  }
  
}
