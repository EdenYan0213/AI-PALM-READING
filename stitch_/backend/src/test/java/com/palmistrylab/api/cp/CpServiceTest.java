package com.palmistrylab.api.cp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.palmistrylab.api.cp.dto.CpAnalyzeRequest;
import com.palmistrylab.api.cp.dto.CpAnalyzeResponse;
import com.palmistrylab.api.cp.dto.CpUser;
import com.palmistrylab.api.llm.LlmClient;
import com.palmistrylab.api.metrics.AppEventRepository;
import com.palmistrylab.api.metrics.MetricsService;
import com.palmistrylab.api.palm.LoreService;
import com.palmistrylab.api.palm.PalmNarrativeWriter;
import com.palmistrylab.api.palm.PalmRules;
import com.palmistrylab.api.palm.PalmSessionService;
import com.palmistrylab.api.palm.SessionRecordRepository;
import com.palmistrylab.api.palm.dto.FeatureLine;
import com.palmistrylab.api.palm.dto.PalmFeatureSet;
import com.palmistrylab.api.palm.dto.PalmShapeFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CpServiceTest {

  @Mock
  private SessionRecordRepository sessionRecordRepository;

  @Mock
  private AppEventRepository appEventRepository;

  private CpService cpService;

  @BeforeEach
  void setUp() {
    LlmClient llmClient = new LlmClient(new RestTemplateBuilder(), new ObjectMapper(), false,
        "http://localhost:9", "", "test-model", "test-model", "test-model");
    PalmSessionService palmSessionService = new PalmSessionService(sessionRecordRepository);
    PalmNarrativeWriter narrativeWriter = new PalmNarrativeWriter(llmClient, new LoreService(), new ObjectMapper());
    MetricsService metricsService = new MetricsService(appEventRepository, sessionRecordRepository,
        org.mockito.Mockito.mock(com.palmistrylab.api.record.PalmRecordRepository.class));
    cpService = new CpService(llmClient, palmSessionService, sessionRecordRepository,
        narrativeWriter, metricsService, new PalmRules());
  }

  private CpAnalyzeRequest request() {
    return new CpAnalyzeRequest(
        new CpUser("你", "水型手", "INFJ"),
        new CpUser("TA", "火型手", "ENFP"),
        null, null);
  }

  @Test
  void analyzeCpWithoutFeaturesUsesDeterministicHashScore() {
    when(sessionRecordRepository.save(any(com.palmistrylab.api.palm.SessionRecordEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    CpAnalyzeResponse first = cpService.analyzeCp(request());
    CpAnalyzeResponse second = cpService.analyzeCp(request());

    assertThat(first.cpSessionId()).startsWith("CP-");
    // 哈希打分确定性：同输入同分
    assertThat(second.matchScore()).isEqualTo(first.matchScore());
    // 组合名来自规则表（水-火 → 救赎文学组）
    assertThat(first.comboName()).isEqualTo("救赎文学组");
    assertThat(first.dimensions()).hasSize(3);
    assertThat(first.matchScore()).isBetween(70.0, 98.0);
  }

  @Test
  void featureBasedScoreIsDeterministicAndRuleDriven() {
    PalmFeatureSet featureA = new PalmFeatureSet("1.1", "perception_sidecar", null,
        new com.palmistrylab.api.palm.dto.PalmShapeFeature("水型手", 0.9, null), null,
        List.of(new FeatureLine("生命线", "长", "大", "连续", false, "无")), List.of());
    PalmFeatureSet featureB = new PalmFeatureSet("1.1", "perception_sidecar", null,
        new com.palmistrylab.api.palm.dto.PalmShapeFeature("火型手", 0.9, null), null,
        List.of(new FeatureLine("生命线", "长", "大", "连续", false, "无")), List.of());

    when(sessionRecordRepository.save(any(com.palmistrylab.api.palm.SessionRecordEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    CpAnalyzeResponse response = cpService.analyzeCp(new CpAnalyzeRequest(
        new CpUser("你", "水型手", "INFJ"),
        new CpUser("TA", "火型手", "ENFP"),
        featureA, featureB));

    // 水火亲和分 88 + 同名线三项几何全同加分 2+1+1 = 92
    assertThat(response.matchScore()).isEqualTo(92.0);
    assertThat(response.comboName()).isEqualTo("救赎文学组");
  }

  @Test
  void featureBonusIsCappedByRules() {
    List<FeatureLine> threeIdenticalLines = List.of(
        new FeatureLine("生命线", "长", "大", "连续", false, "无"),
        new FeatureLine("智慧线", "长", "大", "连续", false, "无"),
        new FeatureLine("感情线", "长", "大", "连续", false, "无"));
    PalmFeatureSet featureA = new PalmFeatureSet("1.1", "heuristic_hash", null,
        new com.palmistrylab.api.palm.dto.PalmShapeFeature("风型手", null, null), null,
        threeIdenticalLines, List.of());
    PalmFeatureSet featureB = new PalmFeatureSet("1.1", "heuristic_hash", null,
        new com.palmistrylab.api.palm.dto.PalmShapeFeature("土型手", null, null), null,
        threeIdenticalLines, List.of());

    when(sessionRecordRepository.save(any(com.palmistrylab.api.palm.SessionRecordEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    CpAnalyzeResponse response = cpService.analyzeCp(new CpAnalyzeRequest(
        new CpUser("你", "风型手", "INTJ"),
        new CpUser("TA", "土型手", "ESTP"),
        featureA, featureB));

    // 风土亲和 90 + 加分封顶 8 = 98（恰好等于规则上限）
    assertThat(response.matchScore()).isEqualTo(98.0);
    assertThat(response.comboName()).isEqualTo("现实智囊组");
  }

  @Test
  void analyzeCpCreatesRestorableSession() {
    when(sessionRecordRepository.save(any(com.palmistrylab.api.palm.SessionRecordEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    CpAnalyzeResponse response = cpService.analyzeCp(request());

    var deep = cpService.unlockCpDeep(response.cpSessionId());
    assertThat(deep.unlocked()).isTrue();
    assertThat(deep.sections()).hasSize(2);
  }
}
