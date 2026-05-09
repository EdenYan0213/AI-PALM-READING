package com.palmistrylab.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class LlmClientService {

  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;
  private final boolean enabled;
  private final String baseUrl;
  private final String apiKey;
  private final String model;
  private final String fallbackModel;

  public LlmClientService(RestTemplateBuilder restTemplateBuilder,
      ObjectMapper objectMapper,
      @Value("${llm.enabled:false}") boolean enabled,
      @Value("${llm.base-url}") String baseUrl,
      @Value("${llm.api-key:}") String apiKey,
      @Value("${llm.model}") String model,
      @Value("${llm.fallback-model:ZhipuAI/GLM-5.1}") String fallbackModel) {
    this.restTemplate = restTemplateBuilder
        .setConnectTimeout(Duration.ofSeconds(10))
        .setReadTimeout(Duration.ofSeconds(45))
        .build();
    this.objectMapper = objectMapper;
    this.enabled = enabled;
    this.baseUrl = baseUrl;
    this.apiKey = apiKey;
    this.model = model;
    this.fallbackModel = fallbackModel;
  }

  public boolean isAvailable() {
    return enabled && apiKey != null && !apiKey.isBlank();
  }

  public String chat(String systemPrompt, String userPrompt) {
    return chat(systemPrompt, userPrompt, null);
  }

  public String chat(String systemPrompt, String userPrompt, String imageData) {
    if (!isAvailable()) {
      throw new IllegalStateException("LLM is not enabled or API key is missing");
    }

    Exception primaryError;
    try {
      return doChat(model, systemPrompt, userPrompt, imageData);
    } catch (Exception firstError) {
      primaryError = firstError;
    }

    if (fallbackModel != null && !fallbackModel.isBlank() && !fallbackModel.equals(model)) {
      try {
        return doChat(fallbackModel, systemPrompt, userPrompt, imageData);
      } catch (Exception fallbackError) {
        throw new IllegalStateException(
            "Primary model failed(" + model + "): " + primaryError.getMessage()
                + " ; fallback model failed(" + fallbackModel + "): " + fallbackError.getMessage(),
            fallbackError);
      }
    }

    throw new IllegalStateException("Primary model failed(" + model + "): " + primaryError.getMessage(), primaryError);
  }

  private String doChat(String modelName, String systemPrompt, String userPrompt, String imageData) {
    Exception firstError;

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
    } catch (Exception e) {
      firstError = e;
    }

    if (imageData != null && !imageData.isBlank()) {
      Map<String, Object> textOnlyBody = Map.of(
          "model", modelName,
          "stream", false,
          "temperature", 0.5,
          "messages", List.of(
              Map.of("role", "user", "content", promptText)));
      try {
        return executeChat(textOnlyBody);
      } catch (Exception ignored) {
        // Continue to lite mode.
      }
    }

    try {
      return doChatLite(modelName, userPrompt);
    } catch (Exception liteError) {
      throw new IllegalStateException(
          "LLM call failed in standard/text-only/lite modes for model " + modelName + ": "
              + firstError.getMessage() + " | " + liteError.getMessage(),
          liteError);
    }
  }

  private String doChatLite(String modelName, String userPrompt) {
    Map<String, Object> body = Map.of(
        "model", modelName,
        "stream", false,
        "temperature", 0.3,
        "messages", List.of(
            Map.of("role", "user", "content", userPrompt)));
    return executeChat(body);
  }

  private String executeChat(Map<String, Object> body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(apiKey);

    HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);
    ResponseEntity<String> response = restTemplate.exchange(
        baseUrl + "/chat/completions",
        HttpMethod.POST,
        requestEntity,
        String.class);

    String payloadText = response.getBody();
    if (payloadText == null || payloadText.isBlank()) {
      throw new IllegalStateException("Empty LLM response");
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
          throw new IllegalStateException("LLM response has no choices");
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

      throw new IllegalStateException("LLM choice has no content");
    } catch (Exception e) {
      String preview = payloadText.replaceAll("\\s+", " ");
      if (preview.length() > 180) {
        preview = preview.substring(0, 180) + "...";
      }
      throw new IllegalStateException("Failed to parse LLM response: " + preview, e);
    }
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
}
