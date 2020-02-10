/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.apache.http.HttpStatus;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.service.LocalizationService;
import org.camunda.optimize.util.FileReaderUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class LocalizationRestServiceIT extends AbstractIT {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @ParameterizedTest
  @MethodSource("defaultLocales")
  public void getLocalizationFile(final String localeCode) {
    //given
    final JsonNode expectedLocaleJson = getExpectedLocalizationFile(localeCode);

    // when
    final JsonNode localeJson = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetLocalizationRequest(localeCode)
      .execute(JsonNode.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(localeJson, is(expectedLocaleJson));
  }

  @Test
  public void getFallbackLocalizationForInvalidCode() {
    //given
    final String localeCode = "xyz";
    final JsonNode expectedLocaleJson = getExpectedLocalizationFile(
      embeddedOptimizeExtension.getConfigurationService().getFallbackLocale()
    );

    // when
    final JsonNode localeJson = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetLocalizationRequest(localeCode)
      .execute(JsonNode.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(localeJson, is(expectedLocaleJson));
  }

  @Test
  public void getFallbackLocalizationForMissingCode() {
    //given
    final JsonNode expectedLocaleJson = getExpectedLocalizationFile(
      embeddedOptimizeExtension.getConfigurationService().getFallbackLocale()
    );

    // when
    final JsonNode localeJson = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetLocalizationRequest(null)
      .execute(JsonNode.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(localeJson, is(expectedLocaleJson));
  }

  @Test
  public void getErrorOnLocalizationFileGone() {
    //given
    final String localeCode = "xyz";
    embeddedOptimizeExtension.getConfigurationService().getAvailableLocales().add(localeCode);

    // when
    final Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetLocalizationRequest(localeCode)
      .execute();

    // then
    assertThat(response.getStatus(), is(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()));
  }

  @ParameterizedTest
  @MethodSource("defaultLocales")
  public void getLocalizedWhatsNewMarkdown(final String localeCode) {
    //given
    final String expectedLocalizedMarkdown = getExpectedWhatsNewMarkdownContentForLocale(localeCode);

    // when
    final String localeMarkdown = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetLocalizedWhatsNewMarkdownRequest(localeCode)
      .execute(String.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(localeMarkdown, is(expectedLocalizedMarkdown));
  }

  @Test
  public void getFallbackWhatsNewMarkdownForInvalidCode() {
    //given
    final String localeCode = "xyz";
    final String expectedLocalizedMarkdown = getExpectedWhatsNewMarkdownContentForLocale(
      embeddedOptimizeExtension.getConfigurationService().getFallbackLocale()
    );

    // when
    final String localeMarkdown = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetLocalizedWhatsNewMarkdownRequest(localeCode)
      .execute(String.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(localeMarkdown, is(expectedLocalizedMarkdown));
  }

  @Test
  public void getFallbackWhatsNewMarkdownForMissingCode() {
    final String expectedLocalizedMarkdown = getExpectedWhatsNewMarkdownContentForLocale(
      embeddedOptimizeExtension.getConfigurationService().getFallbackLocale()
    );

    // when
    final String localeMarkdown = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetLocalizedWhatsNewMarkdownRequest(null)
      .execute(String.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(localeMarkdown, is(expectedLocalizedMarkdown));
  }


  @Test
  public void getErrorOnMarkdownFileGone() {
    //given
    final String localeCode = "xyz";
    embeddedOptimizeExtension.getConfigurationService().getAvailableLocales().add(localeCode);

    // when
    final Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetLocalizedWhatsNewMarkdownRequest(localeCode)
      .execute();

    // then
    assertThat(response.getStatus(), is(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()));
  }

  @SneakyThrows
  private JsonNode getExpectedLocalizationFile(final String locale) {
    return OBJECT_MAPPER.readValue(
      FileReaderUtil.readFile("/" + LocalizationService.LOCALIZATION_PATH + locale + ".json"),
      JsonNode.class
    );
  }

  @SneakyThrows
  private String getExpectedWhatsNewMarkdownContentForLocale(final String localeCode) {
    return FileReaderUtil.readFile("/" + LocalizationService.LOCALIZATION_PATH + "whatsnew_" + localeCode + ".md");
  }

  private static String[] defaultLocales() {
    return new String[]{"en", "de"};
  }
}
