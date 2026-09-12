package com.palmistrylab.api.cp;

import com.palmistrylab.api.cp.dto.CpAnalyzeRequest;
import com.palmistrylab.api.cp.dto.CpAnalyzeResponse;
import com.palmistrylab.api.cp.dto.CpDimension;
import com.palmistrylab.api.llm.LlmClient;
import com.palmistrylab.api.metrics.MetricsService;
import com.palmistrylab.api.palm.ContentGuardrail;
import com.palmistrylab.api.palm.PalmNarrativeWriter;
import com.palmistrylab.api.palm.PalmSessionService;
import com.palmistrylab.api.palm.dto.DeepSection;
import com.palmistrylab.api.palm.dto.UnlockDeepResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * CP 合拍引擎（演示阶段）：分数为 MBTI 哈希确定性区间，叙事由 LLM 润色。
 * TD §4 的目标形态是读取双方 PalmFeatureSet 做规则匹配，待 FeatureSet 完整化后接入。
 */
@Service
public class CpService {

  private final LlmClient llmClient;
  private final PalmSessionService palmSessionService;
  private final com.palmistrylab.api.palm.SessionRecordRepository sessionRecordRepository;
  private final PalmNarrativeWriter narrativeWriter;
  private final MetricsService metricsService;

  public CpService(
      LlmClient llmClient,
      PalmSessionService palmSessionService,
      com.palmistrylab.api.palm.SessionRecordRepository sessionRecordRepository,
      PalmNarrativeWriter narrativeWriter,
      MetricsService metricsService) {
    this.llmClient = llmClient;
    this.palmSessionService = palmSessionService;
    this.sessionRecordRepository = sessionRecordRepository;
    this.narrativeWriter = narrativeWriter;
    this.metricsService = metricsService;
  }

  public CpAnalyzeResponse analyzeCp(CpAnalyzeRequest request) {
    String cpSessionId = "CP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    double score = buildCpScore(request.userA().mbti(), request.userB().mbti());
    String combo = buildComboName(request.userA().handType(), request.userB().handType());

    List<CpDimension> dimensions = List.of(
        new CpDimension("感情线趋同度", clamp((int) Math.round(score + 3))),
        new CpDimension("智慧线互补性", clamp((int) Math.round(score - 4))),
        new CpDimension("生活节奏契合度", clamp((int) Math.round(score - 1))));

    String interpretation = "你们属于「" + combo + "」组合，一方负责搭框架，一方负责点亮细节。";
    String tip = "相处 Tips：当逻辑线遇上感性分叉时，先共识目标，再讨论路径。";

    LlmCpNarrative llmCpNarrative = generateCpNarrative(request, combo, score);
    if (llmCpNarrative != null && !ContentGuardrail.containsBannedClaims(
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
    palmSessionService.put(new PalmSessionService.SessionState(cpSessionId, combo, "双生螺旋", now));
    sessionRecordSave(cpSessionId, combo, now);
    metricsService.recordEvent("cp_analyze", cpSessionId, "cp_page");

    return new CpAnalyzeResponse(cpSessionId, score, combo, dimensions, interpretation, tip);
  }

  public UnlockDeepResponse unlockCpDeep(String cpSessionId) {
    palmSessionService.require(cpSessionId);
    metricsService.recordEvent("cp_ad_unlock", cpSessionId, "rewarded_video");
    List<DeepSection> sections = List.of(
        new DeepSection(
            "摩擦点预警",
            "你们在高压场景下容易出现表达时差：一方要结论，一方要共情。",
            "把争议拆成事实、情绪、方案三段式，冲突会明显降低。"),
        new DeepSection(
            "关系升级窗口",
            "未来三周有两次关键升温窗口，分别在周三晚和周末午后。",
            "固定一周一次深度沟通，优先讨论边界和期待。"));

    return new UnlockDeepResponse(cpSessionId, true, "AD_REWARDED", sections);
  }

  private void sessionRecordSave(String cpSessionId, String combo, Instant now) {
    sessionRecordRepository.save(new com.palmistrylab.api.palm.SessionRecordEntity(cpSessionId, "CP", combo, "双生螺旋", now));
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

  private LlmCpNarrative generateCpNarrative(CpAnalyzeRequest request, String comboName, double score) {
    if (!llmClient.isAvailable()) {
      return null;
    }

    String systemPrompt = "你是CP合拍文案引擎。必须返回严格 JSON，不要 markdown。" + ContentGuardrail.GUARDRAIL_TEXT;
    String userPrompt = "请返回 JSON {\"comboName\":\"\",\"interpretation\":\"\",\"tip\":\"\"}。"
        + "输入：A=" + request.userA().handType() + "/" + request.userA().mbti()
        + " B=" + request.userB().handType() + "/" + request.userB().mbti()
        + " score=" + score + " defaultCombo=" + comboName + "。"
        + "要求：中文，interpretation 40-90 字，tip 20-40 字。";

    try {
      String content = llmClient.chat(systemPrompt, userPrompt);
      com.fasterxml.jackson.databind.JsonNode node = narrativeWriter.parseJson(content);
      return new LlmCpNarrative(
          node.path("comboName").asText(comboName),
          node.path("interpretation").asText("你们属于互补型关系，一方建模一方点亮情绪价值。"),
          node.path("tip").asText("先对齐目标，再讨论路径，冲突会明显降低。"));
    } catch (Exception ignored) {
      return null;
    }
  }

  private record LlmCpNarrative(
      String comboName,
      String interpretation,
      String tip) {
  }
}
