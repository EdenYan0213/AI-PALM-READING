package com.palmistrylab.api.palm;

import com.palmistrylab.api.common.InMemoryLruCache;
import com.palmistrylab.api.common.TextUtils;
import com.palmistrylab.api.llm.LlmClient;
import com.palmistrylab.api.metrics.MetricsService;
import com.palmistrylab.api.palm.dto.AnalyzeProgressListener;
import com.palmistrylab.api.palm.dto.AnalyzePalmRequest;
import com.palmistrylab.api.palm.dto.PalmAnalyzeResponse;
import com.palmistrylab.api.palm.dto.PalmFeatureSet;
import com.palmistrylab.api.palm.dto.PalmImageValidationResponse;
import com.palmistrylab.api.palm.dto.PalmLineSummary;
import com.palmistrylab.api.palm.dto.RareMarkQueryResponse;
import com.palmistrylab.api.palm.dto.ShapeBasis;
import com.palmistrylab.api.palm.dto.UnlockDeepResponse;
import com.palmistrylab.api.perception.PerceptionClient;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 单人手相分析编排（TD §2 Orchestrator 层）：
 * 感知 → 量化兜底 → 叙事生成（校验并入同一次视觉调用）→ 会话/缓存落位。
 * 图哈希缓存保证同图结果一致；校验结论缓存避免同图重复支付 LLM 调用。
 */
@Service
public class PalmAnalysisService {

  private static final String SLOGAN = "你的互联网手相搭子，科学聊玄学";
  private static final String WECHAT_ID = "CyberPalm-Master";
  private static final int RESPONSE_CACHE_MAX = 500;

  private final com.palmistrylab.api.palm.SessionRecordRepository sessionRecordRepository;
  private final PalmSessionService palmSessionService;
  private final PalmNarrativeWriter narrativeWriter;
  private final LlmClient llmClient;
  private final PerceptionClient perceptionClient;
  private final MetricsService metricsService;
  private final TraceGeometryAnalyzer traceGeometryAnalyzer;
  private final LocalPalmImageValidator localPalmImageValidator;

  /** 图哈希 → 分析结果缓存（TD §13.1：同图缓存命中即一致）。V0.2 迁移到 DB。 */
  private final InMemoryLruCache<String, PalmAnalyzeResponse> responseCache = new InMemoryLruCache<>(RESPONSE_CACHE_MAX);
  /** 图哈希 → 手掌校验结论缓存：同一张图不再重复支付一次 LLM 校验调用。 */
  private final InMemoryLruCache<String, PalmImageValidationResponse> validationCache = new InMemoryLruCache<>(RESPONSE_CACHE_MAX);

  public PalmAnalysisService(
      com.palmistrylab.api.palm.SessionRecordRepository sessionRecordRepository,
      PalmSessionService palmSessionService,
      PalmNarrativeWriter narrativeWriter,
      LlmClient llmClient,
      PerceptionClient perceptionClient,
      MetricsService metricsService,
      TraceGeometryAnalyzer traceGeometryAnalyzer,
      LocalPalmImageValidator localPalmImageValidator) {
    this.sessionRecordRepository = sessionRecordRepository;
    this.palmSessionService = palmSessionService;
    this.narrativeWriter = narrativeWriter;
    this.llmClient = llmClient;
    this.perceptionClient = perceptionClient;
    this.metricsService = metricsService;
    this.traceGeometryAnalyzer = traceGeometryAnalyzer;
    this.localPalmImageValidator = localPalmImageValidator;
  }

  public PalmAnalyzeResponse analyzePalm(AnalyzePalmRequest request) {
    return analyzePalm(request, AnalyzeProgressListener.NONE);
  }

