package com.palmistrylab.api.controller;

import com.palmistrylab.api.service.PalmistryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PalmistryController.class)
@Import({ApiExceptionHandler.class, PalmistryControllerTest.TestConfig.class})
class PalmistryControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void healthEndpointReturnsExpectedPayload() throws Exception {
    mockMvc.perform(get("/api/v1/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ok"))
        .andExpect(jsonPath("$.name").value("palmistry-backend"))
        .andExpect(jsonPath("$.version").value("1.0.0"));
  }

  @Test
  void invalidAnalyzeRequestReturnsValidationError() throws Exception {
    mockMvc.perform(post("/api/v1/palm/analyze")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"handSide\":\"left\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.message").value("请求参数不完整或格式错误"));
  }

  @Test
  void validateImageEndpointRejectsNonPalmImage() throws Exception {
    String imageData = loadImageDataUri(Path.of("src/main/resources/static/light_1/screen.png"));

    mockMvc.perform(post("/api/v1/palm/validate-image")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"imageData\":\"" + imageData + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accepted").value(false))
        .andExpect(jsonPath("$.source").value("heuristic"));
  }

  private String loadImageDataUri(Path path) throws Exception {
    byte[] bytes = Files.readAllBytes(path);
    return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
  }

  @TestConfiguration
  static class TestConfig {

    @Bean
    PalmistryService palmistryService() {
      return new PalmistryService(
          null,
          null,
          null,
          null,
          new com.palmistrylab.api.service.LlmClientService(
              new RestTemplateBuilder(),
              new ObjectMapper(),
              false,
              "http://localhost:9",
              "",
              "test-model",
              "test-model"),
          new ObjectMapper());
    }
  }
}