package com.palmistrylab.api.web;

import com.palmistrylab.api.record.PalmRecordService;
import com.palmistrylab.api.record.dto.CalendarResponse;
import com.palmistrylab.api.record.dto.MonthlyReportResponse;
import com.palmistrylab.api.record.dto.RecordDetailResponse;
import com.palmistrylab.api.record.dto.UpdateRecordNoteRequest;
import com.palmistrylab.api.record.dto.UpdateRecordNoteResponse;
import com.palmistrylab.api.record.dto.WeeklyRecordRequest;
import com.palmistrylab.api.record.dto.WeeklyRecordResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 手相能量档案：周记录、日历、单日回顾、便签、月报。 */
@RestController
@RequestMapping("/api/v1")
public class RecordController {

  private final PalmRecordService palmRecordService;

  public RecordController(PalmRecordService palmRecordService) {
    this.palmRecordService = palmRecordService;
  }

  @PostMapping("/record/weekly")
  public ResponseEntity<WeeklyRecordResponse> weeklyRecord(@Valid @RequestBody WeeklyRecordRequest request) {
    return ResponseEntity.ok(palmRecordService.submitWeeklyRecord(request));
  }

  @GetMapping("/record/calendar")
  public ResponseEntity<CalendarResponse> calendar(
      @RequestParam String userId,
      @RequestParam(required = false) String yearMonth) {
    return ResponseEntity.ok(palmRecordService.getCalendar(userId, yearMonth));
  }

  @GetMapping("/record/detail")
  public ResponseEntity<RecordDetailResponse> recordDetail(
      @RequestParam String userId,
      @RequestParam String date) {
    return ResponseEntity.ok(palmRecordService.getRecordDetail(userId, date));
  }

  @PostMapping("/record/note")
  public ResponseEntity<UpdateRecordNoteResponse> updateRecordNote(
      @Valid @RequestBody UpdateRecordNoteRequest request) {
    return ResponseEntity.ok(palmRecordService.updateRecordNote(request));
  }

  @GetMapping("/record/monthly-report")
  public ResponseEntity<MonthlyReportResponse> monthlyReport(
      @RequestParam String userId,
      @RequestParam(required = false) String yearMonth) {
    return ResponseEntity.ok(palmRecordService.getMonthlyReport(userId, yearMonth));
  }
}
