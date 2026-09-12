package com.palmistrylab.api.controller;

import com.palmistrylab.api.model.ApiDtos;
import com.palmistrylab.api.service.ImageRejectedException;
import com.palmistrylab.api.service.PalmistryService;
import com.palmistrylab.api.service.UserTokenService;
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

@RestController
@RequestMapping("/api/v1")
public class PalmistryController {

  private final PalmistryService palmistryService;
  private final UserTokenService userTokenService;
  private final Executor analyzeExecutor;

  public PalmistryController(
      PalmistryService palmistryService,
      UserTokenService userTokenService,
      @Qualifier("analyzeExecutor") Executor analyzeExecutor) {
    this.palmistryService = palmistryService;
    this.userTokenService = userTokenService;
    this.analyzeExecutor = analyzeExecutor;
  }

  @GetMapping("/health")
  public ResponseEntity<ApiDtos.HealthResponse> health() {
    return ResponseEntity.ok(new ApiDtos.HealthResponse("ok", "palmistry-backend", "1.0.0"));
  }

  @GetMapping("/user/identity")
  public ResponseEntity<ApiDtos.UserIdentityResponse> identity(
      @RequestParam(required = false) String previous) {
    return ResponseEntity.ok(userTokenService.resolveIdentity(previous));
  }

  @PostMapping("/palm/analyze")
  public ResponseEntity<ApiDtos.PalmAnalyzeResponse> analyzePalm(
      @Valid @RequestBody ApiDtos.AnalyzePalmRequest request) {
    return ResponseEntity.ok(palmistryService.analyzePalm(request));
  }

  /**
   * 流式分析（SSE）：事件依次为 stage（阶段）→ progress（生成字数）→ done（完整结果），
   * 失败时发 error 事件。让前端展示真实进度，替代假进度条。
   */
  @PostMapping(value = "/palm/analyze/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter analyzePalmStream(@Valid @RequestBody ApiDtos.AnalyzePalmRequest request) {
    SseEmitter emitter = new SseEmitter(180_000L);
    analyzeExecutor.execute(() -> runAnalyzeStream(request, emitter));
    return emitter;
  }

  private void runAnalyzeStream(ApiDtos.AnalyzePalmRequest request, SseEmitter emitter) {
    try {
      ApiDtos.PalmAnalyzeResponse response = palmistryService.analyzePalm(request, new ApiDtos.AnalyzeProgressListener() {
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
  public ResponseEntity<ApiDtos.UnlockDeepResponse> unlockDeep(@Valid @RequestBody ApiDtos.UnlockDeepRequest request) {
    return ResponseEntity.ok(palmistryService.unlockDeep(request.sessionId()));
  }

  @PostMapping("/palm/rare-mark")
  public ResponseEntity<ApiDtos.RareMarkQueryResponse> queryRareMark(
      @Valid @RequestBody ApiDtos.RareMarkQueryRequest request) {
    return ResponseEntity.ok(palmistryService.queryRareMark(request.sessionId()));
  }

  @PostMapping("/cp/analyze")
  public ResponseEntity<ApiDtos.CpAnalyzeResponse> analyzeCp(@Valid @RequestBody ApiDtos.CpAnalyzeRequest request) {
    return ResponseEntity.ok(palmistryService.analyzeCp(request));
  }

  @PostMapping("/cp/unlock-deep")
  public ResponseEntity<ApiDtos.UnlockDeepResponse> unlockCp(@Valid @RequestBody ApiDtos.UnlockDeepRequest request) {
    return ResponseEntity.ok(palmistryService.unlockCpDeep(request.sessionId()));
  }

  @PostMapping("/events/track")
  public ResponseEntity<ApiDtos.TrackEventResponse> trackEvent(@Valid @RequestBody ApiDtos.TrackEventRequest request) {
    return ResponseEntity.ok(palmistryService.trackEvent(request));
  }

  @GetMapping("/metrics/summary")
  public ResponseEntity<ApiDtos.MetricsSummaryResponse> metricsSummary() {
    return ResponseEntity.ok(palmistryService.metricsSummary());
  }

  @PostMapping("/llm/ping")
  public ResponseEntity<ApiDtos.LlmPingResponse> llmPing(@Valid @RequestBody ApiDtos.LlmPingRequest request) {
    return ResponseEntity.ok(palmistryService.llmPing(request.prompt()));
  }

  @PostMapping("/palm/validate-image")
  public ResponseEntity<ApiDtos.PalmImageValidationResponse> validatePalmImage(
      @Valid @RequestBody ApiDtos.PalmImageValidationRequest request) {
    return ResponseEntity.ok(palmistryService.validatePalmImage(request.imageData()));
  }

  @PostMapping("/record/weekly")
  public ResponseEntity<ApiDtos.WeeklyRecordResponse> weeklyRecord(
      @Valid @RequestBody ApiDtos.WeeklyRecordRequest request) {
    return ResponseEntity.ok(palmistryService.submitWeeklyRecord(request));
  }

  @GetMapping("/record/calendar")
  public ResponseEntity<ApiDtos.CalendarResponse> calendar(
      @RequestParam String userId,
      @RequestParam(required = false) String yearMonth) {
    return ResponseEntity.ok(palmistryService.getCalendar(userId, yearMonth));
  }

  @GetMapping("/record/detail")
  public ResponseEntity<ApiDtos.RecordDetailResponse> recordDetail(
      @RequestParam String userId,
      @RequestParam String date) {
    return ResponseEntity.ok(palmistryService.getRecordDetail(userId, date));
  }

  @PostMapping("/record/note")
  public ResponseEntity<ApiDtos.UpdateRecordNoteResponse> updateRecordNote(
      @Valid @RequestBody ApiDtos.UpdateRecordNoteRequest request) {
    return ResponseEntity.ok(palmistryService.updateRecordNote(request));
  }

  @GetMapping("/record/monthly-report")
  public ResponseEntity<ApiDtos.MonthlyReportResponse> monthlyReport(
      @RequestParam String userId,
      @RequestParam(required = false) String yearMonth) {
    return ResponseEntity.ok(palmistryService.getMonthlyReport(userId, yearMonth));
  }
}
