package com.palmistrylab.api.cp;

import com.palmistrylab.api.cp.dto.CpAnalyzeRequest;
import com.palmistrylab.api.cp.dto.CpAnalyzeResponse;
import com.palmistrylab.api.cp.dto.CpDimension;
import com.palmistrylab.api.llm.LlmClient;
import com.palmistrylab.api.metrics.MetricsService;
import com.palmistrylab.api.palm.ContentGuardrail;
import com.palmistrylab.api.palm.PalmNarrativeWriter;
import com.palmistrylab.api.palm.PalmRules;
import com.palmistrylab.api.palm.SessionRecordEntity;
import com.palmistrylab.api.palm.PalmSessionService;
import com.palmistrylab.api.palm.SessionRecordRepository;
import com.palmistrylab.api.palm.dto.DeepSection;
import com.palmistrylab.api.palm.dto.FeatureLine;
import com.palmistrylab.api.palm.dto.PalmFeatureSet;
import com.palmistrylab.api.palm.dto.UnlockDeepResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * CP 合拍引擎。
 * 打分双轨：
 * - 默认（无 FeatureSet）：MBTI 哈希确定性区间（演示）；
 * - 双方传入 PalmFeatureSet 时：规则文件驱动的掌型亲和分 + 同名线几何相近度加分（TD §4）。
 * 叙事均由 LLM 润色并过违禁词审查；阈值全部来自 palmistry-rules.yml。
 */
@Service
public class CpService {

  private final LlmClient llmClient;
  private final PalmSessionService palmSessionService;
  private final SessionRecordRepository sessionRecordRepository;
  private final PalmNarrativeWriter narrativeWriter;
  private final MetricsService metricsService;
  private final PalmRules rules;

  public CpService(
      LlmClient llmClient,
      PalmSessionService palmSessionService,
      SessionRecordRepository sessionRecordRepository,
      PalmNarrativeWriter narrativeWriter,
      MetricsService metricsService,
      PalmRules rules) {
    this.llmClient = llmClient;
    this.palmSessionService = palmSessionService;
    this.sessionRecordRepository = sessionRecordRepository;
    this.narrativeWriter = narrativeWriter;
    this.metricsService = metricsService;
    this.rules = rules;
  }

  public CpAnalyzeResponse analyzeCp(CpAnalyzeRequest request) {
    String cpSessionId = "CP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    double score;
    String combo;
    if (request.featureA() != null && request.featureB() != null) {
      score = featureBasedScore(request.featureA(), request.featureB());
      combo = rules.cpCombo(shapeOf(request.featureA()), shapeOf(request.featureB()));
    } else {
      score = buildCpScore(request.userA().mbti(), request.userB().mbti());
      combo = rules.cpCombo(request.userA().handType(), request.userB().handType());
    }

    List<CpDimension> dimensions = new ArrayList<>();
    for (Map<String, Object> dimension : rules.cpDimensions()) {
      Object offset = dimension.get("offset");
      Object name = dimension.get("name");
      if (name == null || !(offset instanceof Number offsetNumber)) {
        continue;
      }
      dimensions.add(new CpDimension(
          String.valueOf(name),
          (int) clamp(score + offsetNumber.doubleValue())));
    }
    if (dimensions.isEmpty()) {
      dimensions = List.of(new CpDimension("感情线趋同度", (int) clamp(score + 3)));
    }

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
    sessionRecordRepository.save(new SessionRecordEntity(cpSessionId, "CP", combo, "双生螺旋", now));
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

  /** 特征亲和打分：掌型亲和分（规则表）+ 双方同名描线几何相近度加分（封顶）。 */
  private double featureBasedScore(PalmFeatureSet featureA, PalmFeatureSet featureB) {
    double affinity = rules.cpFeatureShapeAffinity(shapeOf(featureA), shapeOf(featureB));

    Map<String, FeatureLine> linesB = featureB.lines() == null ? Map.of() : featureB.lines().stream()
        .collect(Collectors.toMap(FeatureLine::lineName, Function.identity(), (a, b) -> a));
    double bonus = 0.0;
    double bonusMax = rules.cpFeatureLinesBonusMax();
    if (featureA.lines() != null) {
      for (FeatureLine lineA : featureA.lines()) {
        FeatureLine lineB = linesB.get(lineA.lineName());
        if (lineB == null) {
          continue;
        }
        if (lineA.length().equals(lineB.length())) {
          bonus += rules.cpFeatureSameLengthBonus();
        }
        if (lineA.curvature().equals(lineB.curvature())) {
          bonus += rules.cpFeatureSameCurvatureBonus();
        }
        if (lineA.continuity().equals(lineB.continuity())) {
          bonus += rules.cpFeatureSameContinuityBonus();
        }
        if (bonus >= bonusMax) {
          bonus = bonusMax;
          break;
        }
      }
    }
    return clamp(affinity + bonus);
  }

  private String shapeOf(PalmFeatureSet featureSet) {
    if (featureSet == null || featureSet.palmShape() == null || featureSet.palmShape().type() == null) {
      return "";
    }
    return featureSet.palmShape().type().replace("型手", "");
  }

  private double buildCpScore(String mbtiA, String mbtiB) {
    int hash = Math.abs((mbtiA + "-" + mbtiB).hashCode());
    double score = rules.cpScoreMin() + (hash % rules.cpScoreHashRange()) / 100.0;
    return clamp(score);
  }

  private double clamp(double value) {
    return Math.max(rules.cpScoreMin(), Math.min(rules.cpScoreMax(), value));
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
