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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.util.Base64;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.security.MessageDigest;

@Service
public class PalmistryService {

  private static final String SLOGAN = "你的互联网手相搭子，科学聊玄学";
  private static final String WECHAT_ID = "CyberPalm-Master";
  private static final Pattern SENTENCE_SPLIT = Pattern.compile("[。！？!?\\n]+\\s*");
  private static final DateTimeFormatter YEAR_MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
  /**
   * 合规出口审查（TD §13.2）：疾病/死亡/生育预测与具体财务指引绝对禁止。
   * 命中即弃用本次 LLM 文案，回退确定性模板。
   */
  private static final Pattern BANNED_CLAIM_PATTERN = Pattern.compile(
      "癌症|绝症|肿瘤|白血病|晚期|死亡|死期|寿元|阳寿|短命|活不过|血光之灾|牢狱之灾|难产|克子|克夫"
          + "|暴富|发财|破财|炒股|股票|基金|期货|加密货币|彩票|赌博|加仓|减仓|买入|卖出|翻倍|稳赚");
  private static final String CONTENT_GUARDRAIL =
      "内容红线（最高优先级）：严禁输出疾病、死亡、寿元、生育相关预测，"
          + "严禁给出股票/基金/加密货币等任何具体财务投资建议；只做性格倾向与娱乐化解读。";
  private static final int RESPONSE_CACHE_MAX = 500;
  private static final int SESSION_CACHE_MAX = 20_000;
  /** 图片 base64 体积上限（字符数，约 9MB 原图），防止超大 payload 拖垮上传与 LLM 调用。 */
  private static final int MAX_IMAGE_CHARS = 12_000_000;

