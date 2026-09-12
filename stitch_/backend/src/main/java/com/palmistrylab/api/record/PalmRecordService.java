package com.palmistrylab.api.record;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.palmistrylab.api.identity.UserTokenService;
import com.palmistrylab.api.media.PhotoStorageService;
import com.palmistrylab.api.metrics.MetricsService;
import com.palmistrylab.api.palm.dto.PalmLineTraces;
import com.palmistrylab.api.record.dto.CalendarDayRecord;
import com.palmistrylab.api.record.dto.CalendarResponse;
import com.palmistrylab.api.record.dto.EnergyTrendPoint;
import com.palmistrylab.api.record.dto.MonthlyReportResponse;
import com.palmistrylab.api.record.dto.RecordDetailResponse;
import com.palmistrylab.api.record.dto.UpdateRecordNoteResponse;
import com.palmistrylab.api.record.dto.WeeklyRecordResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 手相能量档案（周记录/日历/详情/便签/月报）。
 * 列表读取走窄投影（不拉取 photo_url 大字段）；月报 upsert 加事务与悲观锁防并发重复行。
 */
@Service
public class PalmRecordService {

  private static final DateTimeFormatter YEAR_MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
  private static final int MONTH_TARGET = 4;

  private final PalmRecordRepository palmRecordRepository;
  private final MonthlyReportRepository monthlyReportRepository;
  private final UserTokenService userTokenService;
  private final MetricsService metricsService;
  private final PhotoStorageService photoStorageService;
  private final ObjectMapper objectMapper;

