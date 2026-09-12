package com.palmistrylab.api.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

  private final ApiExceptionHandler handler = new ApiExceptionHandler();

  @Test
  void handleIllegalArgumentMapsToBadRequest() {
    ResponseEntity<Map<String, Object>> response = handler.handleIllegalArgument(new IllegalArgumentException("bad input"));

    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(response.getBody()).containsEntry("code", "BAD_REQUEST");
    assertThat(response.getBody()).containsEntry("message", "bad input");
  }

  @Test
  void handleAnyMapsToInternalError() {
    ResponseEntity<Map<String, Object>> response = handler.handleAny(new RuntimeException("boom"));

    assertThat(response.getStatusCode().value()).isEqualTo(500);
    assertThat(response.getBody()).containsEntry("code", "INTERNAL_ERROR");
    assertThat(response.getBody()).containsEntry("message", "服务繁忙，请稍后再试");
  }

  @Test
  void handleImageRejectedMapsTo422() {
    ResponseEntity<Map<String, Object>> response = handler.handleImageRejected(
        new com.palmistrylab.api.palm.ImageRejectedException("这张图片不是手相，请上传清晰的手掌照片。"));

    assertThat(response.getStatusCode().value()).isEqualTo(422);
    assertThat(response.getBody()).containsEntry("code", "IMAGE_REJECTED");
    assertThat(response.getBody()).containsEntry("message", "这张图片不是手相，请上传清晰的手掌照片。");
  }
}