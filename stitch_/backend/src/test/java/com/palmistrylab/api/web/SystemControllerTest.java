package com.palmistrylab.api.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.palmistrylab.api.common.ApiExceptionHandler;
import com.palmistrylab.api.identity.UserTokenService;
import com.palmistrylab.api.llm.LlmClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SystemController.class)
@Import({ApiExceptionHandler.class, SystemControllerTest.TestConfig.class})
class SystemControllerTest {

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
  void identityIssuesSignedUserId() throws Exception {
    mockMvc.perform(get("/api/v1/user/identity"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(org.hamcrest.Matchers.matchesPattern("U-[0-9a-f]{12}-[0-9a-f]{8}")))
        .andExpect(jsonPath("$.restored").value(false));
  }

  @TestConfiguration
  static class TestConfig {

    @Bean
    UserTokenService userTokenService() {
      return new UserTokenService("test-secret");
    }

    @Bean
    LlmClient llmClient() {
      return new LlmClient(
          new org.springframework.boot.web.client.RestTemplateBuilder(),
          new ObjectMapper(),
          false,
          "http://localhost:9",
          "",
          "test-model",
          "test-model",
          "test-model");
    }
  }
}