  public PalmRecordService(
      PalmRecordRepository palmRecordRepository,
      MonthlyReportRepository monthlyReportRepository,
      UserTokenService userTokenService,
      MetricsService metricsService,
      ObjectMapper objectMapper) {
    this(palmRecordRepository, monthlyReportRepository, userTokenService, metricsService, null, objectMapper);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public PalmRecordService(
      PalmRecordRepository palmRecordRepository,
      MonthlyReportRepository monthlyReportRepository,
      UserTokenService userTokenService,
      MetricsService metricsService,
      PhotoStorageService photoStorageService,
      ObjectMapper objectMapper) {
    this.palmRecordRepository = palmRecordRepository;
    this.monthlyReportRepository = monthlyReportRepository;
    this.userTokenService = userTokenService;
    this.metricsService = metricsService;
    this.photoStorageService = photoStorageService;
    this.objectMapper = objectMapper;
  }

  public WeeklyRecordResponse submitWeeklyRecord(com.palmistrylab.api.record.dto.WeeklyRecordRequest request) {
    String userId = userTokenService.requireValidUserId(request.userId());
    com.palmistrylab.api.common.ImageConstraints.requireReasonableImage(request.imageData());
    LocalDate recordDate = parseRecordDate(request.recordDate());
    String mode = com.palmistrylab.api.common.TextUtils.normalizeMode(request.recordMode(), "quick");
    String tracesJson = serializeTraces(request.traces());

    int energyLevel = buildScore(userId, recordDate.toString(), "energy");
    int wisdomActive = buildScore(userId, recordDate.toString(), "wisdom");
    int emotionWave = buildScore(userId, recordDate.toString(), "emotion");

    Optional<PalmRecordRepository.EnergySnapshot> lastRecord = palmRecordRepository
        .findFirstByUserIdAndRecordDateLessThanOrderByRecordDateDescCreatedAtDesc(userId, recordDate);
    String compareHint = buildCompareHint(lastRecord.orElse(null), energyLevel, wisdomActive, emotionWave);
    String aiSummary = buildWeeklySummary(mode, energyLevel, wisdomActive, emotionWave, compareHint, request.traces());

    PalmRecordEntity saved = palmRecordRepository.save(new PalmRecordEntity(
        userId,
        "single",
        mode,
        photoStorageService == null ? null : photoStorageService.saveDataUrl(request.imageData()),
        tracesJson,
        energyLevel,
        wisdomActive,
        emotionWave,
        aiSummary,
        null,
        recordDate,
        Instant.now()));

    YearMonth month = YearMonth.from(recordDate);
    long monthCount = palmRecordRepository.countByUserIdAndRecordDateBetween(
        userId,
        month.atDay(1),
        month.atEndOfMonth());
    metricsService.recordEvent("weekly_record", "REC-" + saved.getId(), mode);

    return new WeeklyRecordResponse(
        String.valueOf(saved.getId()),
        userId,
        mode,
        recordDate.toString(),
        energyLevel,
        wisdomActive,
        emotionWave,
        aiSummary,
        compareHint,
        toRuneColor(energyLevel),
        monthCount >= MONTH_TARGET,
        (int) monthCount,
        MONTH_TARGET);
  }

  public CalendarResponse getCalendar(String userId, String yearMonth) {
    userTokenService.requireValidUserId(userId);
    YearMonth month = parseYearMonth(yearMonth);
    LocalDate start = month.atDay(1);
    LocalDate end = month.atEndOfMonth();
    List<PalmRecordRepository.RecordSummary> rows =
        palmRecordRepository.findByUserIdAndRecordDateBetweenOrderByRecordDateAscCreatedAtAsc(
        userId,
        start,
        end);

    List<CalendarDayRecord> records = new ArrayList<>();
    List<EnergyTrendPoint> trend = new ArrayList<>();
    for (PalmRecordRepository.RecordSummary row : rows) {
      records.add(new CalendarDayRecord(
          row.getRecordDate().toString(),
          toRuneColor(row.getEnergyLevel()),
          row.getEnergyLevel(),
          row.getWisdomActive(),
          row.getEmotionWave(),
          row.getRecordMode(),
          row.getAiSummary()));
      trend.add(new EnergyTrendPoint(row.getRecordDate().toString(), row.getEnergyLevel()));
    }

    return new CalendarResponse(
        userId,
        month.format(YEAR_MONTH_FMT),
        rows.size(),
        MONTH_TARGET,
        records,
        trend);
  }

  public RecordDetailResponse getRecordDetail(String userId, String date) {
    userTokenService.requireValidUserId(userId);
    LocalDate recordDate = parseRecordDate(date);
    PalmRecordRepository.RecordSummary row = palmRecordRepository
        .findFirstByUserIdAndRecordDateOrderByCreatedAtDesc(userId, recordDate)
        .orElseThrow(() -> new IllegalArgumentException("该日期暂无记录: " + recordDate));
    return new RecordDetailResponse(
        String.valueOf(row.getId()),
        row.getUserId(),
        row.getRecordDate().toString(),
        row.getRecordMode(),
        row.getEnergyLevel(),
        row.getWisdomActive(),
        row.getEmotionWave(),
        row.getAiSummary(),
        row.getUserNote(),
        toRuneColor(row.getEnergyLevel()));
  }

  public UpdateRecordNoteResponse updateRecordNote(com.palmistrylab.api.record.dto.UpdateRecordNoteRequest request) {
    userTokenService.requireValidUserId(request.userId());
    long recordId;
    try {
      recordId = Long.parseLong(request.recordId());
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("记录ID无效: " + request.recordId());
    }

    PalmRecordEntity row = palmRecordRepository.findById(recordId)
        .orElseThrow(() -> new IllegalArgumentException("记录不存在: " + request.recordId()));
    if (!request.userId().equals(row.getUserId())) {
      throw new IllegalArgumentException("无权修改他人记录");
    }
    row.setUserNote(request.userNote());
    palmRecordRepository.save(row);
    return new UpdateRecordNoteResponse(true, request.recordId(), request.userNote());
  }

  /** 月报 upsert：事务 + 悲观锁读，防止并发首查产生重复月报行。 */
  @Transactional
  public MonthlyReportResponse getMonthlyReport(String userId, String yearMonthText) {
    userTokenService.requireValidUserId(userId);
    YearMonth yearMonth = parseYearMonth(yearMonthText);
    LocalDate start = yearMonth.atDay(1);
    LocalDate end = yearMonth.atEndOfMonth();

    List<PalmRecordRepository.RecordSummary> rows =
        palmRecordRepository.findByUserIdAndRecordDateBetweenOrderByRecordDateAscCreatedAtAsc(
        userId,
        start,
        end);

    List<EnergyTrendPoint> trend = new ArrayList<>();
    for (PalmRecordRepository.RecordSummary row : rows) {
      trend.add(new EnergyTrendPoint(row.getRecordDate().toString(), row.getEnergyLevel()));
    }

    String dominant = calcDominantEnergy(rows);
    String reportText = buildMonthlyReportText(yearMonth.format(YEAR_MONTH_FMT), dominant, rows);
    String trendJson;
    try {
      trendJson = objectMapper.writeValueAsString(trend);
    } catch (Exception ex) {
      trendJson = "[]";
    }

    Optional<MonthlyReportEntity> existing = monthlyReportRepository
        .findFirstByUserIdAndYearMonth(userId, yearMonth.format(YEAR_MONTH_FMT));
    MonthlyReportEntity entity;
    if (existing.isPresent()) {
      entity = existing.get();
    } else {
      entity = new MonthlyReportEntity(
          userId,
          yearMonth.format(YEAR_MONTH_FMT),
          0,
          dominant,
          trendJson,
          reportText,
          Instant.now());
    }

    entity.setRecordCount(rows.size());
    entity.setDominantEnergy(dominant);
    entity.setEnergyTrendJson(trendJson);
    entity.setReportText(reportText);
    entity.setCreatedAt(Instant.now());
    monthlyReportRepository.save(entity);

    return new MonthlyReportResponse(
        userId,
        yearMonth.format(YEAR_MONTH_FMT),
        rows.size(),
        dominant,
        trend,
        reportText,
        rows.size() >= MONTH_TARGET);
  }

  private int buildScore(String userId, String date, String domain) {
    int hash = Math.abs((userId + "#" + date + "#" + domain).hashCode());
    return 1 + (hash % 10);
  }

  private String toRuneColor(int energyLevel) {
    if (energyLevel >= 7) {
      return "gold";
    }
    if (energyLevel >= 4) {
      return "blue";
    }
    return "purple";
  }

  private String buildCompareHint(PalmRecordRepository.EnergySnapshot previous, int energy, int wisdom, int emotion) {
    if (previous == null) {
      return "这是你的首次周记录，已建立能量基线。";
    }
    String energyTrend = trendWord(energy - previous.getEnergyLevel(), "生命线能量");
    String wisdomTrend = trendWord(wisdom - previous.getWisdomActive(), "智慧线活跃度");
    String emotionTrend = trendWord(emotion - previous.getEmotionWave(), "感情线波动");
    return "相较于上次记录，" + energyTrend + "，" + wisdomTrend + "，" + emotionTrend + "。";
  }

  private String trendWord(int delta, String metric) {
    if (delta >= 2) {
      return metric + "明显提升";
    }
    if (delta <= -2) {
      return metric + "有所回落";
    }
    return metric + "保持平稳";
  }

  private String buildWeeklySummary(
      String mode,
      int energy,
      int wisdom,
      int emotion,
      String compareHint,
      PalmLineTraces traces) {
    String energyText = bucketText(energy, "精力充沛", "平稳运行", "略显透支");
    String wisdomText = bucketText(wisdom, "思维敏捷", "灵感潜伏", "需要休息");
    String emotionText = bucketText(emotion, "情绪稳定", "渴望连接", "较为敏感");

    String traceBonus = traces == null ? "本次为快速记录模式。" : "已结合你的手动画线进行精细校对。";
    String actionTip;
    if (wisdom >= 7) {
      actionTip = "赛博建议：本周智慧线活跃，适合把那件拖延的事处理掉。";
    } else if (emotion >= 7) {
      actionTip = "赛博建议：感情线显示渴望连接，不妨约一个好久没见的朋友。";
    } else if (energy <= 3) {
      actionTip = "赛博建议：生命线提示透支，给自己安排一个低负荷恢复日。";
    } else {
      actionTip = "赛博建议：保持当前节奏，小步快跑推进一个具体目标。";
    }

    return "本周手相波动报告：" + compareHint + "生命线气色「" + energyText + "」，智慧线活跃度「" + wisdomText
        + "」，感情线波动「" + emotionText + "」。" + traceBonus + actionTip + " (mode=" + mode + ")";
  }

  private String bucketText(int score, String high, String mid, String low) {
    if (score >= 7) {
      return high;
    }
    if (score >= 4) {
      return mid;
    }
    return low;
  }

  private String serializeTraces(PalmLineTraces traces) {
    if (traces == null) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(traces);
    } catch (Exception ex) {
      return null;
    }
  }

