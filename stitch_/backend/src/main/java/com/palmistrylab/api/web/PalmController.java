package com.palmistrylab.api.web;

import com.palmistrylab.api.palm.PalmAnalysisService;
import com.palmistrylab.api.palm.dto.AnalyzePalmRequest;
import com.palmistrylab.api.palm.dto.AnalyzeProgressListener;
import com.palmistrylab.api.palm.dto.PalmAnalyzeResponse;
import com.palmistrylab.api.palm.dto.PalmImageValidationRequest;
import com.palmistrylab.api.palm.dto.PalmImageValidationResponse;
import com.palmistrylab.api.palm.dto.RareMarkQueryRequest;
import com.palmistrylab.api.palm.dto.RareMarkQueryResponse;
import com.palmistrylab.api.palm.dto.UnlockDeepRequest;
import com.palmistrylab.api.palm.dto.UnlockDeepResponse;
import com.palmistrylab.api.palm.ImageRejectedException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.Executor;

/** 单人手相分析：同步/流式分析、深度解锁、稀有印记、图片校验。 */
@RestController
@RequestMapping("/api/v1")
public class PalmController {

  private final PalmAnalysisService palmAnalysisService;
  private final Executor analyzeExecutor;

  public PalmController(
      PalmAnalysisService palmAnalysisService,
      @Qualifier("analyzeExecutor") Executor analyzeExecutor) {
    this.palmAnalysisService = palmAnalysisService;
    this.analyzeExecutor = analyzeExecutor;
  }

  @PostMapping("/palm/analyze")
  public ResponseEntity<PalmAnalyzeResponse> analyzePalm(@Valid @RequestBody AnalyzePalmRequest request) {
    return ResponseEntity.ok(palmAnalysisService.analyzePalm(request));
  }

  /**
   * 流式分析（SSE）：事件依次为 stage（阶段）→ progress（生成字数）→ done（完整结果），
   * 失败时发 error 事件。让前端展示真实进度，替代假进度条。
   */
  @PostMapping(value = "/palm/analyze/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter analyzePalmStream(@Valid @RequestBody AnalyzePalmRequest request) {
    SseEmitter emitter = new SseEmitter(180_000L);
    analyzeExecutor.execute(() -> runAnalyzeStream(request, emitter));
    return emitter;
  }

  private void runAnalyzeStream(AnalyzePalmRequest request, SseEmitter emitter) {
    try {
      PalmAnalyzeResponse response = palmAnalysisService.analyzePalm(request, new AnalyzeProgressListener() {
        @Override
        public void onStage(String stage, int percent, String message) {
          sendEvent(emitter, "stage", Map.of(
              "stage", stage,
              "percent", percent,
              "message", message == null ? "" : message));
        }

        @Override
        public void onGeneratedChars(int chars) {
          sendEvent(emitter, "progress", Map.of("generatedChars", chars));
        }
      });
      sendEvent(emitter, "done", response);
      completeEmitter(emitter);
    } catch (ImageRejectedException ex) {
      sendEvent(emitter, "error", Map.of("code", "IMAGE_REJECTED", "message", String.valueOf(ex.getMessage())));
      completeEmitter(emitter);
    } catch (Exception ex) {
      sendEvent(emitter, "error", Map.of("code", "INTERNAL_ERROR", "message", "生成失败，请稍后再试"));
      completeEmitter(emitter);
    }
  }

  private void sendEvent(SseEmitter emitter, String event, Object payload) {
    try {
      emitter.send(SseEmitter.event().name(event).data(payload, MediaType.APPLICATION_JSON));
    } catch (Exception ignored) {
      // 客户端已断开等情况：剩余事件自然写不出去
    }
  }

  private void completeEmitter(SseEmitter emitter) {
    try {
      emitter.complete();
    } catch (Exception ignored) {
      // 已 complete 或客户端断开
    }
  }

  @PostMapping("/palm/unlock-deep")
  public ResponseEntity<UnlockDeepResponse> unlockDeep(@Valid @RequestBody UnlockDeepRequest request) {
    return ResponseEntity.ok(palmAnalysisService.unlockDeep(request.sessionId()));
  }

  @PostMapping("/palm/rare-mark")
  public ResponseEntity<RareMarkQueryResponse> queryRareMark(
      @Valid @RequestBody RareMarkQueryRequest request) {
    return ResponseEntity.ok(palmAnalysisService.queryRareMark(request.sessionId()));
  }

  @PostMapping("/palm/validate-image")
  public ResponseEntity<PalmImageValidationResponse> validatePalmImage(
      @Valid @RequestBody PalmImageValidationRequest request) {
    return ResponseEntity.ok(palmAnalysisService.validatePalmImage(request.imageData()));
  }
}
