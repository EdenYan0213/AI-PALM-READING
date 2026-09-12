package com.palmistrylab.api.web;

import com.palmistrylab.api.cp.CpService;
import com.palmistrylab.api.cp.dto.CpAnalyzeRequest;
import com.palmistrylab.api.cp.dto.CpAnalyzeResponse;
import com.palmistrylab.api.palm.dto.UnlockDeepRequest;
import com.palmistrylab.api.palm.dto.UnlockDeepResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** CP 合拍：分析报告与深度解锁。 */
@RestController
@RequestMapping("/api/v1")
public class CpController {

  private final CpService cpService;

  public CpController(CpService cpService) {
    this.cpService = cpService;
  }

  @PostMapping("/cp/analyze")
  public ResponseEntity<CpAnalyzeResponse> analyzeCp(@Valid @RequestBody CpAnalyzeRequest request) {
    return ResponseEntity.ok(cpService.analyzeCp(request));
  }

  @PostMapping("/cp/unlock-deep")
  public ResponseEntity<UnlockDeepResponse> unlockCp(@Valid @RequestBody UnlockDeepRequest request) {
    return ResponseEntity.ok(cpService.unlockCpDeep(request.sessionId()));
  }
}
