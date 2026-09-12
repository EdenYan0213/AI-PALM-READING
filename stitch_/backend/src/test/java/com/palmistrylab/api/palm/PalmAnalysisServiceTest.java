package com.palmistrylab.api.palm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.palmistrylab.api.llm.LlmClient;
import com.palmistrylab.api.metrics.MetricsService;
import com.palmistrylab.api.palm.dto.AnalyzePalmRequest;
import com.palmistrylab.api.palm.dto.AnalyzeProgressListener;
import com.palmistrylab.api.palm.dto.PalmAnalyzeResponse;
import com.palmistrylab.api.perception.PerceptionClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PalmAnalysisServiceTest {

  @Mock
  private SessionRecordRepository sessionRecordRepository;

  @Mock
  private com.palmistrylab.api.metrics.AppEventRepository appEventRepository;

  @Mock
  private com.palmistrylab.api.record.PalmRecordRepository palmRecordRepository;

  private PalmAnalysisService palmAnalysisService;
  private LlmClient llmClient;
  private StubVisionLlmClientService stubLlmClient;

  @BeforeEach
  void setUp() {
    llmClient = new LlmClient(
        new RestTemplateBuilder(),
        new ObjectMapper(),
        false,
        "http://localhost:9",
        "",
        "test-model",
        "test-model",
        "test-model");
    stubLlmClient = null;
    rebuildService(llmClient);
  }

  private void rebuildService(LlmClient client) {
    PalmSessionService palmSessionService = new PalmSessionService(sessionRecordRepository);
    PalmNarrativeWriter narrativeWriter = new PalmNarrativeWriter(client, new LoreService(), new ObjectMapper());
    MetricsService metricsService = new MetricsService(appEventRepository, sessionRecordRepository, palmRecordRepository);
    PerceptionClient perceptionClient = new PerceptionClient(new RestTemplateBuilder(), new ObjectMapper(), "");
    palmAnalysisService = new PalmAnalysisService(
        sessionRecordRepository, palmSessionService, narrativeWriter, client, perceptionClient, metricsService);
  }

  @Test
  void analyzePalmCreatesSessionAndTracksEvent() {
    when(sessionRecordRepository.save(any(SessionRecordEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(appEventRepository.save(any(com.palmistrylab.api.metrics.AppEventEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    PalmAnalyzeResponse response = palmAnalysisService.analyzePalm(new AnalyzePalmRequest(
        "camera",
        "left",
        "female",
        "data:image/png;base64,abc",
        null,
        "standard"));

    assertThat(response.sessionId()).startsWith("PALM-");
    assertThat(response.personalityTags()).hasSize(3);
    assertThat(response.freeOverview()).hasSize(3);
    assertThat(response.llmUsed()).isFalse();
    assertThat(response.llmStatus()).isEqualTo("llm_disabled");
    assertThat(response.featureSet()).isNotNull();
    assertThat(response.featureSet().source()).isEqualTo("heuristic_hash");
    assertThat(response.featureSet().palmShape().type()).isEqualTo(response.handType());

    ArgumentCaptor<SessionRecordEntity> sessionCaptor = ArgumentCaptor.forClass(SessionRecordEntity.class);
    verify(sessionRecordRepository).save(sessionCaptor.capture());
    assertThat(sessionCaptor.getValue().getSessionId()).isEqualTo(response.sessionId());
    assertThat(sessionCaptor.getValue().getSessionType()).isEqualTo("SINGLE");

    ArgumentCaptor<com.palmistrylab.api.metrics.AppEventEntity> eventCaptor =
        ArgumentCaptor.forClass(com.palmistrylab.api.metrics.AppEventEntity.class);
    verify(appEventRepository).save(eventCaptor.capture());
    assertThat(eventCaptor.getValue().getEventName()).isEqualTo("single_analyze");
    assertThat(eventCaptor.getValue().getSessionId()).isEqualTo(response.sessionId());
    assertThat(eventCaptor.getValue().getChannel()).isEqualTo("camera");
  }

  @Test
  void unlockDeepRequiresExistingSessionAndReturnsSections() {
    when(sessionRecordRepository.save(any(SessionRecordEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(appEventRepository.save(any(com.palmistrylab.api.metrics.AppEventEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    PalmAnalyzeResponse response = palmAnalysisService.analyzePalm(new AnalyzePalmRequest(
        "camera", "right", null, null, null, "standard"));

    var deepResponse = palmAnalysisService.unlockDeep(response.sessionId());

    assertThat(deepResponse.sessionId()).isEqualTo(response.sessionId());
    assertThat(deepResponse.unlocked()).isTrue();
    assertThat(deepResponse.unlockType()).isEqualTo("AD_REWARDED");
    assertThat(deepResponse.sections()).hasSize(3);

    ArgumentCaptor<com.palmistrylab.api.metrics.AppEventEntity> eventCaptor =
        ArgumentCaptor.forClass(com.palmistrylab.api.metrics.AppEventEntity.class);
    verify(appEventRepository, org.mockito.Mockito.times(2)).save(eventCaptor.capture());
    assertThat(eventCaptor.getAllValues())
        .extracting(com.palmistrylab.api.metrics.AppEventEntity::getEventName)
        .contains("single_analyze", "single_ad_unlock");
  }

  @Test
  void unlockDeepRestoresSessionFromRepositoryAfterRestart() {
    when(appEventRepository.save(any(com.palmistrylab.api.metrics.AppEventEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(sessionRecordRepository.findById("PALM-RESTORED"))
        .thenReturn(Optional.of(new SessionRecordEntity(
            "PALM-RESTORED", "SINGLE", "水型手", "凤凰眼", Instant.now())));

    var deepResponse = palmAnalysisService.unlockDeep("PALM-RESTORED");

    assertThat(deepResponse.sessionId()).isEqualTo("PALM-RESTORED");
    assertThat(deepResponse.unlocked()).isTrue();
    assertThat(deepResponse.sections()).hasSize(3);

    var rareMarkResponse = palmAnalysisService.queryRareMark("PALM-RESTORED");
    assertThat(rareMarkResponse.markName()).isEqualTo("凤凰眼");
  }

  @Test
  void unlockDeepRejectsUnknownSessionWhenNothingRestorable() {
    when(sessionRecordRepository.findById("PALM-GONE")).thenReturn(Optional.empty());

    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> palmAnalysisService.unlockDeep("PALM-GONE"));
  }

  @Test
  void analyzePalmReturnsCachedResultForSameImage() {
    when(sessionRecordRepository.save(any(SessionRecordEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(appEventRepository.save(any(com.palmistrylab.api.metrics.AppEventEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    String imageData = "data:image/png;base64,cache-test-image";
    PalmAnalyzeResponse first = palmAnalysisService.analyzePalm(new AnalyzePalmRequest(
        "camera", "left", null, imageData, null, "standard"));
    PalmAnalyzeResponse second = palmAnalysisService.analyzePalm(new AnalyzePalmRequest(
        "camera", "left", null, imageData, null, "standard"));

    assertThat(second.sessionId()).isEqualTo(first.sessionId());
    assertThat(second.handType()).isEqualTo(first.handType());
    assertThat(second.freeOverview()).isEqualTo(first.freeOverview());

    PalmAnalyzeResponse other = palmAnalysisService.analyzePalm(new AnalyzePalmRequest(
        "camera", "left", null, "data:image/png;base64,different-image", null, "standard"));
    assertThat(other.sessionId()).isNotEqualTo(first.sessionId());
  }

  @Test
  void analyzePalmFiltersBannedLlmContent() {
    String bannedJson = "{\"personalityTags\":[\"A\",\"B\",\"C\"],"
        + "\"freeOverview\":["
        + "{\"lineName\":\"感情线\",\"tags\":\"t\",\"shortInterpretation\":\"你近期适合加仓买入股票，稳赚翻倍。\"},"
        + "{\"lineName\":\"智慧线\",\"tags\":\"t\",\"shortInterpretation\":\"这是完全正常的内容。\"},"
        + "{\"lineName\":\"生命线\",\"tags\":\"t\",\"shortInterpretation\":\"这也是正常内容。\"}],"
        + "\"teaser\":\"正常提示\"}";
    stubLlmClient = new StubVisionLlmClientService(bannedJson);
    rebuildService(stubLlmClient);
    when(sessionRecordRepository.save(any(SessionRecordEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(appEventRepository.save(any(com.palmistrylab.api.metrics.AppEventEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    PalmAnalyzeResponse response = palmAnalysisService.analyzePalm(new AnalyzePalmRequest(
        "camera", "left", null, "data:image/png;base64,banned-test", null, "standard"));

    assertThat(response.llmUsed()).isFalse();
    assertThat(response.llmStatus()).isEqualTo("banned_content_filtered");
    assertThat(response.freeOverview().get(0).shortInterpretation()).doesNotContain("股票");
  }

  @Test
  void analyzePalmDeterministicHandTypeAcrossInstancesWithoutPerception() {
    when(sessionRecordRepository.save(any(SessionRecordEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(appEventRepository.save(any(com.palmistrylab.api.metrics.AppEventEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    String imageData = "data:image/png;base64,determinism-check";
    PalmAnalysisService serviceA = new PalmAnalysisService(
        sessionRecordRepository,
        new PalmSessionService(sessionRecordRepository),
        new PalmNarrativeWriter(llmClient, new LoreService(), new ObjectMapper()),
        llmClient,
        new PerceptionClient(new RestTemplateBuilder(), new ObjectMapper(), ""),
        new MetricsService(appEventRepository, sessionRecordRepository, palmRecordRepository));
    PalmAnalysisService serviceB = new PalmAnalysisService(
        sessionRecordRepository,
        new PalmSessionService(sessionRecordRepository),
        new PalmNarrativeWriter(llmClient, new LoreService(), new ObjectMapper()),
        llmClient,
        new PerceptionClient(new RestTemplateBuilder(), new ObjectMapper(), ""),
        new MetricsService(appEventRepository, sessionRecordRepository, palmRecordRepository));

    PalmAnalyzeResponse fromA = serviceA.analyzePalm(new AnalyzePalmRequest(
        "camera", "left", null, imageData, null, "standard"));
    PalmAnalyzeResponse fromB = serviceB.analyzePalm(new AnalyzePalmRequest(
        "camera", "left", null, imageData, null, "standard"));

    assertThat(fromB.handType()).isEqualTo(fromA.handType());
    assertThat(fromB.rareMark()).isEqualTo(fromA.rareMark());
    assertThat(fromB.personalityTags()).isEqualTo(fromA.personalityTags());
  }

  @Test
  void analyzePalmRejectsImageWhenLlmSaysNotPalm() {
    StubVisionLlmClientService rejectStub = new StubVisionLlmClientService(
        "{\"imageAccepted\":false,\"rejectReason\":\"这张图片不是手相，请上传清晰的手掌照片。\"}");
    rebuildService(rejectStub);

    String imageData = "data:image/png;base64,not-a-palm";
    org.junit.jupiter.api.Assertions.assertThrows(
        ImageRejectedException.class,
        () -> palmAnalysisService.analyzePalm(new AnalyzePalmRequest("camera", "left", null, imageData, null, "standard")));

    // 拒绝结果被缓存：同图再次分析不再调用 LLM，也不产生会话
    int callsAfterFirst = rejectStub.chatCalls.get();
    org.junit.jupiter.api.Assertions.assertThrows(
        ImageRejectedException.class,
        () -> palmAnalysisService.analyzePalm(new AnalyzePalmRequest("camera", "left", null, imageData, null, "standard")));
    assertThat(rejectStub.chatCalls.get()).isEqualTo(callsAfterFirst);
    verify(sessionRecordRepository, never()).save(any(SessionRecordEntity.class));
  }

  @Test
  void analyzePalmEmitsProgressStagesToListener() {
    when(sessionRecordRepository.save(any(SessionRecordEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

    List<String> stages = new ArrayList<>();
    AnalyzeProgressListener listener = new AnalyzeProgressListener() {
      @Override
      public void onStage(String stage, int percent, String message) {
        stages.add(stage);
      }
    };

    palmAnalysisService.analyzePalm(new AnalyzePalmRequest(
        "camera", "left", null, "data:image/png;base64,stage-test", null, "standard"), listener);

    assertThat(stages).containsExactly("perception", "generate", "finalize");
  }

  @Test
  void analyzePalmRejectsOversizedImage() {
    String oversized = "data:image/png;base64," + "A".repeat(12_000_001);
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> palmAnalysisService.analyzePalm(new AnalyzePalmRequest(
            "camera", "left", null, oversized, null, "standard")));
  }

  @Test
  void validatePalmImageUsesAiWhenAvailable() {
    StubVisionLlmClientService acceptStub = new StubVisionLlmClientService(
        "{\"accepted\":true,\"confidence\":0.97,\"reason\":\"清晰手掌\"}");
    rebuildService(acceptStub);

    var response = palmAnalysisService.validatePalmImage("data:image/png;base64,abc");

    assertThat(response.accepted()).isTrue();
    assertThat(response.source()).isEqualTo("ai");
    assertThat(response.confidence()).isEqualTo(0.97);
    assertThat(response.reason()).contains("清晰手掌");
  }

  @Test
  void validatePalmImageCachesResultPerImage() {
    StubVisionLlmClientService acceptStub = new StubVisionLlmClientService(
        "{\"accepted\":true,\"confidence\":0.97,\"reason\":\"清晰手掌\"}");
    rebuildService(acceptStub);

    String imageData = "data:image/png;base64,cache-validation";
    palmAnalysisService.validatePalmImage(imageData);
    var second = palmAnalysisService.validatePalmImage(imageData);

    assertThat(second.source()).isEqualTo("ai");
    assertThat(acceptStub.chatCalls.get()).isEqualTo(1);
  }

  @Test
  void validatePalmImageFallsBackToHeuristicWhenAiUnavailable() throws Exception {
    String imageData = loadImageDataUri(java.nio.file.Path.of("src/test/resources/fixtures/not-a-palm.png"));

    var response = palmAnalysisService.validatePalmImage(imageData);

    assertThat(response.accepted()).isFalse();
    assertThat(response.source()).isEqualTo("heuristic");
  }

  private String loadImageDataUri(java.nio.file.Path path) throws Exception {
    byte[] bytes = java.nio.file.Files.readAllBytes(path);
    return "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(bytes);
  }

  private static class StubVisionLlmClientService extends LlmClient {
    private final String response;
    private final java.util.concurrent.atomic.AtomicInteger chatCalls = new java.util.concurrent.atomic.AtomicInteger();

    StubVisionLlmClientService(String response) {
      super(new RestTemplateBuilder(), new ObjectMapper(), true, "http://localhost:9", "test-key", "test-model", "test-model", "test-model");
      this.response = response;
    }

    @Override
    public boolean isAvailable() {
      return true;
    }

    @Override
    public String chat(String systemPrompt, String userPrompt, String imageData) {
      chatCalls.incrementAndGet();
      return response;
    }

    @Override
    public String chatForValidation(String systemPrompt, String userPrompt, String imageData) {
      chatCalls.incrementAndGet();
      return response;
    }
  }
}