  /** 会话表用 LRU 淘汰，避免长期运行后无限增长。 */
  private final Map<String, SessionState> sessions =
      Collections.synchronizedMap(new LinkedHashMap<>(1024, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, SessionState> eldest) {
          return size() > SESSION_CACHE_MAX;
        }
      });
  /** 图哈希 → 分析结果缓存（TD §13.1：同图缓存命中即一致）。V0.2 迁移到 DB。 */
  private final Map<String, ApiDtos.PalmAnalyzeResponse> responseCache =
      Collections.synchronizedMap(new LinkedHashMap<>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ApiDtos.PalmAnalyzeResponse> eldest) {
          return size() > RESPONSE_CACHE_MAX;
        }
      });
  /** 图哈希 → 手掌校验结论缓存：同一张图不再重复支付一次 LLM 校验调用。 */
  private final Map<String, ApiDtos.PalmImageValidationResponse> validationCache =
      Collections.synchronizedMap(new LinkedHashMap<>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ApiDtos.PalmImageValidationResponse> eldest) {
          return size() > RESPONSE_CACHE_MAX;
        }
      });

  private final SessionRecordRepository sessionRecordRepository;
  private final AppEventRepository appEventRepository;
  private final PalmRecordRepository palmRecordRepository;
  private final MonthlyReportRepository monthlyReportRepository;
  private final LlmClientService llmClientService;
  private final ObjectMapper objectMapper;
  private final UserTokenService userTokenService;
  private final PerceptionClient perceptionClient;
  private final LoreService loreService;
  private final Executor eventExecutor;
  private final PhotoStorageService photoStorageService;

  public PalmistryService(
      SessionRecordRepository sessionRecordRepository,
      AppEventRepository appEventRepository,
      PalmRecordRepository palmRecordRepository,
      MonthlyReportRepository monthlyReportRepository,
      LlmClientService llmClientService,
      ObjectMapper objectMapper,
      UserTokenService userTokenService,
      PerceptionClient perceptionClient,
      LoreService loreService) {
    this(sessionRecordRepository, appEventRepository, palmRecordRepository, monthlyReportRepository,
        llmClientService, objectMapper, userTokenService, perceptionClient, loreService, Runnable::run, null);
  }

  @Autowired
  public PalmistryService(
      SessionRecordRepository sessionRecordRepository,
      AppEventRepository appEventRepository,
      PalmRecordRepository palmRecordRepository,
      MonthlyReportRepository monthlyReportRepository,
      LlmClientService llmClientService,
      ObjectMapper objectMapper,
      UserTokenService userTokenService,
      PerceptionClient perceptionClient,
      LoreService loreService,
      @Qualifier("eventExecutor") Executor eventExecutor,
      PhotoStorageService photoStorageService) {
    this.sessionRecordRepository = sessionRecordRepository;
    this.appEventRepository = appEventRepository;
    this.palmRecordRepository = palmRecordRepository;
    this.monthlyReportRepository = monthlyReportRepository;
    this.llmClientService = llmClientService;
    this.objectMapper = objectMapper;
    this.userTokenService = userTokenService;
    this.perceptionClient = perceptionClient;
    this.loreService = loreService;
    this.eventExecutor = eventExecutor;
    this.photoStorageService = photoStorageService;
  }

  /** 记录模块统一入口校验: 拒绝伪造或未知来源的 userId。 */
  private String requireValidUserId(String userId) {
    if (!userTokenService.verifyUserId(userId)) {
      throw new IllegalArgumentException("用户身份无效，请重新获取身份后使用");
    }
    return userId;
  }

  /** 服务端体积护栏：客户端压缩失效或被绕过时，拒绝超大 base64 图片。 */
  private void requireReasonableImage(String imageData) {
    if (imageData != null && imageData.length() > MAX_IMAGE_CHARS) {
      throw new IllegalArgumentException("图片过大，请压缩后重新上传");
    }
  }

  /** 图片内容 SHA-256，作为一致性缓存键；空图返回 null（不缓存）。 */
  private String sha256Hex(String imageData) {
    if (imageData == null || imageData.isBlank()) {
      return null;
    }
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(imageData.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return "sha256:" + java.util.HexFormat.of().formatHex(hash);
    } catch (Exception ex) {
      return null;
    }
  }

  /** 违禁内容审查：任一文本命中红线即返回 true。 */
  private boolean containsBannedClaims(String... texts) {
    if (texts == null) {
      return false;
    }
    for (String text : texts) {
      if (text != null && BANNED_CLAIM_PATTERN.matcher(text).find()) {
        return true;
      }
    }
    return false;
  }

  private String[] overviewTexts(LlmPalmOverview overview) {
    String[] texts = new String[1 + overview.freeOverview().size()];
    texts[0] = overview.teaser();
    for (int i = 0; i < overview.freeOverview().size(); i++) {
      texts[i + 1] = overview.freeOverview().get(i).shortInterpretation();
    }
    return texts;
  }

  public ApiDtos.PalmAnalyzeResponse analyzePalm(ApiDtos.AnalyzePalmRequest request) {
    return analyzePalm(request, ApiDtos.AnalyzeProgressListener.NONE);
  }

  public ApiDtos.PalmAnalyzeResponse analyzePalm(
      ApiDtos.AnalyzePalmRequest request, ApiDtos.AnalyzeProgressListener listener) {
    requireReasonableImage(request.imageData());
    String imageData = request.imageData();
    String imageHash = sha256Hex(imageData);
    if (imageHash != null) {
      ApiDtos.PalmAnalyzeResponse cached = responseCache.get(imageHash);
      if (cached != null) {
        trackEventInternal("analyze_cache_hit", cached.sessionId(), request.source());
        return cached;
      }
      // 同一张图此前已被判定为非手掌：直接拒绝，不再重复支付一次 LLM 调用。
      ApiDtos.PalmImageValidationResponse prior = validationCache.get(imageHash);
      if (prior != null && !prior.accepted() && "ai".equals(prior.source())) {
        throw new ImageRejectedException(prior.reason());
      }
    }

    String sessionId = "PALM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    listener.onStage("perception", 15, "正在扫描掌型编码...");
    // 手型判定（TD §13.1）：优先感知边车的掌型几何；无边车时用 imageHash 确定性兜底，
    // 保证同一张图在任何实例上都得到同一手型（而非 sessionId 随机）。
    PerceptionClient.PerceptionResult perception = perceptionClient.detect(imageData);
    String handType = (perception != null && perception.detected() && perception.suggestedPalmShape() != null)
        ? perception.suggestedPalmShape()
        : pickHandType(imageHash != null ? imageHash : sessionId);
    List<String> tags = pickPersonalityTags(handType);
    String recordMode = normalizeMode(request.recordMode(), "standard");

    List<ApiDtos.PalmLineSummary> freeOverview = List.of(
        new ApiDtos.PalmLineSummary("感情线", "平稳+轻上扬", "你在关系中会先观察再投入，情绪波动不大，偏好可持续的互动模式，短期热烈型关系对你吸引力有限。"),
        new ApiDtos.PalmLineSummary("智慧线", "平直+末端分叉", "你的思考结构是先理性拆解再创意延展，适合做需要框架与表达并重的工作，决策时通常能兼顾效率和质量。"),
        new ApiDtos.PalmLineSummary("生命线", "中深+环抱鱼际", "你的恢复节律较好，面对阶段性高压时抗波动能力不错，但仍要避免连续透支，规律作息会显著放大状态上限。"));

    String rareMark = pickRareMark(imageHash != null ? imageHash : sessionId);
    String teaser = "检测到稀有印记「" + rareMark + "」，完整解析可在报告页查询";
    TracePromptBundle tracePromptBundle = buildTracePromptBundle(request.traces());
    boolean traceConfirmed = tracePromptBundle.confirmed();
    String traceFeatureSummary = tracePromptBundle.summaryText();

    if (traceConfirmed) {
      freeOverview = buildOverviewFromTraceFeatures(tracePromptBundle.features());
      teaser = "已根据你亲手描摹的掌纹生成解读，完整趋势可在报告页继续解锁。";
    }
    if (!"standard".equals(recordMode) && !"single".equals(recordMode)) {
      teaser = "已进入每周记录模式，本次报告更聚焦短期状态波动。";
    }

    boolean llmUsed = false;
    String llmStatus = "fallback_default";

    listener.onStage("generate", 45, "正在生成专属解读...");
    LlmPalmOverviewResult llmResult = generatePalmOverviewWithLlm(
        handType,
        tags,
        rareMark,
        imageData,
        recordMode,
        tracePromptBundle.promptBlock(),
        listener);
    if (llmResult.rejectReason() != null) {
      // 模型判定不是手掌：缓存校验结论并拒绝，前端引导重新拍照。
      if (imageHash != null) {
        validationCache.put(imageHash, new ApiDtos.PalmImageValidationResponse(
            false, 0.99, llmResult.rejectReason(), "ai"));
      }
      throw new ImageRejectedException(llmResult.rejectReason());
    }
    if (imageHash != null && llmResult.used()) {
      // 分析时模型已确认是手掌：写入校验缓存，validate-image 端点对同图直接复用。
      validationCache.put(imageHash, new ApiDtos.PalmImageValidationResponse(
          true, 0.9, "识别为手掌图片", "ai"));
    }
    llmUsed = llmResult.used();
    llmStatus = llmResult.status();
    LlmPalmOverview llmPalmOverview = llmResult.overview();
    if (llmPalmOverview != null) {
      if (llmPalmOverview.personalityTags() != null && llmPalmOverview.personalityTags().size() >= 3) {
        tags = llmPalmOverview.personalityTags();
      }
      if (llmPalmOverview.freeOverview() != null && llmPalmOverview.freeOverview().size() >= 3) {
        freeOverview = llmPalmOverview.freeOverview();
      }
      if (llmPalmOverview.teaser() != null && !llmPalmOverview.teaser().isBlank()) {
        teaser = llmPalmOverview.teaser();
      }
    }

    listener.onStage("finalize", 90, "正在整理你的专属报告...");
    Instant now = Instant.now();
    sessions.put(sessionId, new SessionState(sessionId, handType, rareMark, now));
    sessionRecordRepository.save(new SessionRecordEntity(sessionId, "SINGLE", handType, rareMark, now));
    trackEventInternal("single_analyze", sessionId, request.source());

    ApiDtos.PalmFeatureSet featureSet = buildFeatureSet(imageHash, perception, handType);
    ApiDtos.PalmAnalyzeResponse response = new ApiDtos.PalmAnalyzeResponse(
        sessionId,
        handType,
        tags,
        freeOverview,
        rareMark,
        teaser,
        SLOGAN,
          traceConfirmed,
          traceFeatureSummary,
        llmUsed,
        llmStatus,
        featureSet);
    if (imageHash != null) {
      responseCache.put(imageHash, response);
    }
    return response;
  }

  /** 构建 PalmFeatureSet 契约（TD §4）：感知边车给出几何依据，降级时标注 heuristic_hash。 */
  private ApiDtos.PalmFeatureSet buildFeatureSet(
      String imageHash, PerceptionClient.PerceptionResult perception, String handType) {
    boolean fromPerception = perception != null && perception.detected();
    ApiDtos.ShapeBasis basis = fromPerception
        ? new ApiDtos.ShapeBasis(perception.palmRatio(), perception.fingerRatio())
        : null;
    return new ApiDtos.PalmFeatureSet(
        "1.0",
        fromPerception ? "perception_sidecar" : "heuristic_hash",
        imageHash,
        new ApiDtos.PalmShapeFeature(handType, fromPerception ? 0.9 : null, basis),
        new ApiDtos.QualityFeature(null, null, fromPerception && perception.retake()));
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
    if (llmSections != null && llmSections.size() >= 2 && !containsBannedClaims(
        llmSections.stream().flatMap(s -> java.util.stream.Stream.of(s.title(), s.detail(), s.cyberTip())).toArray(String[]::new))) {
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
    if (llmCpNarrative != null && !containsBannedClaims(
        llmCpNarrative.comboName(), llmCpNarrative.interpretation(), llmCpNarrative.tip())) {
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
    String userId = requireValidUserId(request.userId());
    requireReasonableImage(request.imageData());
    LocalDate recordDate = parseRecordDate(request.recordDate());
    String mode = normalizeMode(request.recordMode(), "quick");
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
    trackEventInternal("weekly_record", "REC-" + saved.getId(), mode);

    return new ApiDtos.WeeklyRecordResponse(
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
        monthCount >= 4,
        (int) monthCount,
        4);
  }

  public ApiDtos.CalendarResponse getCalendar(String userId, String yearMonth) {
    requireValidUserId(userId);
    YearMonth month = parseYearMonth(yearMonth);
    LocalDate start = month.atDay(1);
    LocalDate end = month.atEndOfMonth();
    List<PalmRecordRepository.RecordSummary> rows =
        palmRecordRepository.findByUserIdAndRecordDateBetweenOrderByRecordDateAscCreatedAtAsc(
        userId,
        start,
        end);

    List<ApiDtos.CalendarDayRecord> records = new ArrayList<>();
    List<ApiDtos.EnergyTrendPoint> trend = new ArrayList<>();
    for (PalmRecordRepository.RecordSummary row : rows) {
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
    requireValidUserId(userId);
    LocalDate recordDate = parseRecordDate(date);
    PalmRecordRepository.RecordSummary row = palmRecordRepository
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
    requireValidUserId(request.userId());
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
    return new ApiDtos.UpdateRecordNoteResponse(true, request.recordId(), request.userNote());
  }

  public ApiDtos.MonthlyReportResponse getMonthlyReport(String userId, String yearMonthText) {
    requireValidUserId(userId);
    YearMonth yearMonth = parseYearMonth(yearMonthText);
    LocalDate start = yearMonth.atDay(1);
    LocalDate end = yearMonth.atEndOfMonth();

    List<PalmRecordRepository.RecordSummary> rows =
        palmRecordRepository.findByUserIdAndRecordDateBetweenOrderByRecordDateAscCreatedAtAsc(
        userId,
        start,
        end);

    List<ApiDtos.EnergyTrendPoint> trend = new ArrayList<>();
    for (PalmRecordRepository.RecordSummary row : rows) {
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

  public ApiDtos.PalmImageValidationResponse validatePalmImage(String imageData) {
    if (imageData == null || imageData.isBlank()) {
      return new ApiDtos.PalmImageValidationResponse(false, 0.0, "这张图片不是手相，请上传清晰的手掌照片。", "empty");
    }

    String imageHash = sha256Hex(imageData);
    if (imageHash != null) {
      ApiDtos.PalmImageValidationResponse cached = validationCache.get(imageHash);
      if (cached != null) {
        return cached;
      }
    }

    if (llmClientService.isAvailable()) {
      try {
        String systemPrompt = "你是手相图片审核模型，只能输出严格 JSON。先判断图片是不是手相图片。";
        String userPrompt = "请只返回 JSON {\"accepted\":true/false,\"confidence\":0到1的小数,\"reason\":\"\"}。"
            + "如果图片主体是一只清晰的人类手掌，可用于手相分析，则 accepted 为 true。"
            + "如果不是手相图片，例如多人、动物、物品、风景、脸部、全身照、截图、拼图、卡通图，则 accepted 为 false。"
            + "如果 accepted 为 false，reason 必须直接写成：这张图片不是手相，请上传清晰的手掌照片。"
            + "如果 accepted 为 true，reason 用一句中文简要说明为什么判定为手相图片。";
        // 校验专用通道：图片是判断依据，失败不走去图降级，仅换模型重试一次。
        String content = llmClientService.chatForValidation(systemPrompt, userPrompt, imageData);
        JsonNode node = parseJsonNode(content);
        boolean accepted = node.path("accepted").asBoolean(
            node.path("isPalm").asBoolean(node.path("valid").asBoolean(false)));
        double confidence = clampConfidence(node.path("confidence").asDouble(accepted ? 0.94 : 0.12));
        String reason = normalizeText(
            node.path("reason").asText(accepted ? "识别为手掌图片" : "这张图片不是手相，请上传清晰的手掌照片。"),
            64);
        if (!accepted) {
          reason = "这张图片不是手相，请上传清晰的手掌照片。";
        }
        ApiDtos.PalmImageValidationResponse response = new ApiDtos.PalmImageValidationResponse(accepted, confidence, reason, "ai");
        if (imageHash != null) {
          validationCache.put(imageHash, response);
        }
        return response;
      } catch (Exception ignored) {
        // 校验调用失败不应拒绝用户：退回本地启发式判定。
      }
    }

    boolean accepted = validatePalmImageLocally(imageData);
    String reason = accepted ? "通过本地兜底校验" : "这张图片不是手相，请上传清晰的手掌照片。";
    ApiDtos.PalmImageValidationResponse response =
        new ApiDtos.PalmImageValidationResponse(accepted, accepted ? 0.72 : 0.08, reason, "heuristic");
    if (imageHash != null) {
      validationCache.put(imageHash, response);
    }
    return response;
  }

  private SessionState requireSession(String sessionId) {
    SessionState session = sessions.get(sessionId);
    if (session == null) {
      session = restoreSessionFromRepository(sessionId);
    }
    if (session == null) {
      throw new IllegalArgumentException("会话不存在或已过期: " + sessionId);
    }
    return session;
  }

  /**
   * 服务重启或内存缓存被清理后，从数据库恢复会话，
   * 保证深度解锁与稀有印记查询不因重启失效。
   */
  private SessionState restoreSessionFromRepository(String sessionId) {
    if (sessionId == null || sessionId.isBlank()) {
      return null;
    }
    Optional<SessionRecordEntity> record = sessionRecordRepository.findById(sessionId);
    if (record.isEmpty()) {
      return null;
    }
    SessionRecordEntity entity = record.get();
    SessionState restored = new SessionState(
        entity.getSessionId(),
        entity.getSubject(),
        entity.getRareMark() == null ? "凤凰眼" : entity.getRareMark(),
        entity.getCreatedAt() == null ? Instant.now() : entity.getCreatedAt());
    sessions.put(sessionId, restored);
    return restored;
  }

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

  /** 埋点异步落库：不阻塞分析主链路；失败静默丢弃（指标事件可容忍）。 */
  private void trackEventInternal(String eventName, String sessionId, String channel) {
    Instant at = Instant.now();
    eventExecutor.execute(() -> {
      try {
        appEventRepository.save(new AppEventEntity(eventName, sessionId, channel, at));
      } catch (Exception ignored) {
        // 埋点失败不影响主流程
      }
    });
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
      return new ApiDtos.PalmLineSummary(defaultName, "未确认", "你未手动描摹该线，当前结果基于通用模型推断，仅供参考。");
    }

    String tags = "长度" + feature.lengthLabel() + " / 弯曲" + feature.curvatureLabel() + " / " + feature.continuityLabel();
    String text = feature.naturalText() + "建议你结合最近30天的实际状态做一次对照复盘。";
    return new ApiDtos.PalmLineSummary(feature.lineName(), tags, text);
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
      String tracePromptBlock,
      ApiDtos.AnalyzeProgressListener listener) {
    if (!llmClientService.isAvailable()) {
      return new LlmPalmOverviewResult(false, "llm_disabled", null, null);
    }

    String systemPrompt = "你是手相研究所的资深解读师，风格专业、具体、可验证。优先返回严格 JSON，不要 markdown。" + CONTENT_GUARDRAIL;
    String loreBlock = loreService.retrieve(handType, rareMark);
    String userPrompt = "请基于输入生成 JSON，字段结构必须是"
        + "{\"imageAccepted\":true,\"rejectReason\":\"\",\"personalityTags\":[\"\",\"\",\"\"],\"freeOverview\":[{\"lineName\":\"感情线\",\"tags\":\"\",\"shortInterpretation\":\"\"},{\"lineName\":\"智慧线\",\"tags\":\"\",\"shortInterpretation\":\"\"},{\"lineName\":\"生命线\",\"tags\":\"\",\"shortInterpretation\":\"\"}],\"teaser\":\"\"}。"
        + "先校验图片：如果图片主体不是一只清晰的人类手掌正面，imageAccepted 必须为 false，"
        + "rejectReason 固定为“这张图片不是手相，请上传清晰的手掌照片。”，其余字段留空；"
        + "是手掌则 imageAccepted 为 true、rejectReason 为空字符串，并生成完整解读。"
        + "输入：handType=" + handType + "，tags=" + String.join("/", tags) + "，rareMark=" + rareMark + "。"
        + "recordMode=" + recordMode + "。"
        + (tracePromptBlock == null || tracePromptBlock.isBlank() ? "" : "\\n" + tracePromptBlock)
        + (loreBlock.isBlank() ? "" : "\n[Knowledge]\n" + loreBlock + "\n以上知识条目是唯一的相术依据，只做润色与连接，不得输出条目之外的相术断言。")
        + "要求：中文、语气可信。每条 shortInterpretation 120-180 字，必须包含线条形态描述+性格/状态解读+具体可执行建议(动作+频次)+时间窗口或适用情境。"
        + "tags 用 2-4 个短语，用 / 分隔。teaser 40-70 字，提示稀有印记但不泄露完整解析。避免空泛套话。";

    try {
      String content = generateContentStreaming(systemPrompt, userPrompt, imageData, listener);
      try {
        JsonNode node = parseJsonNode(content);
        // 校验与解读合并为同一次调用（省一次 8-15s 的视觉模型往返）。
        if (!node.path("imageAccepted").asBoolean(true)) {
          String rejectReason = normalizeText(
              node.path("rejectReason").asText("这张图片不是手相，请上传清晰的手掌照片。"), 64);
          if (rejectReason.isBlank()) {
            rejectReason = "这张图片不是手相，请上传清晰的手掌照片。";
          }
          return new LlmPalmOverviewResult(false, "image_rejected", null, rejectReason);
        }
        List<String> llmTags = List.of(
            node.path("personalityTags").path(0).asText(tags.get(0)),
            node.path("personalityTags").path(1).asText(tags.get(1)),
            node.path("personalityTags").path(2).asText(tags.get(2)));

        List<ApiDtos.PalmLineSummary> llmOverview = List.of(
            new ApiDtos.PalmLineSummary(
                node.path("freeOverview").path(0).path("lineName").asText("感情线"),
                node.path("freeOverview").path(0).path("tags").asText("平稳"),
                node.path("freeOverview").path(0).path("shortInterpretation")
                  .asText("你的感情线走向更偏平稳，末端微上扬，说明你在关系里倾向稳步投入、重视长期一致性。若近期沟通出现回避迹象，建议每周固定1次20分钟对话复盘，按‘事实-感受-需求’三步走，减少误读。适用情境：关系进入磨合期或工作压力上升时。")),
            new ApiDtos.PalmLineSummary(
                node.path("freeOverview").path(1).path("lineName").asText("智慧线"),
                node.path("freeOverview").path(1).path("tags").asText("分叉"),
                node.path("freeOverview").path(1).path("shortInterpretation")
                  .asText("你的智慧线较清晰且末端分叉，代表理性拆解与创意延展并行。若近期决策反复，建议用‘3指标对照法’：成本/收益/可逆性各列1条证据，再做取舍。适用情境：项目选型、职业路径切换或学业方向调整期。")),
            new ApiDtos.PalmLineSummary(
                node.path("freeOverview").path(2).path("lineName").asText("生命线"),
                node.path("freeOverview").path(2).path("tags").asText("中深"),
                node.path("freeOverview").path(2).path("shortInterpretation")
                  .asText("你的生命线弧度饱满且深度适中，说明体能底盘稳定，但在高压阶段易出现延迟性疲劳。建议连续工作2天后安排1次30分钟低强度运动与拉伸，固定睡眠窗口，减少能量透支。适用情境：连续加班或高密度输出周期。")));

        String llmTeaser = node.path("teaser").asText("检测到稀有印记「" + rareMark + "」，完整解析可在报告页查询");
        LlmPalmOverview overview = new LlmPalmOverview(llmTags, llmOverview, llmTeaser);
        if (containsBannedClaims(overviewTexts(overview))) {
          return new LlmPalmOverviewResult(false, "banned_content_filtered", null, null);
        }
        return new LlmPalmOverviewResult(true, "ok_json", overview, null);
      } catch (Exception parseError) {
        LlmPalmOverview textFallback = buildPalmOverviewFromText(content, tags, rareMark);
        if (textFallback != null) {
          if (containsBannedClaims(overviewTexts(textFallback))) {
            return new LlmPalmOverviewResult(false, "banned_content_filtered", null, null);
          }
          return new LlmPalmOverviewResult(true, "ok_text", textFallback, null);
        }
        return new LlmPalmOverviewResult(false, "parse_failed:" + shortReason(parseError), null, null);
      }
    } catch (Exception callError) {
      return new LlmPalmOverviewResult(false, "call_failed:" + shortReason(callError), null, null);
    }
  }

  /**
   * 优先流式生成并把真实生成进度回调给 listener（SSE 用）；
   * 流式失败时回退到带完整重试链的非流式调用。
   */
  private String generateContentStreaming(
      String systemPrompt,
      String userPrompt,
      String imageData,
      ApiDtos.AnalyzeProgressListener listener) {
    if (listener == ApiDtos.AnalyzeProgressListener.NONE) {
      return llmClientService.chat(systemPrompt, userPrompt, imageData);
    }
    AtomicInteger generated = new AtomicInteger();
    AtomicLong lastSentAt = new AtomicLong();
    try {
      return llmClientService.chatStream(systemPrompt, userPrompt, imageData, delta -> {
        int chars = generated.addAndGet(delta.codePointCount(0, delta.length()));
        long now = System.currentTimeMillis();
        long last = lastSentAt.get();
        if (now - last >= 600 && lastSentAt.compareAndSet(last, now)) {
          listener.onGeneratedChars(chars);
        }
      });
    } catch (Exception streamError) {
      return llmClientService.chat(systemPrompt, userPrompt, imageData);
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
        new ApiDtos.PalmLineSummary("感情线", "模型文本解析", s1),
        new ApiDtos.PalmLineSummary("智慧线", "模型文本解析", s2),
        new ApiDtos.PalmLineSummary("生命线", "模型文本解析", s3));

    String teaser = "检测到稀有印记「" + rareMark + "」，模型已完成文本解读，完整趋势可继续解锁查看。";
    return new LlmPalmOverview(tags, overview, teaser);
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
    if (msg.length() > 96) {
      return msg.substring(0, 96);
    }
    return msg;
  }

  private boolean validatePalmImageLocally(String imageData) {
    BufferedImage sourceImage = decodeImage(imageData);
    if (sourceImage == null) {
      return false;
    }

    int size = 64;
    BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = scaled.createGraphics();
    try {
      graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      graphics.drawImage(sourceImage, 0, 0, size, size, null);
    } finally {
      graphics.dispose();
    }

    int totalCells = size * size;
    byte[] mask = new byte[totalCells];
    int skinCount = 0;

    for (int y = 0; y < size; y++) {
      for (int x = 0; x < size; x++) {
        int rgb = scaled.getRGB(x, y);
        int red = (rgb >> 16) & 0xff;
        int green = (rgb >> 8) & 0xff;
        int blue = rgb & 0xff;
        if (isSkinTone(red, green, blue)) {
          mask[y * size + x] = 1;
          skinCount += 1;
        }
      }
    }

    if (skinCount < Math.round(totalCells * 0.12)) {
      return false;
    }

    boolean[] visited = new boolean[totalCells];
    int dominantComponent = 0;
    int dominantMinX = 0;
    int dominantMinY = 0;
    int dominantMaxX = 0;
    int dominantMaxY = 0;
    int largeComponents = 0;
    int[] queue = new int[totalCells];
    int[][] neighbors = new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    for (int index = 0; index < totalCells; index++) {
      if (mask[index] == 0 || visited[index]) {
        continue;
      }

      int componentSize = 0;
      int minX = size;
      int minY = size;
      int maxX = 0;
      int maxY = 0;
      int head = 0;
      int tail = 0;
      queue[tail++] = index;
      visited[index] = true;

      while (head < tail) {
        int current = queue[head++];
        componentSize += 1;
        int x = current % size;
        int y = current / size;
        minX = Math.min(minX, x);
        minY = Math.min(minY, y);
        maxX = Math.max(maxX, x);
        maxY = Math.max(maxY, y);

        for (int[] neighbor : neighbors) {
          int nx = x + neighbor[0];
          int ny = y + neighbor[1];
          if (nx < 0 || ny < 0 || nx >= size || ny >= size) {
            continue;
          }
          int nextIndex = ny * size + nx;
          if (mask[nextIndex] == 1 && !visited[nextIndex]) {
            visited[nextIndex] = true;
            queue[tail++] = nextIndex;
          }
        }
      }

      if (componentSize > dominantComponent) {
        dominantComponent = componentSize;
        dominantMinX = minX;
        dominantMinY = minY;
        dominantMaxX = maxX;
        dominantMaxY = maxY;
      }
      if (componentSize >= Math.round(totalCells * 0.04)) {
        largeComponents += 1;
      }
    }

    if (dominantComponent <= 0) {
      return false;
    }

    double dominantRatio = dominantComponent / (double) totalCells;
    int dominantWidth = dominantMaxX - dominantMinX + 1;
    int dominantHeight = dominantMaxY - dominantMinY + 1;
    double dominantBoxRatio = (dominantWidth * dominantHeight) / (double) totalCells;
    double centerX = (dominantMinX + dominantMaxX) / 2.0 / size;
    double centerY = (dominantMinY + dominantMaxY) / 2.0 / size;

    int centerStart = (int) Math.floor(size * 0.25);
    int centerEnd = (int) Math.floor(size * 0.75);
    int centerSkin = 0;
    for (int y = centerStart; y < centerEnd; y++) {
      for (int x = centerStart; x < centerEnd; x++) {
        if (mask[y * size + x] == 1) {
          centerSkin += 1;
        }
      }
    }

    int centerThreshold = (int) Math.round((centerEnd - centerStart) * (centerEnd - centerStart) * 0.16);
    boolean centerAligned = centerX >= 0.22 && centerX <= 0.78 && centerY >= 0.22 && centerY <= 0.80;
    return dominantRatio >= 0.18
        && dominantRatio <= 0.82
        && dominantBoxRatio >= 0.24
        && centerAligned
        && centerSkin >= centerThreshold
        && largeComponents <= 2;
  }

  private BufferedImage decodeImage(String imageData) {
    try {
      String payload = imageData.trim();
      int commaIndex = payload.indexOf(',');
      if (payload.startsWith("data:") && commaIndex >= 0) {
        payload = payload.substring(commaIndex + 1);
      }
      byte[] bytes = Base64.getDecoder().decode(payload);
      return ImageIO.read(new ByteArrayInputStream(bytes));
    } catch (Exception ignored) {
      return null;
    }
  }

  private boolean isSkinTone(int red, int green, int blue) {
    int max = Math.max(red, Math.max(green, blue));
    int min = Math.min(red, Math.min(green, blue));
    return red > 95 && green > 40 && blue > 20 && (max - min) > 15 && Math.abs(red - green) > 15
        && red > green && red > blue;
  }

  private double clampConfidence(double value) {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      return 0.0;
    }
    return Math.max(0.0, Math.min(1.0, value));
  }

  private String normalizeText(String text, int maxLength) {
    if (text == null) {
      return "";
    }
    String normalized = text.replaceAll("\\s+", " ").trim();
    if (normalized.length() > maxLength) {
      return normalized.substring(0, maxLength);
    }
    return normalized;
  }

  private List<ApiDtos.DeepSection> generateSingleDeepWithLlm(SessionState session) {
    if (!llmClientService.isAvailable()) {
      return null;
    }

    String systemPrompt = "你是手相研究所的深度报告引擎。必须返回严格 JSON 数组，不要 markdown。" + CONTENT_GUARDRAIL;
    String userPrompt = "请返回 JSON 数组 sections，长度3。每个元素结构"
        + "{\"title\":\"\",\"detail\":\"\",\"cyberTip\":\"\"}。"
        + "输入：handType=" + session.handType() + " rareMark=" + session.rareMark() + "。"
        + "要求：中文，detail 90-160 字，要体现趋势+触发条件+行动建议；cyberTip 28-60 字。";

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

    String systemPrompt = "你是CP合拍文案引擎。必须返回严格 JSON，不要 markdown。" + CONTENT_GUARDRAIL;
    String userPrompt = "请返回 JSON {\"comboName\":\"\",\"interpretation\":\"\",\"tip\":\"\"}。"
        + "输入：A=" + request.userA().handType() + "/" + request.userA().mbti()
        + " B=" + request.userB().handType() + "/" + request.userB().mbti()
        + " score=" + score + " defaultCombo=" + comboName + "。"
        + "要求：中文，interpretation 40-90 字，tip 20-40 字。";

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
      String teaser) {
  }

  private record LlmPalmOverviewResult(
      boolean used,
      String status,
      LlmPalmOverview overview,
      String rejectReason) {
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
    PalmRecordRepository.RecordSummary maxEnergy = rows.stream().max(Comparator.comparingInt(PalmRecordRepository.RecordSummary::getEnergyLevel)).orElse(rows.get(0));
    PalmRecordRepository.RecordSummary minEnergy = rows.stream().min(Comparator.comparingInt(PalmRecordRepository.RecordSummary::getEnergyLevel)).orElse(rows.get(0));
    return yearMonth + " 你完成了 " + rows.size() + " 次记录，本月主导能量为「" + dominant + "」。"
        + "高峰出现在 " + maxEnergy.getRecordDate() + "，低谷出现在 " + minEnergy.getRecordDate()
        + "。建议围绕高峰时段安排关键任务，在低谷期留出恢复窗口。";
  }
}
