package com.palmistrylab.api.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service
public class LlmClient {

  /**
   * 带分类的 LLM 调用异常：retryable=false 的确定性失败（4xx，除 408/429）
   * 不再触发去图重试 / 兜底模型重试，避免把用户请求拖成分钟级。
   */
  public static class LlmCallException extends RuntimeException {
    private final Integer status;
    private final boolean retryable;

    public LlmCallException(String message, Integer status, boolean retryable, Throwable cause) {
      super(message, cause);
      this.status = status;
      this.retryable = retryable;
    }

    public Integer getStatus() {
      return status;
    }

    public boolean isRetryable() {
      return retryable;
    }
  }

  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;
  private final boolean enabled;
  private final String baseUrl;
  private final String apiKey;
  private final String model;
  private final String visionModel;
  private final String fallbackModel;

  public LlmClient(RestTemplateBuilder restTemplateBuilder,
      ObjectMapper objectMapper,
      @Value("${llm.enabled:false}") boolean enabled,
      @Value("${llm.base-url}") String baseUrl,
      @Value("${llm.api-key:}") String apiKey,
      @Value("${llm.model}") String model,
      @Value("${llm.vision-model:}") String visionModel,
      @Value("${llm.fallback-model:ZhipuAI/GLM-5.1}") String fallbackModel) {
    this(restTemplateBuilder, objectMapper, enabled, baseUrl, apiKey, model, visionModel, fallbackModel,
        Duration.ofSeconds(5), Duration.ofSeconds(30));
  }

  @Autowired
  public LlmClient(RestTemplateBuilder restTemplateBuilder,
      ObjectMapper objectMapper,
      @Value("${llm.enabled:false}") boolean enabled,
      @Value("${llm.base-url}") String baseUrl,
      @Value("${llm.api-key:}") String apiKey,
      @Value("${llm.model}") String model,
      @Value("${llm.vision-model:}") String visionModel,
      @Value("${llm.fallback-model:ZhipuAI/GLM-5.1}") String fallbackModel,
      @Value("${llm.connect-timeout-seconds:5}") long connectTimeoutSeconds,
      @Value("${llm.read-timeout-seconds:30}") long readTimeoutSeconds) {
    this(restTemplateBuilder, objectMapper, enabled, baseUrl, apiKey, model, visionModel, fallbackModel,
        Duration.ofSeconds(connectTimeoutSeconds), Duration.ofSeconds(readTimeoutSeconds));
  }

  public LlmClient(RestTemplateBuilder restTemplateBuilder,
      ObjectMapper objectMapper,
      boolean enabled,
      String baseUrl,
      String apiKey,
      String model,
      String visionModel,
      String fallbackModel,
      Duration connectTimeout,
      Duration readTimeout) {
    this.restTemplate = restTemplateBuilder
        .setConnectTimeout(connectTimeout)
        .setReadTimeout(readTimeout)
        .build();
    this.objectMapper = objectMapper;
    this.enabled = enabled;
    this.baseUrl = baseUrl;
    this.apiKey = apiKey;
    this.model = model;
    this.visionModel = visionModel;
    this.fallbackModel = fallbackModel;
  }

  public boolean isAvailable() {
    return enabled && apiKey != null && !apiKey.isBlank();
  }

  public String chat(String systemPrompt, String userPrompt) {
    return chat(systemPrompt, userPrompt, null);
  }

  /**
   * 生成类调用。重试链（收紧后）：
   * 主模型(可含图) → [仅可重试错误] 同模型去图 → [仅可重试错误] 兜底模型；
   * 视觉模型失败时整体退回文本主模型链路。4xx（除 408/429）一律立即失败。
   */
  public String chat(String systemPrompt, String userPrompt, String imageData) {
    assertAvailable();

    // 视觉/文本模型拆分（TD §13.1）：带图请求优先走专用视觉模型。
    boolean hasImage = imageData != null && !imageData.isBlank();
    String primaryModel = hasImage && hasText(visionModel) ? visionModel : model;

    Exception primaryError;
    try {
      return doChat(primaryModel, systemPrompt, userPrompt, imageData, false);
    } catch (Exception firstError) {
      primaryError = firstError;
    }

    if (!primaryModel.equals(model)) {
      // 视觉模型失败时退回文本主模型（其内部还会做兜底模型重试）。
      try {
        return chat(systemPrompt, userPrompt, null);
      } catch (Exception secondaryError) {
        throw new IllegalStateException(
            "Vision model failed(" + primaryModel + "): " + summarizeException(primaryError)
                + " ; text model also failed: " + summarizeException(secondaryError),
            secondaryError);
      }
    }

    if (hasText(fallbackModel) && !fallbackModel.equals(model) && isWorthFallback(primaryError)) {
      try {
        return doChat(fallbackModel, systemPrompt, userPrompt, imageData, false);
      } catch (Exception fallbackError) {
        throw new IllegalStateException(
            "Primary model failed(" + primaryModel + "): " + summarizeException(primaryError)
                + " ; fallback model failed(" + fallbackModel + "): " + summarizeException(fallbackError),
            fallbackError);
      }
    }
    throw new IllegalStateException("Primary model failed(" + primaryModel + "): " + summarizeException(primaryError), primaryError);
  }

