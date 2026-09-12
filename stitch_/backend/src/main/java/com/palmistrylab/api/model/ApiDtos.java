package com.palmistrylab.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public final class ApiDtos {

  private ApiDtos() {
  }

  public record HealthResponse(String status, String name, String version) {
  }

  public record AnalyzePalmRequest(
      @NotBlank String source,
      @NotBlank String handSide,
      String gender,
      String imageData,
      PalmLineTraces traces,
      String recordMode) {
  }

  public record PalmLineTraces(
      List<TracePoint> lifeLine,
      List<TracePoint> wisdomLine,
      List<TracePoint> loveLine) {
  }

  public record TracePoint(
      double x,
      double y,
      Long t) {
  }

  public record PalmLineSummary(String lineName, String tags, String shortInterpretation) {
  }

  public record PalmAnalyzeResponse(
      String sessionId,
      String handType,
      List<String> personalityTags,
      List<PalmLineSummary> freeOverview,
      String rareMark,
      String teaser,
      String slogan,
      boolean traceConfirmed,
      String traceFeatureSummary,
      boolean llmUsed,
      String llmStatus,
      PalmFeatureSet featureSet) {
  }

  /** 量化层契约（TD §4）：确定性特征集合，前端/CP 引擎唯一事实源。 */
  public record PalmFeatureSet(
      String version,
      String source,
      String imageHash,
      PalmShapeFeature palmShape,
      QualityFeature quality) {
  }

  public record PalmShapeFeature(String type, Double confidence, ShapeBasis basis) {
  }

  public record ShapeBasis(Double palmRatio, Double fingerRatio) {
  }

  public record QualityFeature(Double blur, Double brightness, Boolean retake) {
  }

  public record UnlockDeepRequest(@NotBlank String sessionId) {
  }

  public record DeepSection(String title, String detail, String cyberTip) {
  }

  public record UnlockDeepResponse(
      String sessionId,
      boolean unlocked,
      String unlockType,
      List<DeepSection> sections) {
  }

  public record RareMarkQueryRequest(@NotBlank String sessionId) {
  }

  public record RareMarkQueryResponse(
      String sessionId,
      String markName,
      String wechatId,
      int remainQuota,
      String hint) {
  }

  public record CpAnalyzeRequest(
      @NotNull CpUser userA,
      @NotNull CpUser userB) {
  }

  public record CpUser(
      @NotBlank String nickname,
      @NotBlank String handType,
      @NotBlank String mbti) {
  }

  public record CpDimension(String name, int score) {
  }

  public record CpAnalyzeResponse(
      String cpSessionId,
      double matchScore,
      String comboName,
      List<CpDimension> dimensions,
      String interpretation,
      String tip) {
  }

  public record TrackEventRequest(
      @NotBlank String eventName,
      String sessionId,
      String channel) {
  }

  public record TrackEventResponse(boolean accepted, String eventName) {
  }

  public record MetricsSummaryResponse(
      long totalSingleAnalyze,
      long totalCpAnalyze,
      long totalWeeklyRecord,
      long totalShare,
      long totalAdUnlock,
      long totalRareMarkQuery,
      double adUnlockRate,
      double cpInitiateRate,
      double shareRate,
      double privateLeadRate) {
  }

  public record LlmPingRequest(@NotBlank String prompt) {
  }

  public record LlmPingResponse(boolean live, String modelOutput) {
  }

  public record PalmImageValidationRequest(@NotBlank String imageData) {
  }

  /** 分析进度回调：SSE 端点据此推送真实阶段与生成进度，同步端点使用 NONE 静默实现。 */
  public interface AnalyzeProgressListener {

    AnalyzeProgressListener NONE = new AnalyzeProgressListener() {
    };

    default void onStage(String stage, int percent, String message) {
    }

    default void onGeneratedChars(int chars) {
    }
  }

  public record PalmImageValidationResponse(
      boolean accepted,
      double confidence,
      String reason,
      String source) {
  }

  public record WeeklyRecordRequest(
      @NotBlank String userId,
      @NotBlank String recordMode,
      @NotBlank String imageData,
      PalmLineTraces traces,
      String recordDate) {
  }

  public record WeeklyRecordResponse(
      String recordId,
      String userId,
      String recordMode,
      String recordDate,
      int energyLevel,
      int wisdomActive,
      int emotionWave,
      String aiSummary,
      String compareHint,
      String runeColor,
      boolean monthlyUnlocked,
      int monthRecordCount,
      int monthTarget) {
  }

  public record CalendarResponse(
      String userId,
      String yearMonth,
      int monthRecordCount,
      int monthTarget,
      List<CalendarDayRecord> records,
      List<EnergyTrendPoint> trend) {
  }

  public record CalendarDayRecord(
      String date,
      String runeColor,
      int energyLevel,
      int wisdomActive,
      int emotionWave,
      String mode,
      String summary) {
  }

  public record EnergyTrendPoint(
      String date,
      int energyLevel) {
  }

  public record RecordDetailResponse(
      String recordId,
      String userId,
      String recordDate,
      String recordMode,
      int energyLevel,
      int wisdomActive,
      int emotionWave,
      String aiSummary,
      String userNote,
      String runeColor) {
  }

  public record UserIdentityResponse(
      String userId,
      boolean restored) {
  }

  public record UpdateRecordNoteRequest(
      @NotBlank String recordId,
      @NotBlank String userId,
      @NotEmpty String userNote) {
  }

  public record UpdateRecordNoteResponse(
      boolean updated,
      String recordId,
      String userNote) {
  }

    public record MonthlyReportResponse(
            String userId,
            String yearMonth,
            int recordCount,
            String dominantEnergy,
            List<EnergyTrendPoint> trend,
            String reportText,
            boolean unlocked) {
    }
}
