package com.palmistrylab.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.palmistrylab.api.entity.AppEventEntity;
import com.palmistrylab.api.entity.MonthlyReportEntity;
import com.palmistrylab.api.entity.PalmRecordEntity;
import com.palmistrylab.api.entity.SessionRecordEntity;
import com.palmistrylab.api.model.ApiDtos;
import com.palmistrylab.api.repository.AppEventRepository;
import com.palmistrylab.api.repository.MonthlyReportRepository;
import com.palmistrylab.api.repository.PalmRecordRepository;
import com.palmistrylab.api.repository.SessionRecordRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
public class PalmistryService {

  private static final String SLOGAN = "你的互联网手相搭子，科学聊玄学";
  private static final String WECHAT_ID = "CyberPalm-Master";
  private static final Pattern SENTENCE_SPLIT = Pattern.compile("[。！？!?\\n]+\\s*");
  private static final DateTimeFormatter YEAR_MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

  private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

  private final SessionRecordRepository sessionRecordRepository;
  private final AppEventRepository appEventRepository;
  private final PalmRecordRepository palmRecordRepository;
  private final MonthlyReportRepository monthlyReportRepository;
  private final LlmClientService llmClientService;
  private final ObjectMapper objectMapper;

  public PalmistryService(
      SessionRecordRepository sessionRecordRepository,
      AppEventRepository appEventRepository,
      PalmRecordRepository palmRecordRepository,
      MonthlyReportRepository monthlyReportRepository,
      LlmClientService llmClientService,
      ObjectMapper objectMapper) {
    this.sessionRecordRepository = sessionRecordRepository;
    this.appEventRepository = appEventRepository;
    this.palmRecordRepository = palmRecordRepository;
    this.monthlyReportRepository = monthlyReportRepository;
    this.llmClientService = llmClientService;
    this.objectMapper = objectMapper;
  }

  public ApiDtos.PalmAnalyzeResponse analyzePalm(ApiDtos.AnalyzePalmRequest request) {
    String sessionId = "PALM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    int variant = Math.abs(sessionId.hashCode()) % 5;
    String handType = pickHandType(sessionId);
    List<String> tags = pickPersonalityTags(handType);
    String imageData = request.imageData();
    String recordMode = normalizeMode(request.recordMode(), "standard");

    List<ApiDtos.PalmLineSummary> freeOverview = pickFreeOverview(handType, variant);
    String rareMark = pickRareMark(sessionId);
    String teaser = "检测到稀有印记「" + rareMark + "」，完整解析可在报告页查询";
    TracePromptBundle tracePromptBundle = buildTracePromptBundle(request.traces());
    boolean traceConfirmed = tracePromptBundle.confirmed();
    String traceFeatureSummary = tracePromptBundle.summaryText();

    String overallSummary = pickOverallSummary(handType, variant);
    String careerTip = pickCareerTip(handType, variant);
    String loveTip = pickLoveTip(handType, variant);
    String healthTip = pickHealthTip(handType, variant);
    String luckyPeriod = pickLuckyPeriod(variant);

    if (traceConfirmed) {
      freeOverview = buildOverviewFromTraceFeatures(tracePromptBundle.features());
      teaser = "已根据你亲手描摹的掌纹生成解读，完整趋势可在报告页继续解锁。";
    }
    if (!"standard".equals(recordMode) && !"single".equals(recordMode)) {
      teaser = "已进入每周记录模式，本次报告更聚焦短期状态波动。";
    }

    boolean llmUsed = false;
    String llmStatus = "fallback_default";
    boolean llmEnhancedAvailable = false;

    LlmPalmOverviewResult llmResult = generatePalmOverviewWithLlm(
        handType, tags, rareMark, imageData, recordMode, tracePromptBundle.promptBlock());
    llmUsed = llmResult.used();
    llmStatus = llmResult.status();
    LlmPalmOverview llmPalmOverview = llmResult.overview();
    if (llmPalmOverview != null) {
      llmEnhancedAvailable = true;
      if (llmPalmOverview.personalityTags() != null && llmPalmOverview.personalityTags().size() >= 3) {
        tags = llmPalmOverview.personalityTags();
      }
      if (llmPalmOverview.freeOverview() != null && llmPalmOverview.freeOverview().size() >= 3) {
        freeOverview = llmPalmOverview.freeOverview();
      }
      if (llmPalmOverview.teaser() != null && !llmPalmOverview.teaser().isBlank()) {
        teaser = llmPalmOverview.teaser();
      }
      if (llmPalmOverview.overallSummary() != null && !llmPalmOverview.overallSummary().isBlank()) {
        overallSummary = llmPalmOverview.overallSummary();
      }
      if (llmPalmOverview.careerTip() != null && !llmPalmOverview.careerTip().isBlank()) {
        careerTip = llmPalmOverview.careerTip();
      }
      if (llmPalmOverview.loveTip() != null && !llmPalmOverview.loveTip().isBlank()) {
        loveTip = llmPalmOverview.loveTip();
      }
      if (llmPalmOverview.healthTip() != null && !llmPalmOverview.healthTip().isBlank()) {
        healthTip = llmPalmOverview.healthTip();
      }
      if (llmPalmOverview.luckyPeriod() != null && !llmPalmOverview.luckyPeriod().isBlank()) {
        luckyPeriod = llmPalmOverview.luckyPeriod();
      }
    }

    Instant now = Instant.now();
    sessions.put(sessionId, new SessionState(sessionId, handType, rareMark, now));
    sessionRecordRepository.save(new SessionRecordEntity(sessionId, "SINGLE", handType, rareMark, now));
    trackEventInternal("single_analyze", sessionId, request.source());

    if (llmUsed) {
      trackEventInternal("llm_success", sessionId, "palm_analyze");
    }

    return new ApiDtos.PalmAnalyzeResponse(
        sessionId, handType, tags, freeOverview, rareMark, teaser, SLOGAN,
        traceConfirmed, traceFeatureSummary, llmUsed, llmStatus,
        overallSummary, careerTip, loveTip, healthTip, luckyPeriod,
        llmEnhancedAvailable);
  }

  public ApiDtos.UnlockDeepResponse unlockDeep(String sessionId) {
    SessionState session = requireSession(sessionId);
    trackEventInternal("single_ad_unlock", sessionId, "rewarded_video");
    List<ApiDtos.DeepSection> sections = new ArrayList<>();
    sections.add(new ApiDtos.DeepSection(
        "深度趋势 · 感情线",
        "未来90天你的关系走势是先观察、后升温。前3周更适合建立边界和节奏，中段会出现一次高质量沟通窗口，若把模糊期待说清，关系稳定性会明显提升。",
        "每周固定一次20分钟复盘：先讲感受，再讲需求，最后约定下一步动作。"));
    sections.add(new ApiDtos.DeepSection(
        "深度趋势 · 智慧线",
        "你近期会在两条机会路径之间反复比较：一条偏稳定现金流，一条偏成长空间。你的优势是信息整合快，但易在细节里过拟合，导致迟迟不拍板。",
        "采用7天双轨试运行，记录投入产出比与心智负担，再做最终选择。"));
    sections.add(new ApiDtos.DeepSection(
        "深度趋势 · 生命线",
        "你的体能底盘不错，但高压期后会出现延迟性疲劳。若连续晚睡3天，专注质量会在第4天明显下滑，进而影响判断稳定性与执行效率。",
        "把高强度任务集中在上午，晚间保留1小时低负荷收尾和恢复拉伸。"));

    List<ApiDtos.DeepSection> llmSections = generateSingleDeepWithLlm(session);
    if (llmSections != null && llmSections.size() >= 2) {
      sections = llmSections;
    }

    return new ApiDtos.UnlockDeepResponse(session.sessionId(), true, "AD_REWARDED", sections);
  }

  public ApiDtos.RareMarkQueryResponse queryRareMark(String sessionId) {
    SessionState session = requireSession(sessionId);
    int remainQuota = Math.abs(sessionId.hashCode() % 35) + 5;
    trackEventInternal("rare_mark_query", sessionId, "report_page");

    return new ApiDtos.RareMarkQueryResponse(
        sessionId,
        session.rareMark(),
        WECHAT_ID,
        remainQuota,
        "印记解析由专属大师提供，添加企业微信后自动下发图文报告");
  }

  public ApiDtos.CpAnalyzeResponse analyzeCp(ApiDtos.CpAnalyzeRequest request) {
    String cpSessionId = "CP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    double score = buildCpScore(request.userA().mbti(), request.userB().mbti());
    String combo = buildComboName(request.userA().handType(), request.userB().handType());

    List<ApiDtos.CpDimension> dimensions = List.of(
        new ApiDtos.CpDimension("感情线趋同度", clamp((int) Math.round(score + 3))),
        new ApiDtos.CpDimension("智慧线互补性", clamp((int) Math.round(score - 4))),
        new ApiDtos.CpDimension("生活节奏契合度", clamp((int) Math.round(score - 1))));

    String interpretation = "你们属于「" + combo + "」组合，一方负责搭框架，一方负责点亮细节。";
    String tip = "相处 Tips：当逻辑线遇上感性分叉时，先共识目标，再讨论路径。";

    LlmCpNarrative llmCpNarrative = generateCpNarrativeWithLlm(request, combo, score);
    if (llmCpNarrative != null) {
      if (llmCpNarrative.comboName() != null && !llmCpNarrative.comboName().isBlank()) {
        combo = llmCpNarrative.comboName();
      }
      if (llmCpNarrative.interpretation() != null && !llmCpNarrative.interpretation().isBlank()) {
        interpretation = llmCpNarrative.interpretation();
      }
      if (llmCpNarrative.tip() != null && !llmCpNarrative.tip().isBlank()) {
        tip = llmCpNarrative.tip();
      }
    }

    Instant now = Instant.now();
    sessions.put(cpSessionId, new SessionState(cpSessionId, combo, "双生螺旋", now));
    sessionRecordRepository.save(new SessionRecordEntity(cpSessionId, "CP", combo, "双生螺旋", now));
    trackEventInternal("cp_analyze", cpSessionId, "cp_page");

    return new ApiDtos.CpAnalyzeResponse(cpSessionId, score, combo, dimensions, interpretation, tip);
  }

  public ApiDtos.UnlockDeepResponse unlockCpDeep(String cpSessionId) {
    requireSession(cpSessionId);
    trackEventInternal("cp_ad_unlock", cpSessionId, "rewarded_video");
    List<ApiDtos.DeepSection> sections = List.of(
        new ApiDtos.DeepSection(
            "摩擦点预警",
            "你们在高压场景下容易出现表达时差：一方要结论，一方要共情。",
            "把争议拆成事实、情绪、方案三段式，冲突会明显降低。"),
        new ApiDtos.DeepSection(
            "关系升级窗口",
            "未来三周有两次关键升温窗口，分别在周三晚和周末午后。",
            "固定一周一次深度沟通，优先讨论边界和期待。"));

    return new ApiDtos.UnlockDeepResponse(cpSessionId, true, "AD_REWARDED", sections);
  }

  public ApiDtos.TrackEventResponse trackEvent(ApiDtos.TrackEventRequest request) {
    trackEventInternal(request.eventName(), request.sessionId(), request.channel());
    return new ApiDtos.TrackEventResponse(true, request.eventName());
  }

  public ApiDtos.MetricsSummaryResponse metricsSummary() {
    long totalSingle = sessionRecordRepository.countBySessionType("SINGLE");
    long totalCp = sessionRecordRepository.countBySessionType("CP");
    long totalWeekly = palmRecordRepository.countByRecordType("single");
    long totalShare = appEventRepository.countByEventName("share_card");
    long totalAdUnlock = appEventRepository.countByEventName("single_ad_unlock")
        + appEventRepository.countByEventName("cp_ad_unlock");
    long totalRareMark = appEventRepository.countByEventName("rare_mark_query");

    double singleBase = totalSingle == 0 ? 1.0 : totalSingle;
    double cpBase = totalSingle == 0 ? 1.0 : totalSingle;
    double sessionBase = (totalSingle + totalCp) == 0 ? 1.0 : (double) (totalSingle + totalCp);

    double adUnlockRate = round2(totalAdUnlock / sessionBase);
    double cpInitiateRate = round2(totalCp / cpBase);
    double shareRate = round2(totalShare / sessionBase);
    double privateLeadRate = round2(totalRareMark / singleBase);

    return new ApiDtos.MetricsSummaryResponse(
        totalSingle,
        totalCp,
        totalWeekly,
        totalShare,
        totalAdUnlock,
        totalRareMark,
        adUnlockRate,
        cpInitiateRate,
        shareRate,
        privateLeadRate);
  }