  /**
   * 图片校验类调用：图片是判断依据，失败后去图重试毫无意义，
   * 因此只允许换模型重试，且不做任何去图降级。
   */
  public String chatForValidation(String systemPrompt, String userPrompt, String imageData) {
    assertAvailable();

    boolean hasImage = imageData != null && !imageData.isBlank();
    String primaryModel = hasImage && hasText(visionModel) ? visionModel : model;

    Exception primaryError;
    try {
      return doChat(primaryModel, systemPrompt, userPrompt, imageData, true);
    } catch (Exception firstError) {
      primaryError = firstError;
    }

    String secondaryModel = primaryModel.equals(model) ? fallbackModel : model;
    if (hasText(secondaryModel) && !secondaryModel.equals(primaryModel) && isWorthFallback(primaryError)) {
      try {
        return doChat(secondaryModel, systemPrompt, userPrompt, imageData, true);
      } catch (Exception secondaryError) {
        throw new IllegalStateException(
            "Validation call failed for " + primaryModel + " and " + secondaryModel + ": "
                + summarizeException(primaryError) + " | " + summarizeException(secondaryError),
            secondaryError);
      }
    }
    throw new IllegalStateException("Validation call failed(" + primaryModel + "): " + summarizeException(primaryError), primaryError);
  }

  /**
   * 流式生成（OpenAI 兼容 SSE）：边生成边通过 onDelta 回调增量文本，
   * 供上层把真实进度推给前端。单次尝试不内部重试，失败由调用方回退到 chat()。
   */
  public String chatStream(String systemPrompt, String userPrompt, String imageData, Consumer<String> onDelta) {
    assertAvailable();

    boolean hasImage = imageData != null && !imageData.isBlank();
    String primaryModel = hasImage && hasText(visionModel) ? visionModel : model;
    String promptText = "[Instruction]\n" + systemPrompt + "\n\n[Task]\n" + userPrompt;
    Object content = hasImage
        ? List.of(
            Map.of("type", "text", "text", promptText),
            Map.of("type", "image_url", "image_url", Map.of("url", imageData)))
        : promptText;

    Map<String, Object> body = Map.of(
        "model", primaryModel,
        "stream", true,
        "temperature", 0.7,
        "messages", List.of(Map.of("role", "user", "content", content)));

    StringBuilder full = new StringBuilder();
    restTemplate.execute(
        baseUrl + "/chat/completions",
        HttpMethod.POST,
        request -> {
          HttpHeaders headers = request.getHeaders();
          headers.setContentType(MediaType.APPLICATION_JSON);
          headers.setBearerAuth(apiKey);
          objectMapper.writeValue(request.getBody(), body);
        },
        response -> {
          try (BufferedReader reader = new BufferedReader(
              new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
              if (!line.startsWith("data:")) {
                continue;
              }
              String data = line.substring(5).trim();
              if ("[DONE]".equals(data)) {
                break;
              }
              JsonNode node = objectMapper.readTree(data);
              JsonNode delta = node.path("choices").path(0).path("delta").path("content");
              if (delta.isTextual() && !delta.asText().isEmpty()) {
                String piece = delta.asText();
                full.append(piece);
                if (onDelta != null) {
                  onDelta.accept(piece);
                }
              }
            }
          }
          return null;
        });

    if (full.isEmpty()) {
      throw new IllegalStateException("Empty streamed LLM response");
    }
    return full.toString();
  }

  private void assertAvailable() {
    if (!isAvailable()) {
      throw new IllegalStateException("LLM is not enabled or API key is missing");
    }
  }

