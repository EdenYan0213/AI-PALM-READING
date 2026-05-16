package com.palmistrylab.api.controller;

import com.palmistrylab.api.model.ApiDtos;
import com.palmistrylab.api.service.PalmistryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PalmistryController {

  private final PalmistryService palmistryService;

  public PalmistryController(PalmistryService palmistryService) {
    this.palmistryService = palmistryService;
  }

  @GetMapping("/health")
  public ResponseEntity<ApiDtos.HealthResponse> health() {
    return ResponseEntity.ok(new ApiDtos.HealthResponse("ok", "palmistry-backend", "1.0.0"));
  }

  @PostMapping("/palm/analyze")
  public ResponseEntity<ApiDtos.PalmAnalyzeResponse> analyzePalm(
      @Valid @RequestBody ApiDtos.AnalyzePalmRequest request) {
    return ResponseEntity.ok(palmistryService.analyzePalm(request));
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