  public PalmAnalyzeResponse analyzePalm(AnalyzePalmRequest request, AnalyzeProgressListener listener) {
    com.palmistrylab.api.common.ImageConstraints.requireReasonableImage(request.imageData());
    String imageData = request.imageData();
    String imageHash = sha256Hex(imageData);
    if (imageHash != null) {
      PalmAnalyzeResponse cached = responseCache.get(imageHash);
      if (cached != null) {
        metricsService.recordEvent("analyze_cache_hit", cached.sessionId(), request.source());
        return cached;
      }
      // 同一张图此前已被判定为非手掌：直接拒绝，不再重复支付一次 LLM 调用。
      PalmImageValidationResponse prior = validationCache.get(imageHash);
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
    String recordMode = TextUtils.normalizeMode(request.recordMode(), "standard");

    List<PalmLineSummary> freeOverview = List.of(
        new PalmLineSummary("感情线", "平稳+轻上扬", "你在关系中会先观察再投入，情绪波动不大，偏好可持续的互动模式，短期热烈型关系对你吸引力有限。"),
        new PalmLineSummary("智慧线", "平直+末端分叉", "你的思考结构是先理性拆解再创意延展，适合做需要框架与表达并重的工作，决策时通常能兼顾效率和质量。"),
        new PalmLineSummary("生命线", "中深+环抱鱼际", "你的恢复节律较好，面对阶段性高压时抗波动能力不错，但仍要避免连续透支，规律作息会显著放大状态上限。"));

    String rareMark = pickRareMark(imageHash != null ? imageHash : sessionId);
    String teaser = "检测到稀有印记「" + rareMark + "」，完整解析可在报告页查询";
    TraceGeometryAnalyzer.TracePromptBundle tracePromptBundle =
        traceGeometryAnalyzer.buildTracePromptBundle(request.traces());
    boolean traceConfirmed = tracePromptBundle.confirmed();
    String traceFeatureSummary = tracePromptBundle.summaryText();

    if (traceConfirmed) {
      freeOverview = traceGeometryAnalyzer.buildOverviewFromFeatures(tracePromptBundle.features());
      teaser = "已根据你亲手描摹的掌纹生成解读，完整趋势可在报告页继续解锁。";
    }
    if (!"standard".equals(recordMode) && !"single".equals(recordMode)) {
      teaser = "已进入每周记录模式，本次报告更聚焦短期状态波动。";
    }

    boolean llmUsed = false;
    String llmStatus = "fallback_default";

    listener.onStage("generate", 45, "正在生成专属解读...");
    PalmNarrativeWriter.LlmPalmOverviewResult llmResult = narrativeWriter.generatePalmOverview(
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
        validationCache.put(imageHash, new PalmImageValidationResponse(
            false, 0.99, llmResult.rejectReason(), "ai"));
      }
      throw new ImageRejectedException(llmResult.rejectReason());
    }
    if (imageHash != null && llmResult.used()) {
      // 分析时模型已确认是手掌：写入校验缓存，validate-image 端点对同图直接复用。
      validationCache.put(imageHash, new PalmImageValidationResponse(
          true, 0.9, "识别为手掌图片", "ai"));
    }
    llmUsed = llmResult.used();
    llmStatus = llmResult.status();
    PalmNarrativeWriter.LlmPalmOverview llmPalmOverview = llmResult.overview();
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
    palmSessionService.put(new PalmSessionService.SessionState(sessionId, handType, rareMark, now));
    sessionRecordRepository.save(new SessionRecordEntity(sessionId, "SINGLE", handType, rareMark, now));
    metricsService.recordEvent("single_analyze", sessionId, request.source());

    PalmFeatureSet featureSet = buildFeatureSet(imageHash, perception, handType, tracePromptBundle, rareMark);
    PalmAnalyzeResponse response = new PalmAnalyzeResponse(
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

  public UnlockDeepResponse unlockDeep(String sessionId) {
    PalmSessionService.SessionState session = palmSessionService.require(sessionId);
    metricsService.recordEvent("single_ad_unlock", sessionId, "rewarded_video");
    List<com.palmistrylab.api.palm.dto.DeepSection> sections = new java.util.ArrayList<>();
    sections.add(new com.palmistrylab.api.palm.dto.DeepSection(
        "深度趋势 · 感情线",
        "未来90天你的关系走势是先观察、后升温。前3周更适合建立边界和节奏，中段会出现一次高质量沟通窗口，若把模糊期待说清，关系稳定性会明显提升。",
        "每周固定一次20分钟复盘：先讲感受，再讲需求，最后约定下一步动作。"));
    sections.add(new com.palmistrylab.api.palm.dto.DeepSection(
        "深度趋势 · 智慧线",
        "你近期会在两条机会路径之间反复比较：一条偏稳定现金流，一条偏成长空间。你的优势是信息整合快，但易在细节里过拟合，导致迟迟不拍板。",
        "采用7天双轨试运行，记录投入产出比与心智负担，再做最终选择。"));
    sections.add(new com.palmistrylab.api.palm.dto.DeepSection(
        "深度趋势 · 生命线",
        "你的体能底盘不错，但高压期后会出现延迟性疲劳。若连续晚睡3天，专注质量会在第4天明显下滑，进而影响判断稳定性与执行效率。",
        "把高强度任务集中在上午，晚间保留1小时低负荷收尾和恢复拉伸。"));

    List<com.palmistrylab.api.palm.dto.DeepSection> llmSections =
        narrativeWriter.generateSingleDeep(session.handType(), session.rareMark());
    if (llmSections != null && llmSections.size() >= 2 && !ContentGuardrail.containsBannedClaims(
        llmSections.stream().flatMap(s -> java.util.stream.Stream.of(s.title(), s.detail(), s.cyberTip())).toArray(String[]::new))) {
      sections = llmSections;
    }

    return new UnlockDeepResponse(session.sessionId(), true, "AD_REWARDED", sections);
  }

  public RareMarkQueryResponse queryRareMark(String sessionId) {
    PalmSessionService.SessionState session = palmSessionService.require(sessionId);
    int remainQuota = Math.abs(sessionId.hashCode() % 35) + 5;
    metricsService.recordEvent("rare_mark_query", sessionId, "report_page");

    return new RareMarkQueryResponse(
        sessionId,
        session.rareMark(),
        WECHAT_ID,
        remainQuota,
        "印记解析由专属大师提供，添加企业微信后自动下发图文报告");
  }

  public PalmImageValidationResponse validatePalmImage(String imageData) {
    if (imageData == null || imageData.isBlank()) {
      return new PalmImageValidationResponse(false, 0.0, "这张图片不是手相，请上传清晰的手掌照片。", "empty");
    }

    String imageHash = sha256Hex(imageData);
    if (imageHash != null) {
      PalmImageValidationResponse cached = validationCache.get(imageHash);
      if (cached != null) {
        return cached;
      }
    }

    if (llmClient.isAvailable()) {
      try {
        String systemPrompt = "你是手相图片审核模型，只能输出严格 JSON。先判断图片是不是手相图片。";
        String userPrompt = "请只返回 JSON {\"accepted\":true/false,\"confidence\":0到1的小数,\"reason\":\"\"}。"
            + "如果图片主体是一只清晰的人类手掌，可用于手相分析，则 accepted 为 true。"
            + "如果不是手相图片，例如多人、动物、物品、风景、脸部、全身照、截图、拼图、卡通图，则 accepted 为 false。"
            + "如果 accepted 为 false，reason 必须直接写成：这张图片不是手相，请上传清晰的手掌照片。"
            + "如果 accepted 为 true，reason 用一句中文简要说明为什么判定为手相图片。";
        // 校验专用通道：图片是判断依据，失败不走去图降级，仅换模型重试一次。
        String content = llmClient.chatForValidation(systemPrompt, userPrompt, imageData);
        com.fasterxml.jackson.databind.JsonNode node = narrativeWriter.parseJson(content);
        boolean accepted = node.path("accepted").asBoolean(
            node.path("isPalm").asBoolean(node.path("valid").asBoolean(false)));
        double confidence = LocalPalmImageValidator.clampConfidence(node.path("confidence").asDouble(accepted ? 0.94 : 0.12));
        String reason = TextUtils.normalizeText(
            node.path("reason").asText(accepted ? "识别为手掌图片" : "这张图片不是手相，请上传清晰的手掌照片。"),
            64);
        if (!accepted) {
          reason = "这张图片不是手相，请上传清晰的手掌照片。";
        }
        PalmImageValidationResponse response = new PalmImageValidationResponse(accepted, confidence, reason, "ai");
        if (imageHash != null) {
          validationCache.put(imageHash, response);
        }
        return response;
      } catch (Exception ignored) {
        // 校验调用失败不应拒绝用户：退回本地启发式判定。
      }
    }

    boolean accepted = localPalmImageValidator.looksLikePalm(imageData);
    String reason = accepted ? "通过本地兜底校验" : "这张图片不是手相，请上传清晰的手掌照片。";
    PalmImageValidationResponse response =
        new PalmImageValidationResponse(accepted, accepted ? 0.72 : 0.08, reason, "heuristic");
    if (imageHash != null) {
      validationCache.put(imageHash, response);
    }
    return response;
  }

  /**
   * 构建 PalmFeatureSet 契约（TD §4 v1.1）：感知边车给出几何依据（降级时标注 heuristic_hash），
   * lines 来自描摹轨迹的确定性几何特征（未描摹为空列表），marks 为图哈希确定性印记。
   */
  private PalmFeatureSet buildFeatureSet(
      String imageHash,
      PerceptionClient.PerceptionResult perception,
      String handType,
      TraceGeometryAnalyzer.TracePromptBundle traceBundle,
      String rareMark) {
    boolean fromPerception = perception != null && perception.detected();
    ShapeBasis basis = fromPerception
        ? new ShapeBasis(perception.palmRatio(), perception.fingerRatio())
        : null;
    List<com.palmistrylab.api.palm.dto.FeatureLine> lines = traceBundle.features().stream()
        .filter(TraceGeometryAnalyzer.LineTraceFeature::available)
        .map(f -> new com.palmistrylab.api.palm.dto.FeatureLine(
            f.lineName(), f.lengthLabel(), f.curvatureLabel(), f.continuityLabel(), f.forked(), f.eventLabel()))
        .toList();
    List<com.palmistrylab.api.palm.dto.FeatureMark> marks = rareMark == null
        ? List.of()
        : List.of(new com.palmistrylab.api.palm.dto.FeatureMark(rareMark, "deterministic_hash"));
    return new PalmFeatureSet(
        "1.1",
        fromPerception ? "perception_sidecar" : "heuristic_hash",
        imageHash,
        new com.palmistrylab.api.palm.dto.PalmShapeFeature(handType, fromPerception ? 0.9 : null, basis),
        new com.palmistrylab.api.palm.dto.QualityFeature(null, null, fromPerception && perception.retake()),
        lines,
        marks);
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

  /** 图片内容 SHA-256，作为一致性缓存键；空图返回 null（不缓存）。 */
  private String sha256Hex(String imageData) {
    if (imageData == null || imageData.isBlank()) {
      return null;
    }
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(imageData.getBytes(StandardCharsets.UTF_8));
      return "sha256:" + java.util.HexFormat.of().formatHex(hash);
    } catch (Exception ex) {
      return null;
    }
  }
}