  private String calcDominantEnergy(List<PalmRecordRepository.RecordSummary> rows) {
    if (rows.isEmpty()) {
      return "休整之月";
    }
    double energyAvg = rows.stream().mapToInt(PalmRecordRepository.RecordSummary::getEnergyLevel).average().orElse(0);
    double wisdomAvg = rows.stream().mapToInt(PalmRecordRepository.RecordSummary::getWisdomActive).average().orElse(0);
    double emotionAvg = rows.stream().mapToInt(PalmRecordRepository.RecordSummary::getEmotionWave).average().orElse(0);
    if (wisdomAvg >= energyAvg && wisdomAvg >= emotionAvg) {
      return "创造之月";
    }
    if (emotionAvg >= energyAvg && emotionAvg >= wisdomAvg) {
      return "连接之月";
    }
    if (energyAvg >= 7) {
      return "突破之月";
    }
    return "休整之月";
  }

  private String buildMonthlyReportText(String yearMonth, String dominant, List<PalmRecordRepository.RecordSummary> rows) {
    if (rows.isEmpty()) {
      return yearMonth + " 暂无记录，建议每周至少拍一次手相，建立你的能量曲线。";
    }
    PalmRecordRepository.RecordSummary maxEnergy = rows.stream()
        .max(Comparator.comparingInt(PalmRecordRepository.RecordSummary::getEnergyLevel)).orElse(rows.get(0));
    PalmRecordRepository.RecordSummary minEnergy = rows.stream()
        .min(Comparator.comparingInt(PalmRecordRepository.RecordSummary::getEnergyLevel)).orElse(rows.get(0));
    return yearMonth + " 你完成了 " + rows.size() + " 次记录，本月主导能量为「" + dominant + "」。"
        + "高峰出现在 " + maxEnergy.getRecordDate() + "，低谷出现在 " + minEnergy.getRecordDate()
        + "。建议围绕高峰时段安排关键任务，在低谷期留出恢复窗口。";
  }

  private LocalDate parseRecordDate(String dateText) {
    if (dateText == null || dateText.isBlank()) {
      return LocalDate.now();
    }
    return LocalDate.parse(dateText);
  }

  private YearMonth parseYearMonth(String ymText) {
    if (ymText == null || ymText.isBlank()) {
      return YearMonth.now();
    }
    return YearMonth.parse(ymText, YEAR_MONTH_FMT);
  }
}
