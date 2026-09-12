package com.palmistrylab.api.web;

import com.palmistrylab.api.identity.UserTokenService;
import com.palmistrylab.api.llm.LlmClient;
import com.palmistrylab.api.palm.dto.LlmPingRequest;
import com.palmistrylab.api.palm.dto.LlmPingResponse;
import com.palmistrylab.api.web.dto.HealthResponse;
import com.palmistrylab.api.web.dto.UserIdentityResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 系统与身份：健康检查、LLM 探活、用户身份签发/续签。 */
@RestController
@RequestMapping("/api/v1")
public class SystemController {

  private final UserTokenService userTokenService;
  private final LlmClient llmClient;

  public SystemController(UserTokenService userTokenService, LlmClient llmClient) {
    this.userTokenService = userTokenService;
    this.llmClient = llmClient;
  }

  @GetMapping("/health")
  public ResponseEntity<HealthResponse> health() {
    return ResponseEntity.ok(new HealthResponse("ok", "palmistry-backend", "1.0.0"));
  }

  @GetMapping("/user/identity")
  public ResponseEntity<UserIdentityResponse> identity(
      @RequestParam(required = false) String previous) {
    return ResponseEntity.ok(userTokenService.resolveIdentity(previous));
  }

  @PostMapping("/llm/ping")
  public ResponseEntity<LlmPingResponse> llmPing(@Valid @RequestBody LlmPingRequest request) {
    if (!llmClient.isAvailable()) {
      return ResponseEntity.ok(new LlmPingResponse(false, "LLM not enabled"));
    }
    try {
      String output = llmClient.chat("你是一个简洁助手。", request.prompt());
      return ResponseEntity.ok(new LlmPingResponse(true, output));
    } catch (Exception ex) {
      return ResponseEntity.ok(new LlmPingResponse(false, "LLM call failed: " + ex.getMessage()));
    }
  }
}