  private String doChat(String modelName, String systemPrompt, String userPrompt, String imageData, boolean imageEssential) {
    String promptText = "[Instruction]\n" + systemPrompt + "\n\n[Task]\n" + userPrompt;
    Object content = (imageData == null || imageData.isBlank())
        ? promptText
        : List.of(
            Map.of("type", "text", "text", promptText),
            Map.of("type", "image_url", "image_url", Map.of("url", imageData)));

    Map<String, Object> body = Map.of(
        "model", modelName,
        "stream", false,
        "temperature", 0.7,
        "messages", List.of(
            Map.of("role", "user", "content", content)));

    try {
      return executeChat(body);
    } catch (LlmCallException e) {
      // 确定性失败（鉴权/参数错误等）立即失败；去图重试只针对可重试错误。
      if (imageData == null || imageData.isBlank() || imageEssential || !e.isRetryable()) {
        throw e;
      }
    }

    // 去图重试：仅一次（图片负载导致的传输失败时有效）。
    Map<String, Object> textOnlyBody = Map.of(
        "model", modelName,
        "stream", false,
        "temperature", 0.5,
        "messages", List.of(
            Map.of("role", "user", "content", promptText)));
    try {
      return executeChat(textOnlyBody);
    } catch (LlmCallException retryError) {
      throw retryError;
    }
  }

  private String executeChat(Map<String, Object> body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(apiKey);

    HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);
    ResponseEntity<String> response;
    try {
      response = restTemplate.exchange(
          baseUrl + "/chat/completions",
          HttpMethod.POST,
          requestEntity,
          String.class);
    } catch (HttpStatusCodeException e) {
      int status = e.getStatusCode().value();
      boolean retryable = status == 408 || status == 429 || status >= 500;
      throw new LlmCallException("HTTP " + status + ": " + shorten(e.getResponseBodyAsString()), status, retryable, e);
    } catch (ResourceAccessException e) {
      throw new LlmCallException("Network/timeout: " + e.getMessage(), null, true, e);
    }

    String payloadText = response.getBody();
    if (payloadText == null || payloadText.isBlank()) {
      throw new LlmCallException("Empty LLM response", null, true, null);
    }

    try {
      JsonNode payload = objectMapper.readTree(payloadText);
      JsonNode choices = payload.path("choices");
      if (!choices.isArray() || choices.isEmpty()) {
        JsonNode output = payload.path("output");
        JsonNode outputChoices = output.path("choices");
        if (outputChoices.isArray() && !outputChoices.isEmpty()) {
          choices = outputChoices;
        } else {
          JsonNode outputText = output.path("text");
          if (!outputText.isMissingNode() && !outputText.asText().isBlank()) {
            return outputText.asText();
          }
          throw new LlmCallException("LLM response has no choices", null, true, null);
        }
      }

      JsonNode first = choices.get(0);
      JsonNode message = first.path("message");
      if (!message.isMissingNode() && message.has("content")) {
        String messageContent = extractContentValue(message.path("content"));
        if (messageContent != null && !messageContent.isBlank()) {
          return messageContent;
        }
      }

      JsonNode delta = first.path("delta");
      if (!delta.isMissingNode() && delta.has("content")) {
        return delta.path("content").asText();
      }

      JsonNode text = first.path("text");
      if (!text.isMissingNode() && !text.asText().isBlank()) {
        return text.asText();
      }

      throw new LlmCallException("LLM choice has no content", null, true, null);
    } catch (LlmCallException e) {
      throw e;
    } catch (Exception e) {
      String preview = payloadText.replaceAll("\\s+", " ");
      if (preview.length() > 180) {
        preview = preview.substring(0, 180) + "...";
      }
      throw new LlmCallException("Failed to parse LLM response: " + preview, null, true, e);
    }
  }

  private boolean isWorthFallback(Throwable error) {
    // 4xx 确定性失败换模型也无济于事（鉴权、参数错误对所有模型一致），立即失败。
    return !(error instanceof LlmCallException llmError) || llmError.isRetryable();
  }

  private String extractContentValue(JsonNode contentNode) {
    if (contentNode == null || contentNode.isMissingNode() || contentNode.isNull()) {
      return null;
    }
    if (contentNode.isTextual()) {
      return contentNode.asText();
    }
    if (contentNode.isArray()) {
      StringBuilder builder = new StringBuilder();
      for (JsonNode item : contentNode) {
        if (item.isTextual()) {
          builder.append(item.asText());
          continue;
        }
        JsonNode textNode = item.path("text");
        if (!textNode.isMissingNode() && textNode.isTextual()) {
          if (builder.length() > 0) {
            builder.append('\n');
          }
          builder.append(textNode.asText());
        }
      }
      return builder.toString();
    }
    if (contentNode.isObject()) {
      JsonNode textNode = contentNode.path("text");
      if (!textNode.isMissingNode() && textNode.isTextual()) {
        return textNode.asText();
      }
    }
    return contentNode.asText();
  }

  private String summarizeException(Exception error) {
    if (error == null) {
      return "unknown";
    }
    String message = error.getMessage();
    return shorten(message == null || message.isBlank() ? error.getClass().getSimpleName() : message);
  }

  private String shorten(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    String preview = value.replaceAll("\\s+", " ").trim();
    if (preview.length() > 260) {
      return preview.substring(0, 260) + "...";
    }
    return preview;
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
