package com.palmistrylab.api.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.palmistrylab.api.common.ApiExceptionHandler;
import com.palmistrylab.api.llm.LlmClient;
import com.palmistrylab.api.metrics.AppEventRepository;
import com.palmistrylab.api.metrics.MetricsService;
import com.palmistrylab.api.palm.LoreService;
import com.palmistrylab.api.palm.PalmAnalysisService;
import com.palmistrylab.api.palm.PalmNarrativeWriter;
import com.palmistrylab.api.palm.PalmSessionService;
import com.palmistrylab.api.palm.SessionRecordRepository;
import com.palmistrylab.api.perception.PerceptionClient;
import com.palmistrylab.api.record.PalmRecordRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.Executor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 上帝类拆分后，控制器测试可以直接装配真实服务
 * （此前 PalmistryService 依赖 10 个协作者，只能传 null 仓库凑合）。
 */
@WebMvcTest(PalmController.class)
@Import({ApiExceptionHandler.class, PalmControllerTest.TestConfig.class})
class PalmControllerTest {

  @Autowired
  private MockMvc mockMvc;

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
    String imageData = loadImageDataUri(java.nio.file.Path.of("src/test/resources/fixtures/not-a-palm.png"));

    mockMvc.perform(post("/api/v1/palm/validate-image")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"imageData\":\"" + imageData + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accepted").value(false))
        .andExpect(jsonPath("$.source").value("heuristic"));
  }

  private String loadImageDataUri(java.nio.file.Path path) throws Exception {
    byte[] bytes = java.nio.file.Files.readAllBytes(path);
    return "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(bytes);
  }

  @TestConfiguration
  static class TestConfig {

    @Bean
    PalmAnalysisService palmAnalysisService() {
      SessionRecordRepository sessionRepo = Mockito.mock(SessionRecordRepository.class);
      AppEventRepository eventRepo = Mockito.mock(AppEventRepository.class);
      PalmRecordRepository palmRepo = Mockito.mock(PalmRecordRepository.class);
      LlmClient llmClient = new LlmClient(new RestTemplateBuilder(), new ObjectMapper(), false,
          "http://localhost:9", "", "test-model", "test-model", "test-model");
      PerceptionClient perceptionClient = new PerceptionClient(new RestTemplateBuilder(), new ObjectMapper(), "");
      PalmSessionService palmSessionService = new PalmSessionService(sessionRepo);
      PalmNarrativeWriter narrativeWriter = new PalmNarrativeWriter(llmClient, new LoreService(), new ObjectMapper());
      MetricsService metricsService = new MetricsService(eventRepo, sessionRepo, palmRepo);
      com.palmistrylab.api.palm.PalmRules rules = new com.palmistrylab.api.palm.PalmRules();
      return new PalmAnalysisService(sessionRepo, palmSessionService, narrativeWriter, llmClient,
          perceptionClient, metricsService,
          new com.palmistrylab.api.palm.TraceGeometryAnalyzer(rules),
          new com.palmistrylab.api.palm.LocalPalmImageValidator(rules));
    }

    @Bean(name = "analyzeExecutor")
    Executor analyzeExecutor() {
      return Runnable::run;
    }
  }
}
