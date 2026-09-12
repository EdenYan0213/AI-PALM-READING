package com.palmistrylab.api.perception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

/**
 * 感知层边车客户端（V0.2, TD §3）。
 *
 * 通过 PERCEPTION_BASE_URL 指向 FastAPI + MediaPipe 边车；
 * 未配置或调用失败时返回 null，主流程降级为 imageHash 确定性兜底，不影响可用性。
 */
@Service
public class PerceptionClient {

  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;
  private final String baseUrl;

  public PerceptionClient(
      RestTemplateBuilder restTemplateBuilder,
      ObjectMapper objectMapper,
      @Value("${app.perception.base-url:}") String baseUrl) {
    this.restTemplate = restTemplateBuilder
        .setConnectTimeout(Duration.ofSeconds(1))
        .setReadTimeout(Duration.ofSeconds(3))
        .build();
    this.objectMapper = objectMapper;
    this.baseUrl = baseUrl;
  }

  public boolean isEnabled() {
    return baseUrl != null && !baseUrl.isBlank();
  }

  /** 调用边车 /detect；任何失败（未配置/超时/异常）都返回 null。 */
  public PerceptionResult detect(String imageData) {
    if (!isEnabled() || imageData == null || imageData.isBlank()) {
      return null;
    }
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      HttpEntity<Map<String, String>> entity = new HttpEntity<>(Map.of("imageData", imageData), headers);
      String body = restTemplate.exchange(
          baseUrl + "/detect", HttpMethod.POST, entity, String.class).getBody();
      if (body == null || body.isBlank()) {
        return null;
      }
      JsonNode node = objectMapper.readTree(body);
      if (!node.path("detected").asBoolean(false)) {
        return new PerceptionResult(false, null, 0, 0, null,
            node.path("quality").path("retake").asBoolean(false),
            node.path("reason").asText(null));
      }
      JsonNode geometry = node.path("palmGeometry");
      return new PerceptionResult(
          true,
          node.path("handedness").asText(null),
          geometry.path("palmRatio").asDouble(0),
          geometry.path("fingerRatio").asDouble(0),
          geometry.path("suggestedPalmShape").asText(null),
          node.path("quality").path("retake").asBoolean(false),
          null);
    } catch (Exception ex) {
      return null;
    }
  }

  public record PerceptionResult(
      boolean detected,
      String handedness,
      double palmRatio,
      double fingerRatio,
      String suggestedPalmShape,
      boolean retake,
      String reason) {
  }
}