  public ApiDtos.WeeklyRecordResponse submitWeeklyRecord(ApiDtos.WeeklyRecordRequest request) {
    LocalDate recordDate = parseRecordDate(request.recordDate());
    String mode = normalizeMode(request.recordMode(), "quick");
    String tracesJson = serializeTraces(request.traces());

    int energyLevel = buildScore(request.userId(), recordDate.toString(), "energy");
    int wisdomActive = buildScore(request.userId(), recordDate.toString(), "wisdom");
    int emotionWave = buildScore(request.userId(), recordDate.toString(), "emotion");

    Optional<PalmRecordEntity> lastRecord = palmRecordRepository
        .findFirstByUserIdAndRecordDateLessThanOrderByRecordDateDescCreatedAtDesc(request.userId(), recordDate);
    String compareHint = buildCompareHint(lastRecord.orElse(null), energyLevel, wisdomActive, emotionWave);
    String aiSummary = buildWeeklySummary(mode, energyLevel, wisdomActive, emotionWave, compareHint, request.traces());

    PalmRecordEntity saved = palmRecordRepository.save(new PalmRecordEntity(
        request.userId(),
        "single",
        mode,
        request.imageData(),
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
        request.userId(),
        month.atDay(1),
        month.atEndOfMonth());
    trackEventInternal("weekly_record", "REC-" + saved.getId(), mode);

    return new ApiDtos.WeeklyRecordResponse(
        String.valueOf(saved.getId()),
        request.userId(),
        mode,
        recordDate.toString(),
        energyLevel,
        wisdomActive,
        emotionWave,
        aiSummary,
        compareHint,
        toRuneColor(energyLevel),
        monthCount >= 4,
        (int) monthCount,
        4);
  }

  public ApiDtos.CalendarResponse getCalendar(String userId, String yearMonth) {
    YearMonth month = parseYearMonth(yearMonth);
    LocalDate start = month.atDay(1);
    LocalDate end = month.atEndOfMonth();
    List<PalmRecordEntity> rows = palmRecordRepository.findByUserIdAndRecordDateBetweenOrderByRecordDateAscCreatedAtAsc(
        userId,
        start,
        end);

    List<ApiDtos.CalendarDayRecord> records = new ArrayList<>();
    List<ApiDtos.EnergyTrendPoint> trend = new ArrayList<>();
    for (PalmRecordEntity row : rows) {
      records.add(new ApiDtos.CalendarDayRecord(
          row.getRecordDate().toString(),
          toRuneColor(row.getEnergyLevel()),
          row.getEnergyLevel(),
          row.getWisdomActive(),
          row.getEmotionWave(),
          row.getRecordMode(),
          row.getAiSummary()));
      trend.add(new ApiDtos.EnergyTrendPoint(row.getRecordDate().toString(), row.getEnergyLevel()));
    }

    return new ApiDtos.CalendarResponse(
        userId,
        month.format(YEAR_MONTH_FMT),
        rows.size(),
        4,
        records,
        trend);
  }

  public ApiDtos.RecordDetailResponse getRecordDetail(String userId, String date) {
    LocalDate recordDate = parseRecordDate(date);
    PalmRecordEntity row = palmRecordRepository
        .findFirstByUserIdAndRecordDateOrderByCreatedAtDesc(userId, recordDate)
        .orElseThrow(() -> new IllegalArgumentException("该日期暂无记录: " + recordDate));
    return new ApiDtos.RecordDetailResponse(
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

  public ApiDtos.UpdateRecordNoteResponse updateRecordNote(ApiDtos.UpdateRecordNoteRequest request) {
    long recordId;
    try {
      recordId = Long.parseLong(request.recordId());
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("记录ID无效: " + request.recordId());
    }

    PalmRecordEntity row = palmRecordRepository.findById(recordId)
        .orElseThrow(() -> new IllegalArgumentException("记录不存在: " + request.recordId()));
    row.setUserNote(request.userNote());
    palmRecordRepository.save(row);
    return new ApiDtos.UpdateRecordNoteResponse(true, request.recordId(), request.userNote());
  }

  public ApiDtos.MonthlyReportResponse getMonthlyReport(String userId, String yearMonthText) {
    YearMonth yearMonth = parseYearMonth(yearMonthText);
    LocalDate start = yearMonth.atDay(1);
    LocalDate end = yearMonth.atEndOfMonth();

    List<PalmRecordEntity> rows = palmRecordRepository.findByUserIdAndRecordDateBetweenOrderByRecordDateAscCreatedAtAsc(
        userId,
        start,
        end);

    List<ApiDtos.EnergyTrendPoint> trend = new ArrayList<>();
    for (PalmRecordEntity row : rows) {
      trend.add(new ApiDtos.EnergyTrendPoint(row.getRecordDate().toString(), row.getEnergyLevel()));
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

    return new ApiDtos.MonthlyReportResponse(
        userId,
        yearMonth.format(YEAR_MONTH_FMT),
        rows.size(),
        dominant,
        trend,
        reportText,
        rows.size() >= 4);
  }

  public ApiDtos.LlmPingResponse llmPing(String prompt) {
    if (!llmClientService.isAvailable()) {
      return new ApiDtos.LlmPingResponse(false, "LLM not enabled");
    }

    try {
      String output = llmClientService.chat("你是一个简洁助手。", prompt);
      return new ApiDtos.LlmPingResponse(true, output);
    } catch (Exception ex) {
      return new ApiDtos.LlmPingResponse(false, "LLM call failed: " + ex.getMessage());
    }
  }

  private SessionState requireSession(String sessionId) {
    SessionState session = sessions.get(sessionId);
    if (session == null) {
      throw new IllegalArgumentException("会话不存在或已过期: " + sessionId);
    }
    return session;
  }

  private List<ApiDtos.PalmLineSummary> pickFreeOverview(String handType, int v) {
    return switch (handType) {
      case "火型手" -> List.of(fireLove[v], fireWisdom[v], fireLife[v]);
      case "水型手" -> List.of(waterLove[v], waterWisdom[v], waterLife[v]);
      case "风型手" -> List.of(airLove[v], airWisdom[v], airLife[v]);
      default -> List.of(earthLove[v], earthWisdom[v], earthLife[v]);
    };
  }

  private static final ApiDtos.PalmLineSummary[] fireLove = {
    new ApiDtos.PalmLineSummary("感情线", "上扬+深", "你的感情起步迅猛但容易被节奏差异绊住，核心课题是学会在热度退去后仍保持连接。", "火型手的感情线往往起于掌缘高位，走势偏上扬且力度较深，意味着你爱得真、投入快。但火型天性追求即时反馈，当关系进入平稳期时容易感到乏味。你的挑战是在热度降温后找到一个稳定节奏，而不是重启新循环。近期感情关键词：先稳后进。", "每周选1次30分钟高质量对话，谈感受而不是解决方案。", "3-5月是感情升温窗口"),
    new ApiDtos.PalmLineSummary("感情线", "短曲+锐起", "你的情感爆发力强但持续性偏弱，适合有节奏的感性表达而非全天候黏合。", "短曲型感情线说明你在感情中更像火焰——来势猛、消退也快。这并非缺点，而是你的自然节律。重要的是找到跟你节奏互补的伴侣：既能接住你的热情高峰，也能理解你需要喘息空间的时候。建议你善用情感爆发期做深度连接，平稳期做自我补给。", "高热期多表达肯定，平淡期保留1次/周最低连接，避免断崖式降温。", "逢7号前后感情敏锐度最高"),
    new ApiDtos.PalmLineSummary("感情线", "上扬+末端分叉", "你在感情中同时追求真诚和自由，适合开放但不失深度的关系模式。", "感情线末端分叉代表你在亲密关系中同时有两条路径需要——既渴望深层情感连接，也需要保留自我空间。这让你的恋爱更立体，但也容易让伴侣误解为犹豫不决。最佳策略是在关系初期就明确表态：我要的是深度共鸣，不是全天候黏合。", "每次争吵后24小时内做1次复盘：我的需求是什么，而不是对方做错了什么。", "8-10月感情的稳定性达到高点"),
    new ApiDtos.PalmLineSummary("感情线", "深直+偏高", "你对待感情认真且极具行动力，一旦认定对方会全力以赴，但容易忽略情感磨合的耐心。", "深直的感情线显示你在感情中以行动表达优先，嘴上不说但做的很多。问题在于：你不是所有人都能匹配的节奏——快不代表深。建议你在投入前花2周做观察，确认对方也愿意给出同等的诚意再加速。", "每周1次刻意慢下来，用10分钟写感情笔记：这周我感受到了什么？", "月初到月中感情判断力最强"),
    new ApiDtos.PalmLineSummary("感情线", "长弧+轻微波动", "你的情感光谱较宽，既能深陷也能抽离，控制这种弹性就是你最大的感情优势。", "长弧感情线表示你在亲密关系中既不会完全失控，也不会过于疏离。关键在于波动期——当情绪起伏大时尽量不要做重大感情决定，而是给自己3天缓冲期。你的感情质量取决于你在波动期的自我管理能力。", "情绪波动时遵守3天冷静法则：第1天感受，第2天分析，第3天才做决定。", "满月后3天适合处理感情关键对话")
  };

  private static final ApiDtos.PalmLineSummary[] fireWisdom = {
    new ApiDtos.PalmLineSummary("智慧线", "平直+有力", "你的决策风格是快准狠，适合做需要即时判断力的工作，但容易在信息不足时过早拍板。", "火型手的智慧线走势偏直，代表你的思维路径清晰——从A到B不绕弯。这在执行力上极有优势，但当信息不完整时容易变成「先做再想」。最佳策略是：小事快速决策，大事强制自己列出正反各3条再行动。", "重要决策前强制列出3条反对理由，48小时后拍板。", "每月20号前后思维清晰度最高"),
    new ApiDtos.PalmLineSummary("智慧线", "末端分叉+微弧", "你同时具备理性分析与创意联想能力，信息量大时反而决策变慢。", "分叉智慧线让你既能用公式算账，也能用比喻讲故事。这让你的表达力极强，但也带来一个典型困境——选项太多时容易冻结。你的破解方法是：任何决策就用3指标对照法（成本、可行性、可逆性），每项打1-5分，总分最高的直接做。", "用3×3矩阵法：每个选项列出成本/可行性/可逆性各1条证据，48小时内必须拍板。", "逢周二上午9-11点决策力最高"),
    new ApiDtos.PalmLineSummary("智慧线", "长+清晰", "你是一个深度思考者，能在复杂信息中快速抓主线，但容易想太多导致行动延迟。", "智慧线长且清晰意味着你天生善于结构化思考——这对规划和策略工作极有优势。但火型手的行动力配上深度思考力，容易导致「念头太多、出手太少」。你需要一个物理机制来打破这个循环：设定决策倒计时，到期就必须选一条路走。", "任何犹豫超过3天的决定，列出1-10紧迫性评分，7分以上的立刻执行。", "月中决策质量最佳"),
    new ApiDtos.PalmLineSummary("智慧线", "上弯+微开叉", "你擅长将理论快速转化为行动，但要避免为了速度牺牲细节。", "智慧线上弯代表你习惯先有框架再填内容，微开叉意味着你在关键节点会同时考虑两条路径。这让你在项目管理类工作中极有优势——但当你为了追求进度跳过细节时，后期容易返工。建议你在每次快进之前做一个5分钟的漏洞扫描。", "每周五做1次5分钟漏洞扫描：这周有没有因为赶进度跳过了什么？", "月初逻辑推理能力最强"),
    new ApiDtos.PalmLineSummary("智慧线", "偏直+刚健", "你倾向于用最短路径解决问题，直觉判断往往是对的，但需要留出验证空间。", "偏直的智慧线代表你的思维模型是高效率的——你会本能地否定低概率路径，直取核心解法。但火型手的人容易对自己的判断力过度自信，建议你在直觉告诉你「就这个了」的时候，追加一个10分钟的验证步骤：有没有我没看到的盲点？", "直觉决策后留10分钟验证窗口，列出2个可能被忽略的风险点。", "上午10-12点直觉判断力最准")
  };

  private static final ApiDtos.PalmLineSummary[] fireLife = {
    new ApiDtos.PalmLineSummary("生命线", "深弧+扩张", "你的精力输出模式是爆发型的——能高强度冲刺但恢复周期不容忽视。", "深扩张弧生命线显示你有很强的能量储备，但也意味着你的消耗模式是集中爆发。你可能连续3天超负荷运转后第4天突然崩盘，这不是你意志力不够，而是你的恢复曲线就是如此。最佳策略是把高强度工作时间控制在3天以内，第4天主动做低强度收尾。", "高强度3天后强制安排1个恢复日，每天保留30分钟低强度收尾时间。", "每月1-10号精力最充沛"),
    new ApiDtos.PalmLineSummary("生命线", "中深+饱满弧", "你先天精力不错但容易透支不自觉，信号是连续3天后突然走神或效率骤降。", "饱满弧生命线说明你的精力底盘是稳固的，但线条中段偶有变浅——暗示你在持续高压后容易出现延迟性疲劳。表现不是体力崩溃，而是专注力和判断力悄悄下滑。关键建议：如果连续工作多天后发现自己开始走神、做低级错误，这已经在透支了。", "每天下午保留1小时低负荷时间，每周至少2天保证8小时以上睡眠。", "上旬体能高峰，下旬需要主动减速"),
    new ApiDtos.PalmLineSummary("生命线", "紧实+偏短", "你的精力模型是短高型——集中在关键时段爆发，而非长时间匀速输出。", "偏短的紧实生命线代表你在短时间内有极高的爆发力，但拉长战线后稳定性会下降。这并非劣势——很多顶尖执行者都是短高型体质。关键是在高爆发期做好恢复储备：运动、睡眠、刻意放空。你的最佳节奏是3天冲刺+1天恢复。", "3天冲刺+1天恢复节奏，冲刺期每天30分钟有氧保持体能底座。", "逢整周第3-5天为最佳冲刺窗口"),
    new ApiDtos.PalmLineSummary("生命线", "宽弧+偏浅", "你精力范围宽但有波动区间，核心是要识别自己的低谷日并主动避让。", "宽弧偏浅的生命线表示你在大部分时间有稳定的精力输出，但有特定日子会明显下滑。如果你发现自己某天早上起来就觉得累，这不是偷懒——这是你的身体在申请恢复日。最佳策略是在自己精力高峰的关键日子做重要决策，低谷日只做轻量工作。", "记录1周精力曲线，找出自己的高峰日和低谷日，高峰做重活、低谷做轻活。", "整月前半段体能综合表现最佳"),
    new ApiDtos.PalmLineSummary("生命线", "弧正+中深", "你的恢复能力是你的隐形资产——短时间高强度后恢复快，但长期连续透支会侵蚀这个优势。", "弧正中深说明你精力输出和恢复都处于中上水平，短期冲刺后反弹很快。但这个优势容易让你高估自己的耐力——连续3周高压后第4周开始小问题频出，就是身体在报警。核心策略：每3周安排1整天的主动恢复日（不工作、不社交、纯休息）。", "每3周安排1个主动恢复日，日常保留1小时运动+1小时放空。", "每周一至周三精力最稳定，适合攻坚性工作")
  };

  private static final ApiDtos.PalmLineSummary[] waterLove = {
    new ApiDtos.PalmLineSummary("感情线", "长弧+微波动", "你天生对他人情绪敏锐，关系中最深的连接来自被理解而非被主动。", "水型手的感情线往往弧度饱满，代表你在关系中情感输出丰富。微波动意味着你对伴侣的状态变化极度敏感——一个微表情你就能感受到。这让你成为极好的倾听者和共情者，但也容易吸收对方情绪导致自己消耗。关键是建立情感边界：我可以理解你，但不必替你承担。", "每周1次主动表达自己的需求，而不是只回应对方的需求。", "2-4月感情共鸣度最高"),
    new ApiDtos.PalmLineSummary("感情线", "细长+柔和", "你在感情中会先观察、再投入，一旦认定会非常忠诚，但前期可能显得慢热。", "细长柔和型感情线说明你的情感节奏是慢热型——需要时间建立安全感和信任。这不是缺点，这是你在筛选值得深度投入的关系。当你真正打开后，你的忠诚度和情感深度会让伴侣感到非常踏实。最佳策略是在前2周保持自然节奏，不刻意加速也不刻意保留。", "前2周保持自然节奏，见面后给对方1条具体的感受反馈，让关系有锚定点。", "满月后1周适合处理感情关键对话"),
    new ApiDtos.PalmLineSummary("感情线", "深+偏直", "你对感情的判断偏理性但内核是感性的，外表冷静内心汹涌。", "感情线偏直的水型手有一种特殊张力：你看起来冷静，但内心体验极其丰富。你的伴侣可能误以为你不在意——因为你不会用剧烈方式表达。建议你学会用1-2句明确的感受表达替代全部的克制，这会让关系质变。", "每天对伴侣说1句有具体感受的话，替代全部忍耐和克制。", "月中到月末感情开放度自然增加"),
    new ApiDtos.PalmLineSummary("感情线", "偏高+微弧", "你对待感情有一套自己的标准，宁缺勿滥是本能而非策略。", "偏高型感情线表示你对亲密关系的期待基准较高——你不会因为孤独而凑合。这让你在感情中极其有底线，但也可能让你在好选项面前犹豫太久。建议你区分「非做不可的条件」和「可以商量的细节」，前者不妥协，后者给空间。", "列出3条感情的底线条件和3条可以商量的条件，择偶时严格对齐底线。", "3月和9月是感情关系进入新阶段的高峰期"),
    new ApiDtos.PalmLineSummary("感情线", "弯曲+分支", "你的情感世界较丰富，可能同时有多种关系类型需求——深谈对象、一起玩的朋友、稳定伴侣各有分工。", "分支型感情线说明你的情感需求的层次感很强——不是一段关系就能满足所有需求。这并不意味着你花心，而是你需要不同维度的连接。关键是让伴侣理解这一点，并在主关系中提供足够的安全感。", "每月安排1次深度情感对话，告诉伴侣你现在最需要的支持是什么类型的。", "月初到10号感情表达最自然流畅")
  };

  private static final ApiDtos.PalmLineSummary[] waterWisdom = {
    new ApiDtos.PalmLineSummary("智慧线", "弧弯+偏长", "你以直觉驱动思考，强项是快速感知他人需求和潜在动机。", "水型手的智慧线往往弧度较大，代表你的思维更多依赖直觉和经验模式而非纯逻辑推导。这让你在人际理解、创意洞察方面极有天赋，但在需要硬核数据分析的领域可能需要额外的校验步骤。最佳策略：相信第一直觉，但用数据验证。", "所有重要决定做完后追加1次数据验证：直觉告诉我什么，数据告诉我什么？", "雨季或降温时段直觉力最敏锐"),
    new ApiDtos.PalmLineSummary("智慧线", "中等+柔和弧", "你的思维模式是感受先行、逻辑补后——先有感觉再找论据。", "柔和弧智慧线表示你做判断的潜台词总是「这感觉对吗？」然后才去找逻辑支撑。这让你在消费类、体验类决策上极快且往往准确，但在涉及重大资源分配时可能因为「感觉好」而低估风险。强制自己做成本-收益表是破解之道。", "涉及金钱或时间投入的决策，先花5分钟写成本和收益各3条，再决定。", "每月7号前后决策偏差最小"),
    new ApiDtos.PalmLineSummary("智慧线", "下弯+偏深", "你是一个深度内省者，能在复杂情境中捕捉到别人忽略的微妙信号。", "下弯智慧线意味着你的思考不仅是直觉的，还带有深层内省——你会在安静时反复咀嚼信息，直到形成自己的判断。这让你在战略分析和趋势判断上极有优势，但容易因为持续内省而错过行动窗口。你需要一个外部提醒机制。", "设1个7天决策闹钟：这天必须做出决定，不管有没有想清楚。", "月中旬深度思考后产出质量最高"),
    new ApiDtos.PalmLineSummary("智慧线", "清晰+末端开叉", "你在深入研究一个领域后会自然延伸出跨界联想，这是你的创新力来源。", "末端开叉的智慧线说明你不仅有垂直深度，还有横向联想能力——这让你在跨学科、跨领域的工作中极有优势。但开叉也意味着你容易在几十个创意中飘移，需要定期收束回到核心目标上。", "每周一定义1个本周核心问题，所有创意延伸都围绕这个核心展开。", "周一上午灵感和逻辑平衡状态最好"),
    new ApiDtos.PalmLineSummary("智慧线", "偏短+有力", "你在关键时刻的判断力很强，但日常决策容易受情绪干扰。", "偏短有力的智慧线说明你的思维效率在紧急状态下极高，但在平静期反而容易犹豫。这很合理——你的直觉在压力下会被激活，平时缺乏压力信号时反而启动不了。策略是给自己制造适度的「人造紧急感」来推动日常决策。", "给每个日常决策设5分钟时限，超时就用第一直觉。", "每月下旬判断力最稳")
  };

  private static final ApiDtos.PalmLineSummary[] waterLife = {
    new ApiDtos.PalmLineSummary("生命线", "细长+柔弧", "你精力波动较大但恢复力强，核心是要学会识别自己的低谷期并主动避让。", "细长柔弧生命线意味着你的精力呈现波浪式起伏——而非平稳输出。在水型手中这尤为突出：你的创造力和情绪稳定性与精力高度关联。最佳策略是不与低谷期较劲，而是把重要事项排到高峰期。", "记录2周的精力曲线（1-10分），找出你的高峰日模式，把重要事项排到高峰日。", "每个自然月的头8天精力最稳定"),
    new ApiDtos.PalmLineSummary("生命线", "中弧+偏浅", "你的身体素质不错但情绪对精力影响很大——情绪低落时体能也跟着下降。", "偏浅中弧生命线显示你的身体状态受情绪驱动——开心时精力充沛，低落时整个人都没电。这在水型手中很常见。核心策略不是强制自己始终高兴，而是建立情绪-精力的防火墙：情绪低时做轻活，而不是硬扛。", "情绪低落日改为轻量工作模式，不勉强做高强度决策。固定睡眠时间不因心情改变。", "每月中旬后第1周情绪精力共振最好"),
    new ApiDtos.PalmLineSummary("生命线", "饱满+偏深", "你的精力底座稳固，但持续承压后容易通过情绪化反应来释放。", "饱满深色生命线说明你先天精力储备足够，问题不在体能而在分配方式。水型手的人容易把精力耗在「为别人操心」上——你不觉得累，但突然有一天会发现自己已经透支了3个月。建议你每月做1次精力审计：我在为谁消耗？有多少是为自己的？", "每月做1次精力审计表：列出你在为谁消耗精力，标注哪些可以 delegate 或 release。", "季初到季中精力最充沛"),
    new ApiDtos.PalmLineSummary("生命线", "弧宽+中途微变浅", "你对压力的耐受有一个阈值——越过阈值后健康会连续下滑。", "生命线中途微变浅暗示你在某个压力阈值后会出现一个拐点——之前还能扛，之后断崖式下滑。这个阈值因人而异，你需要找出自己的信号：失眠、食欲变化、注意力涣散都是前兆。关键是不要等到断崖，而是在信号出现时就减速。", "识别你的3个压力预警信号（如失眠、食欲变化、注意力涣散），出现任1个立刻安排半天恢复。", "每月第2-3周是耐压高点"),
    new ApiDtos.PalmLineSummary("生命线", "深弧+起端偏高", "你有较强的自我保护本能，会本能地在高压期降低输出以保全核心。", "起端偏高深弧生命线说明你的本能反应是保护性的——在压力到达前就会主动减速。这是很好的自保机制，但有时可能被误解为不上进。建议你向信任的人声明你的节奏模式，避免被误判为懈怠。", "向核心圈2-3人声明你的恢复节奏模式，避免因减速被误解为懈怠。", "全年前8个月恢复力最强")
  };

  private static final ApiDtos.PalmLineSummary[] airLove = {
    new ApiDtos.PalmLineSummary("感情线", "偏直+清晰", "你在感情中理性与感性并存，会用逻辑评估但最终靠直觉决策。", "风型手的感情线走势偏直，代表你在关系中习惯先看条件匹配度再走进情感层。这让你很少出现冲动型关系，但也可能让你在真正想投入时过度分析。最佳策略是：前3次接触充分了解，之后就关掉逻辑让直觉带路。", "前3次接触保持理性评估，第4次开始刻意关闭分析模式让直觉决定。", "4-6月感情筛选和深化期"),
    new ApiDtos.PalmLineSummary("感情线", "上扬+中等弧", "你对感情有一种恰到好处的热情——不过度也不冷淡，最容易让人感到舒适。", "中等弧上扬型感情线是风型手中最平衡的情感模式——你既能体会深情，也保持着必要的独立空间。这让你的伴侣感到安全而不压抑，自由而不失控。关键是在关系进入瓶颈期时，不要因为「看起来还行」就不做维护。", "每月1次刻意制造1个新体验（新餐厅、新路线、新话题），保持关系的新鲜感。", "逢季度交替月份感情最易突破"),
    new ApiDtos.PalmLineSummary("感情线", "长+微弧", "你的感情观成熟但容易陷入逻辑自洽——觉得不应该出问题就不沟通。", "长微弧感情线代表你在关系中很像一个「聪明的旁观者」——你看得清问题在哪，但容易觉得「以我们的理解力不需要说」。这是最大的陷阱：对方需要的往往不是理解，而是被看见。", "每月至少1次把「对方应该懂我的」替换为「我需要说出来」，直接表达感受。", "每周四感情开放度最高"),
    new ApiDtos.PalmLineSummary("感情线", "偏短+有力", "你在感情中一旦认定就火力全开，早期的判断力是你最大的资产。", "短有力感情线说明你在感情中的模式是精准制导——看准了就全力投入。这让你在关系中极具效率和诚意，但也需要在前期真的做好判断。建议你在前2周刻意放慢节奏，确认3个关键匹配点后再全力投入。", "前2周刻意放慢：确认价值观、生活节奏、沟通模式3个匹配点再深入。", "每月中旬感情判断力最清晰"),
    new ApiDtos.PalmLineSummary("感情线", "弧弯+分支", "你对感情有多种维度的需求，一个伴侣可能无法让你完全满足——但这也是你的魅力。", "分支型感情线说明你的情感需求是多通道的——你需要深度对话、轻松趣玩、共同成长并行。这让你在伴侣眼中是有趣的存在，但也让你容易因为某一维度的缺失而感到不满足。关键是识别当前最饿的那个维度，优先补给。", "识别当前最缺失的情感维度（深度对话/轻松趣玩/共同成长），本月优先补充这个。", "社交活跃期（月中周末后）感情连接最密集")
  };

  private static final ApiDtos.PalmLineSummary[] airWisdom = {
    new ApiDtos.PalmLineSummary("智慧线", "长直+平缓", "你的思维优势在于结构化——能把混乱信息快速整理成清晰框架。", "风型手的智慧线通常走势偏直偏长，代表你在面对信息过载时有天生的过滤能力——你本能地找到主线，忽略噪音。这让你在策略规划、数据分析、文案结构类工作中极有优势。但在需要跳跃式创意的场景中可能略显保守。", "每周花20分钟整理1个信息过载的领域，你的结构化能力是解决问题的钥匙。", "月初逻辑思维最清晰"),
    new ApiDtos.PalmLineSummary("智慧线", "清晰+末端微上扬", "你在理论到实践之间架桥的能力很强——能把想法落地成可执行方案。", "微上扬智慧线说明你不仅有想法，还有落地路径——这类人在团队中是极其核心的存在。但你也容易成为所有人的依赖对象，因为你说的方案总是最可行的。建议你定期评估：哪些是你的核心价值，哪些可以交给别人做？", "每季度评估1次：列出你目前在承担但可以delegate的任务，释放20%时间。", "周二到周四执行与思考的平衡状态最佳"),
    new ApiDtos.PalmLineSummary("智慧线", "末端分叉+中等弧", "你是双轨思维的代表——同时具备深度和广度，但也因此容易在两条路之间摇摆。", "分叉智慧线让你像双子一样同时在两条路径思考，这在创新和跨领域工作中极有优势。但分叉也带来选择困难——当你觉得两个方向都好时，需要强制自己做一个时间约束决策。", "双线项目最多并行2个，超出就强制排序：哪个能在7天内出结果就先做哪个。", "每次新月后1周双轨思维效率最高"),
    new ApiDtos.PalmLineSummary("智慧线", "偏短+锐利", "你在关键时刻的判断力非常强，日常决策偏好直觉但准确性不低。", "短锐智慧线说明你的认知风格是「快而准」——相比深度分析你更擅长快速判断。这在需要即时决策的场景中极有优势，但在策略规划中可能需要放慢节奏做二次验证。", "重大决策用直觉定方向，用20分钟做数据验证；日常决策信任第一直觉。", "每月前5天决策精准度最高"),
    new ApiDtos.PalmLineSummary("智慧线", "长弧+流畅", "你的表达能力是隐形优势——能把复杂概念说清楚，这在职场中极其稀缺。", "长弧流畅智慧线说明你的思维流畅度非常高——想清楚就能说清楚，这在跨团队协作、向上汇报、对外沟通中极有优势。但你容易因为「能说清楚」就觉得自己理解透了，建议在自己最自信的领域刻意找1-2个反例。", "在你最自信的结论中刻意找2个反例，确保你没有因为表达流畅而跳过关键推理。", "上午10点前后表达和逻辑同步最佳")
  };

  private static final ApiDtos.PalmLineSummary[] airLife = {
    new ApiDtos.PalmLineSummary("生命线", "中弧+等宽", "你的精力输出模式是稳态的——高峰不夸张、低谷不崩盘。", "等宽中弧生命线代表你的精力波动区间较窄，整体偏稳定。这在长期项目中极有优势——你不会突然断电，也不会过度亢奋。但稳定型精力容易导致自满：觉得「还好」就不主动优化。建议每季度做1次精力审计，确保没在温水煮青蛙。", "每季度做1次精力审计（1-10分评估），确保没有温水煮青蛙式下滑。", "全年精力波动小，每季度首月略高峰"),
    new ApiDtos.PalmLineSummary("生命线", "深+有力", "你有很好的恢复能力，高强度后能快速回弹，但恢复期需要真正的放空。", "深有力生命线说明你在精力管理上的核心优势是恢复力——熬一夜、加几天班后回弹很快。但这也会让你高估自己的代偿能力，建议在恢复期真的放空而不是卷另一种形式的工作。", "恢复期真的放空：不做任何脑力工作（包括刷手机），出去走30分钟比睡觉有效。", "周一至周三恢复效率最高"),
    new ApiDtos.PalmLineSummary("生命线", "偏长+微渐浅", "你的耐力好但不自觉，长周期中容易忽略第一次体力示警。", "微渐浅生命线意味着你在持续输出时不会突然崩溃，但精力会缓慢下滑——你自己可能都不觉得，直到效率明显降低。建议你在周期性项目开始时标记一个「检查点」，到点后强制做10分钟自检。", "长项目开始时设检查点，每到检查点做1次10分钟自检：当前精力和专注力还剩几成？", "每个自然月前半段综合状态最稳"),
    new ApiDtos.PalmLineSummary("生命线", "细线+流畅", "你精力虽不旺盛但非常稳定，适合需要持续注意力的工作。", "细流畅生命线说明你的精力总量也许不是最高的，但分布最均匀——没有大起大落，续航力极强。你在需要持续注意力和稳定输出型的工作中会胜出。但记得给自己安排定期充电，不要因为「还行」就忽略维护。", "每周安排1个充电日（哪怕只是2小时纯休息），不要等耗尽再充电。", "春夏季精力综合表现最佳"),
    new ApiDtos.PalmLineSummary("生命线", "弧宽+稳", "你有充足的精力储备但需要设计释放节奏，而非等到见到红灯才减速。", "弧宽稳生命线代表你底座很大——先天精力储备够用。但容易因为「底座大」就无节制地输出，直到见到红灯才减速。最佳策略是主动设计节奏：每4周安排1个低强度周，让储备有时间补充。", "每4周安排1个低强度周（只做必要事项），让精力储备补充到位。", "月初到月中综合状态最佳")
  };

  private static final ApiDtos.PalmLineSummary[] earthLove = {
    new ApiDtos.PalmLineSummary("感情线", "平缓+稳", "你在感情中是最可靠的存在——慢热但一认定就稳如磐石。", "土型手的感情线走势平缓，代表你在关系中追求稳定、可预期的连接——不会忽冷忽热，也不会三天换一种态度。这让你成为伴侣最安心的选择，但也可能被误解为不够热情。建议你每月刻意制造1次惊喜式表达，让对方知道你的稳不代表淡。", "每月1次以对方喜欢的方式做1个主动表达，打破「稳定=平淡」的误会。", "秋冬季节感情稳定度最高"),
    new ApiDtos.PalmLineSummary("感情线", "深+偏短", "你对感情的投入是全有或全无的，不会半心半意，但需要确信被对方重视。", "深短型感情线意味着你的爱是一个窄门——要么全心投入要么不开始。这让你在关系中极具安全感，但也让你容易因为对方的一个小提示就怀疑整个关系。建议你在疑虑出现时直接问而不是猜。", "任何疑虑在24小时内说出来，不要让猜测吃掉安全感。", "月底到月初感情自信号最强"),
    new ApiDtos.PalmLineSummary("感情线", "中深+微弧", "你在感情中的表达是渐进式的——行动多过语言，细节多过口号。", "微弧中深感情线代表你不会用大段表白来表达爱，而是用日复一日的稳定行动。这需要伴侣能读取你的行为语言——你可能没说过「我爱你」，但每天的准时到家就是你的表达。建议你偶尔也用语言明确表达一次。", "每周1次用语言直接表达1个感受或肯定，让行动被看见。", "月初感情表达欲最高"),
    new ApiDtos.PalmLineSummary("感情线", "宽弧+偏深", "你在感情中很有包容力，但持续包容不等于没有底线。", "宽弧偏深感情线说明你的感情容量很大——你能接纳对方很多缺点和不满。但大容量也意味着一旦被撑破、爆发力极强。关键是在前3次不满时就表达，不要等到第30次才一起说出。", "不满出现1次就说1次，不要攒到3次以上。用「我感受到…我需要…」句式表达。", "每季度中段关系突破概率最高"),
    new ApiDtos.PalmLineSummary("感情线", "中等+缓上升", "你的感情是慢炖型——越煮越浓，但需要时间长到够火候。", "缓上升感情线说明你的感情需要足够的时间来建立信任和深度——快煮的关系你反而不适应。这不是劣势，而是你的节奏。最好的策略是找同样重视长期稳定的人，而不是被短期热度冲昏头。", "识别对方是否有3个长期信号（守时、守诺、守边界），有这些就值得慢炖。", "每年春初和秋初关系自然深化期")
  };

  private static final ApiDtos.PalmLineSummary[] earthWisdom = {
    new ApiDtos.PalmLineSummary("智慧线", "偏直+深", "你的思维特质是务实可靠——在复杂场景中你是最不会被情绪带偏的人。", "偏直深色智慧线说明你做判断时几乎不受情绪干扰——这在需要冷静决策的团队中极有价值。但务实型思维也容易低估「感受」对决策的影响。建议你在所有决策中加1步：考虑做这个决定后相关人员的感受会怎样？", "每个决策加1步感受评估：这个决定做后，直接影响的人感受会怎样？", "月初务实判断力最精准"),
    new ApiDtos.PalmLineSummary("智慧线", "中等+偏直", "你的思考方式是积累型——通过实践验证来形成自己的认知体系。", "中等偏直智慧线说明你不是靠灵感做事的人，而是靠经验积累构建认知模型。这在执行型、管理型工作中极有优势——你的每个决策背后都有足够的实践依据。但小心犯经验主义的错：遇新场景时先验证再套旧方法。", "遇到破局场景时，先花30分钟收集3个不同于以往的数据点，再判断。", "每月下旬经验与直觉融合度最高"),
    new ApiDtos.PalmLineSummary("智慧线", "长+平缓", "你的思维耐心是你的优势——能在别人放弃的领域持续深耕。", "长平缓智慧线说明你在信息密集型、需要长期积累的领域有天然优势。你可能不是最先想到创新点子的人，但你是最能把点子变成成品的人。建议你把积累时间设为你的杠杆——每次想快进时提醒自己：慢即是快。", "每季度选1个领域做深度积累，用100小时建立该领域的结构化认知。", "每季度开始后第2-3周深度思考最有效"),
    new ApiDtos.PalmLineSummary("智慧线", "短粗+有力", "你的决策风格是大刀阔斧——看重执行速度和可操作性。", "短粗有力智慧线代表你在决策中优先砍掉不必要选项，直接抓最可执行的那条路。这在资源有限、时间紧迫的场景中极有优势。但粗放式决策容易跳过风险排查，建议在每个大决策后追加1次5分钟的风险清单。", "每个大决策后追加5分钟风险清单：列出可能的负面后果和应对方案。", "每周二执行决策效率最高"),
    new ApiDtos.PalmLineSummary("智慧线", "微弧+偏深", "你在实用性判断上有天赋——能快速判断什么是真正有用的。", "微弧偏深智慧线说明你天生就知道什么能落地、什么只是理论。这让你在评估方案时极有发言权。但也容易导致创新思维的盲区——如果一件事没有先例你可能直接否定它。建议你对每个「不可能」的想法追加1个问题：在什么条件下它可能？", "对每个否决的想法追问1次：在什么条件下它可能？找回5%的创新空间。", "每月中旬判断力与开放度平衡最好")
  };

  private static final ApiDtos.PalmLineSummary[] earthLife = {
    new ApiDtos.PalmLineSummary("生命线", "宽弧+深", "你的精力底座极大且稳定，是最适合长期持久战的人。", "宽弧深色生命线说明你先天精力储备充足、输出稳定，这是你最大的隐性资产。你不需要频繁休息来充电，但需要定期维护来保持这个优势——就像一台性能好的车也要定期保养。", "每周3次30分钟有氧+固定睡眠窗口7-8小时，这是保持精力底座的基本维护。", "全年前3个季度精力综合最稳"),
    new ApiDtos.PalmLineSummary("生命线", "厚+饱满", "你先天体质好但容易忽视轻微的不适信号——直到它变成大问题。", "厚饱满生命线代表你的身体容错率非常高——小毛病你扛得住，但正是因为扛得住你就不当回事。核心建议是：如果某个不适信号连续3天出现，必须认真对待而不是继续硬扛。", "连续3天的不适信号必须认真对待：记录出现时间、频率、强度变化。", "四季转换期需要额外注意身体信号"),
    new ApiDtos.PalmLineSummary("生命线", "中等+稳定弧", "你的精力输出模式是匀速长跑型——不追求爆发但持久力极强。", "稳定弧中等生命线意味着你不适合3天干完1周活这种模式，而是每天稳定输出6-7小时。关键策略是尊重自己的节奏——不要被别人「冲刺3天休息2天」的模式带跑。", "每天稳定输出6-7小时比冲刺更有效，避免被旁人的冲刺节奏带跑。", "全年精力输出平稳，没有明显低谷期"),
    new ApiDtos.PalmLineSummary("生命线", "偏短+紧", "你的精力总量有限但运用效率极高——从不浪费在无意义的事上。", "短紧生命线说明你先天精力不太充裕，但你对此有清醒认知——不社交到深夜、不折腾低效项目。这让你虽然总量少但利用率高。关键是继续保持这个纪律性，在必要社交中设定时间边界。", "设定社交硬边界：低价值社交限时1小时，准时离场不留恋。", "每季度首月精力利用效率最高"),
    new ApiDtos.PalmLineSummary("生命线", "深弧+中长", "你的恢复力强但容易透支不自知——因为每次都恢复了就以为没有极限。", "深弧中长生命线代表你恢复力好——每次透支后都能快速回弹，所以你一直感觉「还行」。但累积性透支是有上限的，当那个阀值被突破时，恢复速度会突然变慢。建议你每3个月做1次刻意恢复周。", "每3个月安排1个刻意恢复周（只做必要工作+每天早睡1小时），防止累积透支。", "上旬恢复力最好，适合安排高强度任务")
  };

  private String pickOverallSummary(String handType, int v) {
    return switch (handType) {
      case "火型手" -> switch (v) {
        case 0 -> "你是一个行动优先、节奏明快的人。三条主线共同揭示：感情上热烈但需学会长跑节奏，思维上快速精准但需要留出验证空间，精力上爆发力极强但恢复周期要保护好。你的核心优势是决断力和执行力——用好它，但别让它变成冲动。";
        case 1 -> "火型手的综合特质是热烈与果断并存。你在感情中全情投入但易因节奏不匹配而退出，在思维上当机立断但偶尔忽略细节，在精力管理上能集中突破但要防延迟性疲劳。点亮你的不是温和，是有挑战的目标和有回应的关系。";
        case 2 -> "你的三条主线揭示了一个核心模式：高能量+快速反馈循环。你适合需要即时判断和行动力的场景——决策、竞技、创业。但你的天坑是在高速运转中忽略身体和感情的减速信号。本月关键词：主动刹车。";
        case 3 -> "火型手的人天生的剧本是——冲在前面，回头看有没有人跟上。三条主线表明你兼具行动力和决断力，但容易被自己的速度拖着跑。感情的慢热者配不上你的节奏但能给你深度，思维的验证步骤让你从快变成又快又对。";
        default -> "你的手相整体呈高能量、强执行特征。感情线显示你一旦确认就全力以赴但需防止节奏落差，智慧线显示你快速决策但需留验证空间，生命线显示你爆发力强但恢复需要设计。核心建议：把你的快用在判断上，不要只用在行动上。";
      };
      case "水型手" -> switch (v) {
        case 0 -> "你是一个以共情力和直觉为核心驱动力的人。三条主线共同揭示：感情上深度连接但需建立边界，思维上直觉强但需数据验证，精力上情绪驱动波动大但恢复力好。你的核心优势是对人的理解力——用好它，但不要让它变成情绪负担。";
        case 1 -> "水型手的综合特质是深海型——表面平静下面有大量暗流。你在感情中天然理解他人但容易吸收对方情绪，在思维上直觉精准但需要逻辑校验，在精力上动力与情绪深度绑定。点亮你的不是目标，是意义感和被理解的深度。";
        case 2 -> "你的三条主线揭示了一个核心模式：感受力+恢复力的组合。你适合需要深度理解他人和复杂语境的场景——咨询、创作、建设性沟通。但你的天坑是在为他人消耗后不给自己充电。本月关键词：优先自己。";
        case 3 -> "水型手的人天生剧本是——用理解力撑起所有人的世界，但自己的世界只能靠边界来守住。三条主线表明你兼具共情力和深度思考力，但容易被「我能理解」变成「我替你扛」。关键是学会说「我理解但不代替」。";
        default -> "你的手相整体呈高感知、深连接特征。感情线显示你共情力强但需建立边界，智慧线显示你直觉准确但需数据保障，生命线显示你情绪驱动型但恢复力好。核心建议：把你的理解力用在理解自己上，不要只用在理解他人上。";
      };
      case "风型手" -> switch (v) {
        case 0 -> "你是一个思维灵活、表达力强的人。三条主线共同揭示：感情中理性与感性并存但偏理性决策，思维上结构化和表达力兼备，精力上偏稳态输出但容易忽略维护。你的核心优势是清晰——用好它，让复杂的事变简单是你最大的价值。";
        case 1 -> "风型手的综合特质是桥梁型——连接想法和现实，连接人和人。你在感情中理性评估但不失温度，在思维上能把复杂变简单但有时跳过体验，在精力管理上偏稳但需要定期刻意充电。点亮你的不是归属，是被需要和解决问题的空间。";
        case 2 -> "你的三条主线揭示了一个核心模式：结构力+表达力的组合。你适合需要快速整理信息和清晰传递的场景——策略、沟通、产品设计。但你的天坑是用逻辑回避情感——觉得「想清楚了」就不需要「感受一下」。";
        case 3 -> "风型手的人天生剧本是——把世界整理成可理解的版本然后讲给所有人听。三条主线表明你兼具结构化思维和表达力，但容易因为太清楚就跳过体验。你的下一步进化是：先感受再理解。";
        default -> "你的手相整体呈高逻辑、强表达特征。感情线显示你理性与感性并存但决策偏理性，智慧线显示你结构化和表达力兼备，生命线显示你稳态输出但需定期充电。核心建议：把你的清晰用在让复杂的事变简单上，不要只用在想把简单的事说清楚上。";
      };
      default -> switch (v) {
        case 0 -> "你是一个稳定、持久、值得信赖的人。三条主线共同揭示：感情上慢热但坚定，思维上经验驱动扎实可靠，精力上底座大需定期维护。你的核心优势是可靠性——在所有人都动摇的时候你还在，这就是你最强大的力量。";
        case 1 -> "土型手的综合特质是基石型——给团队和关系提供稳固的底盘。你在感情中忠诚但需要学会表达，在思维上务实但需给创新留空间，在精力上充沛但容易超限不自知。点亮你的不是新鲜感，是确定性和长期积累。";
        case 2 -> "你的三条主线揭示了一个核心模式：耐力+判断力的组合。你适合需要长期主义和扎实执行的场景——项目管理、系统建设、深度积累。但你的天坑是用稳定性覆盖一切——有时候你需要的是打破重建而不是继续修修补补。";
        case 3 -> "土型手的人天生剧本是——跑最远的马拉松，做最长情的伴侣。三条主线表明你兼具耐力和务实判断力，但容易因为太稳而忽视需要突破的时刻。你的下一步进化是：学会在不稳的时候创造自己的突破点。";
        default -> "你的手相整体呈高稳定、强持久特征。感情线显示你慢热但一旦认定就稳如磐石，智慧线显示你务实判断但需给创新留空间，生命线显示你底座大但需定期维护。核心建议：你的稳定是力量，但偶尔打破稳定是进化。";
      };
    };
  }

  private String pickCareerTip(String handType, int v) {
    return switch (handType) {
      case "火型手" -> CAREER_TIPS_FIRE[v];
      case "水型手" -> CAREER_TIPS_WATER[v];
      case "风型手" -> CAREER_TIPS_AIR[v];
      default -> CAREER_TIPS_EARTH[v];
    };
  }
  private String pickLoveTip(String handType, int v) {
    return switch (handType) {
      case "火型手" -> LOVE_TIPS_FIRE[v];
      case "水型手" -> LOVE_TIPS_WATER[v];
      case "风型手" -> LOVE_TIPS_AIR[v];
      default -> LOVE_TIPS_EARTH[v];
    };
  }
  private String pickHealthTip(String handType, int v) {
    return switch (handType) {
      case "火型手" -> HEALTH_TIPS_FIRE[v];
      case "水型手" -> HEALTH_TIPS_WATER[v];
      case "风型手" -> HEALTH_TIPS_AIR[v];
      default -> HEALTH_TIPS_EARTH[v];
    };
  }
  private String pickLuckyPeriod(int v) {
    return LUCKY_PERIODS[v];
  }

  private static final String[] CAREER_TIPS_FIRE = {
    "每季度选1件高挑战任务用冲刺模式完成，其余时间保持匀速推进。",
    "你的决断力是核心资产——用3指标法（成本/可行性/可逆性）缩短决策时间。",
    "适合需要即时决策的场景：项目管理、销售、创业。每月底复盘1次要方向。",
    "把你的快用在判断上，不要只用在行动上。重要决策前追加10分钟验证。",
    "每月初集中精力攻克最重要的1件事，下旬转为维护和收尾模式。"
  };
  private static final String[] CAREER_TIPS_WATER = {
    "你的共情力在用户研究和人际关系场景中是核武器——每月至少安排1次深度用户访谈。",
    "创意类工作是你的主场，但需要逻辑校验来防止情感过载拖慢产出。",
    "适合需要深度理解的场景：咨询、内容、关系管理。每周定义1个核心问题。",
    "直觉判断后追加数据验证：你的「感觉对了」80%都是对的，但那20%可能最致命。",
    "每季度做1次直觉审计：哪些决策靠直觉对了？哪些需要数据补充？"
  };
  private static final String[] CAREER_TIPS_AIR = {
    "你的表达力是职场核心杠杆——每周刻意练习1次公开表达（演讲、文档或1on1）。",
    "结构化思维+表达=你的超级能力。每月写1篇复盘文，把碎片经验变成可迁移方法论。",
    "适合需要信息整合和清晰传递的场景：策略、产品设计、跨团队协作。",
    "警惕用逻辑回避体验——每季度做1件没有数据支撑、纯靠直觉的决定，锻炼感性判断。",
    "月末做1次知识输出（文章/分享/复盘），把当月积累的碎片变成结构化认知。"
  };
  private static final String[] CAREER_TIPS_EARTH = {
    "你的可靠性是团队中不可替代的底座——但每季度需要1次刻意突破来避免原地踏步。",
    "积累型工作是你的主场，但刻意缩短积累-产出的周期，不要积累太久才输出。",
    "适合需要长期稳定的场景：系统建设、项目管理、深度运营。每月底复盘1次方向。",
    "在执行中留20%时间做探索——这是你最缺的不是执行力，是10%的创新力。",
    "每季度做1次战略盘点：当前积累是否还在对的方向上？慢不代表对。"
  };

  private static final String[] LOVE_TIPS_FIRE = {
    "感情中不要把快节奏当成标准——对方的慢不代表不爱，学会用对方的速度陪跑。",
    "每周1次30分钟深度对话，谈感受而不是解决方案，这是维持关系质量最低投入。",
    "在感情降温期不要立即重启新循环——给2周时间看平淡是否其实是一种深度。",
    "你的热情是你的魅力但也是开关——学会在激情和日常之间主动切换，而不是等自然降温。",
    "每月1次主动问对方1个真实需求，而不是假设你知道。"
  };
  private static final String[] LOVE_TIPS_WATER = {
    "每月1次主动表达自己的需求，而不是只回应对方的需求——你的共情力是天赋但不要全是消耗。",
    "识别伴侣的3个核心情感需求并每周至少满足1个，同时明确说出自己的2个核心需求。",
    "当感觉不适时直接说「我需要什么」而不是等对方猜——你的暗示太难猜了。",
    "每2周做1次关系健康度检查（1-10分评估），低于7分就需要一次半小时的修复对话。",
    "设置情感边界不是为了推开对方，而是为了更可持续地在一起——不设边界的关系最先枯竭。"
  };
  private static final String[] LOVE_TIPS_AIR = {
    "你的理性是吸引力但不要让它变成感情里的评审——每月至少1次主动表达感受而非分析。",
    "在「想清楚了」后面追加1步「感受到了吗？」——你的逻辑和感受需要同样被听见。",
    "每周1次不讲逻辑只讲感受的10分钟对话——你的伴侣需要的有时不是解决方案，是被看见。",
    "不要用分析替代表达——对方需要你说「我也想你了」而不是推导出这个结论。",
    "每月1次放下效率追求，做1件纯粹让双方开心的没有KPI的事。"
  };
  private static final String[] LOVE_TIPS_EARTH = {
    "每个月1次用语言主动说出1个感受——你的行动已经做了90%，但对方需要听到10%。",
    "感情中的可预测性是你的表达方式——每周1次固定的约会就是你的情书。",
    "你的可靠是感情中最被低估的品质——但每季度1次的惊喜可以防止稳定变成平淡。",
    "不满出现1次就说1次——你的大盘量很大但撑破时爆发力惊人，不要攒着说。",
    "慢炖的关系是你的节奏——不要因为别人对你快就加速，你的30度恒温比别人的90度瞬间更有价值。"
  };

  private static final String[] HEALTH_TIPS_FIRE = {
    "每3天高强度后强制安排1个恢复日——你的爆发力需要配合恢复节奏才能持续。",
    "规则睡眠是你最大的竞争优势——熬夜2天你的判断力在第3天开始明显下滑。",
    "运动是最好的情绪出口——每周3次30分钟有氧可以同时解决体能和情绪管理。",
    "你的体感信号是骤降型：一旦感觉累了就已经透支3天了——在第1天就减速。",
    "每季度做1次全面体检—not because you're worried, but because you want to keep running fast. 体能是你所有其他优势的基础。"
  };
  private static final String[] HEALTH_TIPS_WATER = {
    "情绪低落日改为轻量模式——不要硬扛，你的精力跟情绪是绑定的，这不是意志力问题。",
    "每天7-8小时固定睡眠窗口是非谈判项——低于这个阈值你的共情力和判断力同步下滑。",
    "每周2次30分钟独处散步——你需要的时间不是健身，是清空情绪容量。",
    "你的精力和情绪是联动的——记录1周精力与情绪对照表，找到你的联动模式。",
    "每2周做1次情绪归零：1小时无设备散步+1杯热饮，让情绪容量重新满杯。"
  };
  private static final String[] HEALTH_TIPS_AIR = {
    "你的精力管理关键词是维护——不需要爆发式运动，但需要每天30分钟的规律活动。",
    "每周1个充电日（哪怕只是2小时纯休息）——不要因为「还行」就跳过这步。",
    "用结构化管理精力：把调查型任务和表达型任务交替安排，避免单一模式消耗。",
    "睡眠是你最被低估的生产力杠杆——7小时以上睡眠后的产出是6小时的3倍。",
    "每季度做1次精力审计：1-10分评估当前状态，如果低于6分就需要1个恢复周。"
  };
  private static final String[] HEALTH_TIPS_EARTH = {
    "你是最不需要担心精力的人但也最容易忽略身体信号——每3天1次自检（1-10分评估精力）。",
    "规律是你的护城河——每天固定起床时间和运动时间比任何补剂都有效。",
    "你的耐力太好了以至于忽视轻微不适——如果连续3天出现同一个不适信号请认真对待。",
    "每3个月安排1个刻意恢复周——不是因为你累了，是因为你不想等到累了才算。",
    "适合中低强度持续运动：快走、游泳、骑行——不需要冲刺，但需要每周3次30分钟以上。"
  };

  private static final String[] LUCKY_PERIODS = {
    "每月上旬精力和判断力处于高峰，适合做重要决定和关键推进；中旬注意节奏放缓。",
    "每月中旬是社交和资源整合高峰期，上旬适合深度思考和方案设计。",
    "逢整月的7号前后与21号前后是本月两个最佳决策窗口——重要事项尽量排在这两天。",
    "春冬季综合状态最佳——春主生发适合启动新项目，冬主收藏适合复盘和规划。",
    "满月后3天感情窗口最佳，新月后3天事业判断力最尖，用自然节奏安排不同类型的重点事项。"
  };

  private String pickHandType(String seed) {
    List<String> types = List.of("火型手", "水型手", "风型手", "土型手");
    return types.get(Math.abs(seed.hashCode()) % types.size());
  }

  private String pickRareMark(String seed) {
    List<String> marks = List.of("凤凰眼", "双鱼纹", "神秘十字", "太阳环");
    return marks.get(Math.abs(seed.hashCode() / 7) % marks.size());
  }

  private List<String> pickPersonalityTags(String handType) {
    return switch (handType) {
      case "火型手" -> List.of("行动驱动", "社交发电机", "目标导向");
      case "水型手" -> List.of("高敏感", "直觉系", "共情力强");
      case "风型手" -> List.of("逻辑脑", "表达强", "适应快");
      default -> List.of("稳定派", "耐力型", "长期主义");
    };
  }

  private double buildCpScore(String mbtiA, String mbtiB) {
    int hash = Math.abs((mbtiA + "-" + mbtiB).hashCode());
    double score = 70 + (hash % 2900) / 100.0;
    return Math.min(98.0, Math.max(70.0, score));
  }

  private String buildComboName(String typeA, String typeB) {
    if ("火型手".equals(typeA) && "水型手".equals(typeB)
        || "水型手".equals(typeA) && "火型手".equals(typeB)) {
      return "救赎文学组";
    }
    if ("风型手".equals(typeA) && "土型手".equals(typeB)
        || "土型手".equals(typeA) && "风型手".equals(typeB)) {
      return "现实智囊组";
    }
    return "理想主义组";
  }

  private int clamp(int value) {
    return Math.max(70, Math.min(98, value));
  }

  private void trackEventInternal(String eventName, String sessionId, String channel) {
    appEventRepository.save(new AppEventEntity(eventName, sessionId, channel, Instant.now()));
  }

  private TracePromptBundle buildTracePromptBundle(ApiDtos.PalmLineTraces traces) {
    LineTraceFeature life = extractLineFeature("生命线", traces == null ? null : traces.lifeLine());
    LineTraceFeature wisdom = extractLineFeature("智慧线", traces == null ? null : traces.wisdomLine());
    LineTraceFeature love = extractLineFeature("感情线", traces == null ? null : traces.loveLine());

    List<LineTraceFeature> available = new ArrayList<>();
    if (life.available()) {
      available.add(life);
    }
    if (wisdom.available()) {
      available.add(wisdom);
    }
    if (love.available()) {
      available.add(love);
    }

    if (available.isEmpty()) {
      return new TracePromptBundle(false, "", "未进行掌纹手动确认。", List.of());
    }

    StringBuilder prompt = new StringBuilder();
    prompt.append("以下是用户亲手描摹出的手相特征，请严格基于这些描述进行解读，不要编造。\\n");
    for (LineTraceFeature feature : available) {
      prompt.append("- ")
          .append(feature.lineName())
          .append("：长度")
          .append(feature.lengthLabel())
          .append("，弯曲度")
          .append(feature.curvatureLabel())
          .append("，连续性")
          .append(feature.continuityLabel())
          .append("，末端分叉")
          .append(feature.forked() ? "是" : "否")
          .append("，事件")
          .append(feature.eventLabel())
          .append("。\\n");
    }

    String summary = "已进行掌纹确认：" + available.stream().map(LineTraceFeature::compactText).reduce((a, b) -> a + "；" + b)
        .orElse("已进行掌纹确认");
    return new TracePromptBundle(true, prompt.toString(), summary, available);
  }

  private List<ApiDtos.PalmLineSummary> buildOverviewFromTraceFeatures(List<LineTraceFeature> features) {
    Map<String, LineTraceFeature> byName = new ConcurrentHashMap<>();
    for (LineTraceFeature feature : features) {
      byName.put(feature.lineName(), feature);
    }

    return List.of(
        toOverview(byName.get("感情线"), "感情线"),
        toOverview(byName.get("智慧线"), "智慧线"),
        toOverview(byName.get("生命线"), "生命线"));
  }

  private ApiDtos.PalmLineSummary toOverview(LineTraceFeature feature, String defaultName) {
    if (feature == null || !feature.available()) {
      return new ApiDtos.PalmLineSummary(defaultName, "未确认", "你未手动描摹该线，当前结果基于通用模型推断，仅供参考。", "", "", "");
    }

    String tags = "长度" + feature.lengthLabel() + " / 弯曲" + feature.curvatureLabel() + " / " + feature.continuityLabel();
    String text = feature.naturalText() + "建议你结合最近30天的实际状态做一次对照复盘。";
    return new ApiDtos.PalmLineSummary(feature.lineName(), tags, text, "", "", "");
  }

  private LineTraceFeature extractLineFeature(String lineName, List<ApiDtos.TracePoint> points) {
    if (points == null || points.size() < 3) {
      return LineTraceFeature.unavailable(lineName);
    }

    List<ApiDtos.TracePoint> sampled = samplePoints(points, 3.0);
    if (sampled.size() < 3) {
      return LineTraceFeature.unavailable(lineName);
    }

    double length = pathLength(sampled);
    double chord = distance(sampled.get(0), sampled.get(sampled.size() - 1));
    double baseDiagonal = estimateDiagonal(sampled);
    if (baseDiagonal < 1e-6) {
      return LineTraceFeature.unavailable(lineName);
    }

    double lengthRatio = length / baseDiagonal;
    String lengthLabel = classifyLength(lengthRatio);
    double curvatureRatio = chord < 1e-6 ? 1.0 : length / chord;
    String curvatureLabel = classifyCurvature(curvatureRatio);

    int jumpCount = countJumps(sampled);
    String continuity = jumpCount >= 2 ? "断续" : "连续";
    boolean forked = detectFork(sampled, baseDiagonal);
    String eventLabel = detectEvents(sampled, baseDiagonal);
    String natural = buildNaturalSentence(lineName, lengthLabel, curvatureLabel, continuity, forked, eventLabel);

    return new LineTraceFeature(lineName, true, lengthLabel, curvatureLabel, continuity, forked, eventLabel, natural);
  }

  private List<ApiDtos.TracePoint> samplePoints(List<ApiDtos.TracePoint> points, double minDistance) {
    List<ApiDtos.TracePoint> sampled = new ArrayList<>();
    ApiDtos.TracePoint last = null;
    for (ApiDtos.TracePoint p : points) {
      if (p == null) {
        continue;
      }
      if (last == null || distance(last, p) >= minDistance) {
        sampled.add(p);
        last = p;
      }
    }
    return sampled;
  }

  private double pathLength(List<ApiDtos.TracePoint> points) {
    double sum = 0.0;
    for (int i = 1; i < points.size(); i++) {
      sum += distance(points.get(i - 1), points.get(i));
    }
    return sum;
  }

  private double estimateDiagonal(List<ApiDtos.TracePoint> points) {
    double minX = Double.MAX_VALUE;
    double minY = Double.MAX_VALUE;
    double maxX = -Double.MAX_VALUE;
    double maxY = -Double.MAX_VALUE;
    for (ApiDtos.TracePoint p : points) {
      minX = Math.min(minX, p.x());
      minY = Math.min(minY, p.y());
      maxX = Math.max(maxX, p.x());
      maxY = Math.max(maxY, p.y());
    }
    return Math.hypot(maxX - minX, maxY - minY);
  }

  private String classifyLength(double ratio) {
    if (ratio >= 1.15) {
      return "长";
    }
    if (ratio >= 0.75) {
      return "中等";
    }
    return "短";
  }

  private String classifyCurvature(double ratio) {
    if (ratio >= 1.35) {
      return "大";
    }
    if (ratio >= 1.15) {
      return "中";
    }
    return "小";
  }

  private int countJumps(List<ApiDtos.TracePoint> points) {
    if (points.size() < 3) {
      return 0;
    }
    double total = 0.0;
    for (int i = 1; i < points.size(); i++) {
      total += distance(points.get(i - 1), points.get(i));
    }
    double avg = total / (points.size() - 1);
    int jumps = 0;
    for (int i = 1; i < points.size(); i++) {
      double segment = distance(points.get(i - 1), points.get(i));
      if (segment > Math.max(18.0, avg * 3.2)) {
        jumps++;
      }
    }
    return jumps;
  }

  private boolean detectFork(List<ApiDtos.TracePoint> points, double baseDiagonal) {
    int n = points.size();
    if (n < 8) {
      return false;
    }
    int from = Math.max(0, (int) Math.floor(n * 0.8));
    List<ApiDtos.TracePoint> tail = points.subList(from, n);
    ApiDtos.TracePoint end = points.get(n - 1);
    double maxSpread = 0.0;
    Set<Integer> angleBuckets = new HashSet<>();
    for (ApiDtos.TracePoint p : tail) {
      maxSpread = Math.max(maxSpread, distance(end, p));
    }
    for (int i = 1; i < tail.size(); i++) {
      ApiDtos.TracePoint a = tail.get(i - 1);
      ApiDtos.TracePoint b = tail.get(i);
      double dx = b.x() - a.x();
      double dy = b.y() - a.y();
      if (Math.hypot(dx, dy) < 1e-6) {
        continue;
      }
      double angle = Math.toDegrees(Math.atan2(dy, dx));
      int bucket = (int) Math.round(angle / 25.0);
      angleBuckets.add(bucket);
    }
    return maxSpread > baseDiagonal * 0.12 && angleBuckets.size() >= 2;
  }

  private String detectEvents(List<ApiDtos.TracePoint> points, double baseDiagonal) {
    boolean hasPause = false;
    for (int i = 1; i < points.size(); i++) {
      ApiDtos.TracePoint prev = points.get(i - 1);
      ApiDtos.TracePoint cur = points.get(i);
      if (prev.t() != null && cur.t() != null) {
        long dt = Math.abs(cur.t() - prev.t());
        if (dt >= 260 && distance(prev, cur) < 2.0) {
          hasPause = true;
          break;
        }
      }
    }

    boolean hasLoop = false;
    for (int i = 0; i < points.size(); i++) {
      for (int j = i + 4; j < points.size(); j++) {
        if (distance(points.get(i), points.get(j)) < 6.0) {
          double path = 0.0;
          for (int k = i + 1; k <= j; k++) {
            path += distance(points.get(k - 1), points.get(k));
          }
          if (path > baseDiagonal * 0.18) {
            hasLoop = true;
            break;
          }
        }
      }
      if (hasLoop) {
        break;
      }
    }

    if (hasLoop) {
      return "岛纹";
    }
    if (hasPause) {
      return "停顿";
    }
    return "无";
  }

  private String buildNaturalSentence(
      String lineName,
      String lengthLabel,
      String curvatureLabel,
      String continuity,
      boolean forked,
      String eventLabel) {
    StringBuilder sb = new StringBuilder();
    sb.append(lineName)
        .append("长度")
        .append(lengthLabel)
        .append("，弯曲度")
        .append(curvatureLabel)
        .append("，整体")
        .append(continuity)
        .append("。");
    if (forked) {
      sb.append("末端出现分叉，说明你在关键阶段有双路径倾向。");
    }
    if (!"无".equals(eventLabel)) {
      sb.append("轨迹中检测到").append(eventLabel).append("事件，代表该阶段存在明显停留或反复确认。");
    }
    return sb.toString();
  }

  private double distance(ApiDtos.TracePoint a, ApiDtos.TracePoint b) {
    return Math.hypot(a.x() - b.x(), a.y() - b.y());
  }

  private double round2(double value) {
    return Math.round(value * 10000.0) / 100.0;
  }

private LlmPalmOverviewResult generatePalmOverviewWithLlm(
      String handType,
      List<String> tags,
      String rareMark,
      String imageData,
      String recordMode,
      String tracePromptBlock) {
    if (!llmClientService.isAvailable()) {
      return new LlmPalmOverviewResult(false, "llm_disabled", null);
    }

    int variant = Math.abs(handType.hashCode()) % 5;
    String baseOverview = switch (handType) {
      case "火型手" -> "感情：" + fireLove[variant].shortInterpretation() + "；智慧：" + fireWisdom[variant].shortInterpretation() + "；生命：" + fireLife[variant].shortInterpretation();
      case "水型手" -> "感情：" + waterLove[variant].shortInterpretation() + "；智慧：" + waterWisdom[variant].shortInterpretation() + "；生命：" + waterLife[variant].shortInterpretation();
      case "风型手" -> "感情：" + airLove[variant].shortInterpretation() + "；智慧：" + airWisdom[variant].shortInterpretation() + "；生命：" + airLife[variant].shortInterpretation();
      default -> "感情：" + earthLove[variant].shortInterpretation() + "；智慧：" + earthWisdom[variant].shortInterpretation() + "；生命：" + earthLife[variant].shortInterpretation();
    };
    String baseSummary = pickOverallSummary(handType, variant);
    String baseCareer = pickCareerTip(handType, variant);
    String baseLove = pickLoveTip(handType, variant);
    String baseHealth = pickHealthTip(handType, variant);
    String baseLucky = pickLuckyPeriod(variant);

    String systemPrompt = "你是手相解读增强引擎。你收到一段基础解读文本，需要在保持核心框架的基础上增强细节、增加个性化解读和补充建议。返回严格JSON。";
    String userPrompt = "基于以下基础解读进行增强个性化：\n"
        + "手型=" + handType + "，性格标签=" + String.join("/", tags) + "，稀有印记=" + rareMark + "\n"
        + "基础解读：" + baseOverview + "\n"
        + "基础综合解读：" + baseSummary + "\n"
        + "基础职业建议：" + baseCareer + "\n"
        + "基础感情建议：" + baseLove + "\n"
        + "基础健康建议：" + baseHealth + "\n"
        + "基础幸运时段：" + baseLucky + "\n\n"
        + "请增强后返回JSON：\n"
        + "{\"personalityTags\":[\"\",\"\",\"\",\"\"],\"freeOverview\":[{\"lineName\":\"感情线\",\"tags\":\"2-4个短语用/分隔\",\"shortInterpretation\":\"60-90字概述\",\"detailInterpretation\":\"100-160字详细解读含趋势分析和触发条件\",\"actionableAdvice\":\"25-50字具体可执行建议含动作和频次\",\"timeWindow\":\"8-20字适用时间窗口\"},{\"lineName\":\"智慧线\",...},{\"lineName\":\"生命线\",...}],\"teaser\":\"35-55字\",\"overallSummary\":\"50-80字整体综合解读\",\"careerTip\":\"20-40字职业建议\",\"loveTip\":\"20-40字感情建议\",\"healthTip\":\"20-40字健康建议\",\"luckyPeriod\":\"15-30字幸运时段\"}\n"
        + "要求：基于基础解读增强，保留核心观点但增加细节、补充个性化元素、加入当前趋势分析。中文返回。";

    if (tracePromptBlock != null && !tracePromptBlock.isBlank()) {
      userPrompt += "\n\n额外掌纹描摹信息：" + tracePromptBlock;
    }

    try {
      String content = llmClientService.chat(systemPrompt, userPrompt, imageData);
      try {
        JsonNode node = parseJsonNode(content);
        List<String> llmTags = List.of(
            node.path("personalityTags").path(0).asText(tags.get(0)),
            node.path("personalityTags").path(1).asText(tags.get(1)),
            node.path("personalityTags").path(2).asText(tags.get(2)));

        List<ApiDtos.PalmLineSummary> llmOverview = List.of(
            new ApiDtos.PalmLineSummary(
                node.path("freeOverview").path(0).path("lineName").asText("感情线"),
                node.path("freeOverview").path(0).path("tags").asText(""),
                node.path("freeOverview").path(0).path("shortInterpretation").asText(""),
                node.path("freeOverview").path(0).path("detailInterpretation").asText(""),
                node.path("freeOverview").path(0).path("actionableAdvice").asText(""),
                node.path("freeOverview").path(0).path("timeWindow").asText("")),
            new ApiDtos.PalmLineSummary(
                node.path("freeOverview").path(1).path("lineName").asText("智慧线"),
                node.path("freeOverview").path(1).path("tags").asText(""),
                node.path("freeOverview").path(1).path("shortInterpretation").asText(""),
                node.path("freeOverview").path(1).path("detailInterpretation").asText(""),
                node.path("freeOverview").path(1).path("actionableAdvice").asText(""),
                node.path("freeOverview").path(1).path("timeWindow").asText("")),
            new ApiDtos.PalmLineSummary(
                node.path("freeOverview").path(2).path("lineName").asText("生命线"),
                node.path("freeOverview").path(2).path("tags").asText(""),
                node.path("freeOverview").path(2).path("shortInterpretation").asText(""),
                node.path("freeOverview").path(2).path("detailInterpretation").asText(""),
                node.path("freeOverview").path(2).path("actionableAdvice").asText(""),
                node.path("freeOverview").path(2).path("timeWindow").asText("")));

        String llmTeaser = node.path("teaser").asText("检测到稀有印记「" + rareMark + "」，完整解析可在报告页查询");
        String llmOverallSummary = node.path("overallSummary").asText(null);
        String llmCareerTip = node.path("careerTip").asText(null);
        String llmLoveTip = node.path("loveTip").asText(null);
        String llmHealthTip = node.path("healthTip").asText(null);
        String llmLuckyPeriod = node.path("luckyPeriod").asText(null);
        return new LlmPalmOverviewResult(true, "ok_json", new LlmPalmOverview(llmTags, llmOverview, llmTeaser, llmOverallSummary, llmCareerTip, llmLoveTip, llmHealthTip, llmLuckyPeriod));
      } catch (Exception parseError) {
        LlmPalmOverview textFallback = buildPalmOverviewFromText(content, tags, rareMark);
        if (textFallback != null) {
          return new LlmPalmOverviewResult(true, "ok_text", textFallback);
        }
        return new LlmPalmOverviewResult(false, "parse_failed:" + shortReason(parseError), null);
      }
    } catch (Exception callError) {
      return new LlmPalmOverviewResult(false, "call_failed:" + shortReason(callError), null);
    }
  }

  private LlmPalmOverview buildPalmOverviewFromText(String content, List<String> tags, String rareMark) {
    if (content == null || content.isBlank()) {
      return null;
    }

    List<String> sentences = new ArrayList<>();
    for (String segment : SENTENCE_SPLIT.split(content)) {
      String s = segment == null ? "" : segment.trim();
      if (!s.isBlank()) {
        sentences.add(s);
      }
    }
    if (sentences.isEmpty()) {
      return null;
    }

    String s1 = sentences.get(0) + "。";
    String s2 = (sentences.size() > 1 ? sentences.get(1)
      : "你的智慧线偏理性与结构化，适合先搭框架再填细节；遇到关键选择时建议列出成本/收益/可逆性三项证据，再做取舍，并在一周内复盘结果以校准判断") + "。";
    String s3 = (sentences.size() > 2 ? sentences.get(2)
      : "生命线显示恢复节律较稳定，但高压期后易出现延迟疲劳，建议连续工作2天后安排1次30分钟低强度运动与拉伸，并固定睡眠窗口以稳住体能基线") + "。";

    List<ApiDtos.PalmLineSummary> overview = List.of(
        new ApiDtos.PalmLineSummary("感情线", "模型文本解析", s1, "", "", ""),
        new ApiDtos.PalmLineSummary("智慧线", "模型文本解析", s2, "", "", ""),
        new ApiDtos.PalmLineSummary("生命线", "模型文本解析", s3, "", "", ""));

    String teaser = "检测到稀有印记「" + rareMark + "」，模型已完成文本解读，完整趋势可继续解锁查看。";
    return new LlmPalmOverview(tags, overview, teaser, null, null, null, null, null);
  }

  private String shortReason(Exception ex) {
    if (ex == null) {
      return "unknown";
    }
    String msg = ex.getMessage();
    if (msg == null || msg.isBlank()) {
      return ex.getClass().getSimpleName();
    }
    msg = msg.replaceAll("\\s+", " ").trim();
    if (msg.length() > 300) {
      return msg.substring(0, 300);
    }
    return msg;
  }

  private List<ApiDtos.DeepSection> generateSingleDeepWithLlm(SessionState session) {
    if (!llmClientService.isAvailable()) {
      return null;
    }

    String systemPrompt = "你是手相深度报告引擎，返回严格JSON数组。";
    String userPrompt = "返回JSON数组sections，长度3。每元素{\"title\":\"\",\"detail\":\"\",\"cyberTip\":\"\"}。"
        + "handType=" + session.handType() + " rareMark=" + session.rareMark() + "。"
        + "中文，detail 60-120字含趋势+建议，cyberTip 20-40字。";

    try {
      String content = llmClientService.chat(systemPrompt, userPrompt);
      JsonNode root = parseJsonNode(content);
      JsonNode sectionsNode = root;
      if (!sectionsNode.isArray()) {
        sectionsNode = root.path("sections");
      }
      if (!sectionsNode.isArray() || sectionsNode.size() < 2) {
        return null;
      }
      List<ApiDtos.DeepSection> sections = new ArrayList<>();
      for (JsonNode item : sectionsNode) {
        sections.add(new ApiDtos.DeepSection(
            item.path("title").asText("深度趋势"),
            item.path("detail").asText("近期处于波段上行区间，建议保持稳定节奏。"),
            item.path("cyberTip").asText("先稳住作息，再放大关键行动。")));
      }
      return sections;
    } catch (Exception ignored) {
      return null;
    }
  }

  private LlmCpNarrative generateCpNarrativeWithLlm(ApiDtos.CpAnalyzeRequest request, String comboName, double score) {
    if (!llmClientService.isAvailable()) {
      return null;
    }

    String systemPrompt = "你是CP合拍文案引擎，返回严格JSON。";
    String userPrompt = "返回JSON{\"comboName\":\"\",\"interpretation\":\"\",\"tip\":\"\"}。"
        + "A=" + request.userA().handType() + "/" + request.userA().mbti()
        + " B=" + request.userB().handType() + "/" + request.userB().mbti()
        + " score=" + score + " defaultCombo=" + comboName + "。"
        + "中文，interpretation 40-80字，tip 20-40字。";

    try {
      String content = llmClientService.chat(systemPrompt, userPrompt);
      JsonNode node = parseJsonNode(content);
      return new LlmCpNarrative(
          node.path("comboName").asText(comboName),
          node.path("interpretation").asText("你们属于互补型关系，一方建模一方点亮情绪价值。"),
          node.path("tip").asText("先对齐目标，再讨论路径，冲突会明显降低。"));
    } catch (Exception ignored) {
      return null;
    }
  }

  private JsonNode parseJsonNode(String content) throws Exception {
    try {
      return objectMapper.readTree(content);
    } catch (Exception ignored) {
      String extracted = extractJsonBlock(content);
      if (extracted == null || extracted.isBlank()) {
        throw ignored;
      }
      return objectMapper.readTree(extracted);
    }
  }

  private String extractJsonBlock(String text) {
    if (text == null) {
      return null;
    }

    String trimmed = text.trim();
    if (trimmed.startsWith("```")) {
      int firstBreak = trimmed.indexOf('\n');
      int lastFence = trimmed.lastIndexOf("```");
      if (firstBreak > -1 && lastFence > firstBreak) {
        trimmed = trimmed.substring(firstBreak + 1, lastFence).trim();
      }
    }

    int startObj = trimmed.indexOf('{');
    int startArr = trimmed.indexOf('[');
    int start = -1;
    char opener = '\0';
    if (startObj >= 0 && (startArr < 0 || startObj < startArr)) {
      start = startObj;
      opener = '{';
    } else if (startArr >= 0) {
      start = startArr;
      opener = '[';
    }

    if (start < 0) {
      return null;
    }

    char closer = opener == '{' ? '}' : ']';
    int depth = 0;
    boolean inString = false;
    boolean escaping = false;
    for (int i = start; i < trimmed.length(); i++) {
      char c = trimmed.charAt(i);
      if (inString) {
        if (escaping) {
          escaping = false;
        } else if (c == '\\') {
          escaping = true;
        } else if (c == '"') {
          inString = false;
        }
        continue;
      }

      if (c == '"') {
        inString = true;
        continue;
      }

      if (c == opener) {
        depth++;
      } else if (c == closer) {
        depth--;
        if (depth == 0) {
          return trimmed.substring(start, i + 1);
        }
      }
    }
    return null;
  }

  private record LlmPalmOverview(
      List<String> personalityTags,
      List<ApiDtos.PalmLineSummary> freeOverview,
      String teaser,
      String overallSummary,
      String careerTip,
      String loveTip,
      String healthTip,
      String luckyPeriod) {
  }

  private record LlmPalmOverviewResult(
      boolean used,
      String status,
      LlmPalmOverview overview) {
  }

  private record LlmCpNarrative(
      String comboName,
      String interpretation,
      String tip) {
  }

  private record TracePromptBundle(
      boolean confirmed,
      String promptBlock,
      String summaryText,
      List<LineTraceFeature> features) {
  }

  private record LineTraceFeature(
      String lineName,
      boolean available,
      String lengthLabel,
      String curvatureLabel,
      String continuityLabel,
      boolean forked,
      String eventLabel,
      String naturalText) {

    static LineTraceFeature unavailable(String lineName) {
      return new LineTraceFeature(lineName, false, "未知", "未知", "未确认", false, "无", "");
    }

    String compactText() {
      if (!available) {
        return lineName + "未确认";
      }
      return lineName + "长度" + lengthLabel + "、弯曲" + curvatureLabel + "、" + continuityLabel;
    }
  }

  private record SessionState(
      String sessionId,
      String handType,
      String rareMark,
      Instant createdAt) {
  }

  private String normalizeMode(String mode, String fallback) {
    if (mode == null || mode.isBlank()) {
      return fallback;
    }
    String normalized = mode.trim().toLowerCase();
    if ("quick".equals(normalized) || "full".equals(normalized) || "standard".equals(normalized)
        || "single".equals(normalized)) {
      return normalized;
    }
    return fallback;
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

  private String buildCompareHint(PalmRecordEntity previous, int energy, int wisdom, int emotion) {
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
      ApiDtos.PalmLineTraces traces) {
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

  private String serializeTraces(ApiDtos.PalmLineTraces traces) {
    if (traces == null) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(traces);
    } catch (Exception ex) {
      return null;
    }
  }

  private String calcDominantEnergy(List<PalmRecordEntity> rows) {
    if (rows.isEmpty()) {
      return "休整之月";
    }
    double energyAvg = rows.stream().mapToInt(PalmRecordEntity::getEnergyLevel).average().orElse(0);
    double wisdomAvg = rows.stream().mapToInt(PalmRecordEntity::getWisdomActive).average().orElse(0);
    double emotionAvg = rows.stream().mapToInt(PalmRecordEntity::getEmotionWave).average().orElse(0);
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

  private String buildMonthlyReportText(String yearMonth, String dominant, List<PalmRecordEntity> rows) {
    if (rows.isEmpty()) {
      return yearMonth + " 暂无记录，建议每周至少拍一次手相，建立你的能量曲线。";
    }
    PalmRecordEntity maxEnergy = rows.stream().max(Comparator.comparingInt(PalmRecordEntity::getEnergyLevel)).orElse(rows.get(0));
    PalmRecordEntity minEnergy = rows.stream().min(Comparator.comparingInt(PalmRecordEntity::getEnergyLevel)).orElse(rows.get(0));
    return yearMonth + " 你完成了 " + rows.size() + " 次记录，本月主导能量为「" + dominant + "」。"
        + "高峰出现在 " + maxEnergy.getRecordDate() + "，低谷出现在 " + minEnergy.getRecordDate()
        + "。建议围绕高峰时段安排关键任务，在低谷期留出恢复窗口。";
  }
}
