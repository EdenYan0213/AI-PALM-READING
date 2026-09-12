package com.palmistrylab.api.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LlmClientTest {

  private static final String BASE = "https://llm.example/v1";
  private static final String CHAT_URL = BASE + "/chat/completions";

  private MockRestServiceServer server;
  private LlmClient client;

  @BeforeEach
  void setUp() {
    // 借助 Spring Boot 的 MockServerRestTemplateCustomizer 把 MockServer 绑进客户端内部的 RestTemplate。
    org.springframework.boot.test.web.client.MockServerRestTemplateCustomizer customizer =
        new org.springframework.boot.test.web.client.MockServerRestTemplateCustomizer();
    RestTemplateBuilder builder = new RestTemplateBuilder().customizers(customizer);
    client = new LlmClient(builder, new ObjectMapper(), true, BASE, "test-key",
        "primary-model", "", "fallback-model");
    server = customizer.getServer();
  }

  private String chatBody(String content) {
    return "{\"choices\":[{\"message\":{\"content\":\"" + content + "\"}}]}";
  }

  @Test
  void chatParsesMessageContent() {
    server.expect(requestTo(CHAT_URL)).andExpect(method(org.springframework.http.HttpMethod.POST))
        .andRespond(withSuccess(chatBody("你好"), MediaType.APPLICATION_JSON));

    assertThat(client.chat("sys", "user")).isEqualTo("你好");
    server.verify();
  }

  @Test
  void chatRetriesFallbackModelOnServerErrorAndSucceeds() {
    // 主模型 500 → 无图不走去图重试 → 兜底模型成功：共 2 次请求
    server.expect(requestTo(CHAT_URL)).andExpect(method(org.springframework.http.HttpMethod.POST))
        .andRespond(withServerError());
    server.expect(requestTo(CHAT_URL)).andExpect(method(org.springframework.http.HttpMethod.POST))
        .andRespond(withSuccess(chatBody("兜底成功"), MediaType.APPLICATION_JSON));

    assertThat(client.chat("sys", "user")).isEqualTo("兜底成功");
    server.verify();
  }

  @Test
  void chatWithImageRetriesTextOnlyThenFallbackModel() {
    // 带图：主模型 500 → 去图重试 500 → 兜底模型成功：共 3 次请求
    server.expect(requestTo(CHAT_URL)).andRespond(withServerError());
    server.expect(requestTo(CHAT_URL)).andRespond(withServerError());
    server.expect(requestTo(CHAT_URL)).andRespond(withSuccess(chatBody("ok"), MediaType.APPLICATION_JSON));

    String result = client.chat("sys", "user", "data:image/png;base64,abc");
    assertThat(result).isEqualTo("ok");
    server.verify();
  }

  @Test
  void chatFailsFastOn4xxWithoutRetries() {
    server.expect(requestTo(CHAT_URL)).andRespond(
        org.springframework.test.web.client.response.MockRestResponseCreators
            .withStatus(org.springframework.http.HttpStatus.BAD_REQUEST));

    assertThatThrownBy(() -> client.chat("sys", "user"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("400");
    server.verify(); // 只发生 1 次请求：4xx 确定性失败不重试、不走兜底
  }

  @Test
  void chatForValidationNeverDropsImage() {
    // 校验通道：主模型 500 后只换模型重试（带图），绝不走去图降级：共 2 次请求
    server.expect(requestTo(CHAT_URL)).andRespond(withServerError());
    server.expect(requestTo(CHAT_URL)).andRespond(withSuccess(chatBody("palm"), MediaType.APPLICATION_JSON));

    String result = client.chatForValidation("sys", "is this a palm?", "data:image/png;base64,abc");
    assertThat(result).isEqualTo("palm");
    server.verify();
  }

  @Test
  void chatStreamAccumulatesDeltasAndInvokesCallback() {
    String sseBody = "data: {\"choices\":[{\"delta\":{\"content\":\"感情\"}}]}\n\n"
        + "data: {\"choices\":[{\"delta\":{\"content\":\"线平稳\"}}]}\n\n"
        + "data: [DONE]\n\n";
    server.expect(requestTo(CHAT_URL))
        .andRespond(withSuccess(sseBody, MediaType.TEXT_EVENT_STREAM));

    List<String> deltas = new ArrayList<>();
    String full = client.chatStream("sys", "user", null, deltas::add);

    assertThat(full).isEqualTo("感情线平稳");
    assertThat(deltas).containsExactly("感情", "线平稳");
    server.verify();
  }

  @Test
  void chatThrowsWhenAllAttemptsFail() {
    server.expect(requestTo(CHAT_URL)).andRespond(withServerError());
    server.expect(requestTo(CHAT_URL)).andRespond(withServerError());

    assertThatThrownBy(() -> client.chat("sys", "user"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Primary model failed");
    server.verify();
  }
}
