package com.palmistrylab.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.palmistrylab.api.entity.AppEventEntity;
import com.palmistrylab.api.entity.PalmRecordEntity;
import com.palmistrylab.api.entity.SessionRecordEntity;
import com.palmistrylab.api.model.ApiDtos;
import com.palmistrylab.api.repository.AppEventRepository;
import com.palmistrylab.api.repository.MonthlyReportRepository;
import com.palmistrylab.api.repository.PalmRecordRepository;
import com.palmistrylab.api.repository.SessionRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PalmistryServiceTest {

  @Mock
  private SessionRecordRepository sessionRecordRepository;

  @Mock
  private AppEventRepository appEventRepository;

  @Mock
  private PalmRecordRepository palmRecordRepository;

  @Mock
  private MonthlyReportRepository monthlyReportRepository;

  private PalmistryService palmistryService;
  private LlmClientService llmClientService;
  private UserTokenService userTokenService;
  private PerceptionClient perceptionClient;

  @BeforeEach
  void setUp() {
    llmClientService = new LlmClientService(
        new RestTemplateBuilder(),
        new ObjectMapper(),
        false,
        "http://localhost:9",
        "",
        "test-model",
        "test-model",
        "test-model");
    userTokenService = new UserTokenService("test-secret");
    perceptionClient = new PerceptionClient(new RestTemplateBuilder(), new ObjectMapper(), "");
    palmistryService = new PalmistryService(
        sessionRecordRepository,
        appEventRepository,
        palmRecordRepository,
        monthlyReportRepository,
        llmClientService,
        new ObjectMapper(),
        userTokenService,
        perceptionClient,
        new LoreService());
  }

  @Test
  void analyzePalmCreatesSessionAndTracksEvent() {
    when(sessionRecordRepository.save(any(SessionRecordEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(appEventRepository.save(any(AppEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

    ApiDtos.PalmAnalyzeResponse response = palmistryService.analyzePalm(new ApiDtos.AnalyzePalmRequest(
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

    ArgumentCaptor<AppEventEntity> eventCaptor = ArgumentCaptor.forClass(AppEventEntity.class);
    verify(appEventRepository).save(eventCaptor.capture());
    assertThat(eventCaptor.getValue().getEventName()).isEqualTo("single_analyze");
    assertThat(eventCaptor.getValue().getSessionId()).isEqualTo(response.sessionId());
    assertThat(eventCaptor.getValue().getChannel()).isEqualTo("camera");
  }

  @Test
  void unlockDeepRequiresExistingSessionAndReturnsSections() {
    when(sessionRecordRepository.save(any(SessionRecordEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(appEventRepository.save(any(AppEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

    ApiDtos.PalmAnalyzeResponse response = palmistryService.analyzePalm(new ApiDtos.AnalyzePalmRequest(
        "camera",
        "right",
        null,
        null,
        null,
        "standard"));

    ApiDtos.UnlockDeepResponse deepResponse = palmistryService.unlockDeep(response.sessionId());

    assertThat(deepResponse.sessionId()).isEqualTo(response.sessionId());
    assertThat(deepResponse.unlocked()).isTrue();
    assertThat(deepResponse.unlockType()).isEqualTo("AD_REWARDED");
    assertThat(deepResponse.sections()).hasSize(3);

    ArgumentCaptor<AppEventEntity> eventCaptor = ArgumentCaptor.forClass(AppEventEntity.class);
    verify(appEventRepository, times(2)).save(eventCaptor.capture());
    assertThat(eventCaptor.getAllValues())
        .extracting(AppEventEntity::getEventName)
        .contains("single_analyze", "single_ad_unlock");
  }

  @Test
  void unlockDeepRestoresSessionFromRepositoryAfterRestart() {
    when(appEventRepository.save(any(AppEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(sessionRecordRepository.findById("PALM-RESTORED"))
        .thenReturn(Optional.of(new SessionRecordEntity(
            "PALM-RESTORED", "SINGLE", "水型手", "凤凰眼", Instant.now())));

    ApiDtos.UnlockDeepResponse deepResponse = palmistryService.unlockDeep("PALM-RESTORED");

    assertThat(deepResponse.sessionId()).isEqualTo("PALM-RESTORED");
    assertThat(deepResponse.unlocked()).isTrue();
    assertThat(deepResponse.sections()).hasSize(3);

    ApiDtos.RareMarkQueryResponse rareMarkResponse = palmistryService.queryRareMark("PALM-RESTORED");
    assertThat(rareMarkResponse.markName()).isEqualTo("凤凰眼");
  }

  @Test
  void unlockDeepRejectsUnknownSessionWhenNothingRestorable() {
    when(sessionRecordRepository.findById("PALM-GONE")).thenReturn(Optional.empty());

    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> palmistryService.unlockDeep("PALM-GONE"));
  }

  @Test
  void analyzePalmReturnsCachedResultForSameImage() {
    when(sessionRecordRepository.save(any(SessionRecordEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(appEventRepository.save(any(AppEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

    String imageData = "data:image/png;base64,cache-test-image";
    ApiDtos.PalmAnalyzeResponse first = palmistryService.analyzePalm(new ApiDtos.AnalyzePalmRequest(
        "camera", "left", null, imageData, null, "standard"));
    ApiDtos.PalmAnalyzeResponse second = palmistryService.analyzePalm(new ApiDtos.AnalyzePalmRequest(
        "camera", "left", null, imageData, null, "standard"));

    assertThat(second.sessionId()).isEqualTo(first.sessionId());
    assertThat(second.handType()).isEqualTo(first.handType());
    assertThat(second.freeOverview()).isEqualTo(first.freeOverview());

    ApiDtos.PalmAnalyzeResponse other = palmistryService.analyzePalm(new ApiDtos.AnalyzePalmRequest(
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
    PalmistryService service = new PalmistryService(
        sessionRecordRepository,
        appEventRepository,
        palmRecordRepository,
        monthlyReportRepository,
        new StubVisionLlmClientService(bannedJson),
        new ObjectMapper(),
        userTokenService,
        perceptionClient,
        new LoreService());
    when(sessionRecordRepository.save(any(SessionRecordEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(appEventRepository.save(any(AppEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

    ApiDtos.PalmAnalyzeResponse response = service.analyzePalm(new ApiDtos.AnalyzePalmRequest(
        "camera", "left", null, "data:image/png;base64,banned-test", null, "standard"));

    assertThat(response.llmUsed()).isFalse();
    assertThat(response.llmStatus()).isEqualTo("banned_content_filtered");
    assertThat(response.freeOverview().get(0).shortInterpretation()).doesNotContain("股票");
  }

  @Test
  void analyzePalmDeterministicHandTypeAcrossInstancesWithoutPerception() {
    when(sessionRecordRepository.save(any(SessionRecordEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(appEventRepository.save(any(AppEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

    String imageData = "data:image/png;base64,determinism-check";
    PalmistryService serviceA = new PalmistryService(
        sessionRecordRepository, appEventRepository, palmRecordRepository, monthlyReportRepository,
        llmClientService, new ObjectMapper(), userTokenService, perceptionClient, new LoreService());
    PalmistryService serviceB = new PalmistryService(
        sessionRecordRepository, appEventRepository, palmRecordRepository, monthlyReportRepository,
        llmClientService, new ObjectMapper(), userTokenService, perceptionClient, new LoreService());

    ApiDtos.PalmAnalyzeResponse fromA = serviceA.analyzePalm(new ApiDtos.AnalyzePalmRequest(
        "camera", "left", null, imageData, null, "standard"));
    ApiDtos.PalmAnalyzeResponse fromB = serviceB.analyzePalm(new ApiDtos.AnalyzePalmRequest(
        "camera", "left", null, imageData, null, "standard"));

    assertThat(fromB.handType()).isEqualTo(fromA.handType());
    assertThat(fromB.rareMark()).isEqualTo(fromA.rareMark());
    assertThat(fromB.personalityTags()).isEqualTo(fromA.personalityTags());
  }

  @Test
  void metricsSummaryAggregatesCountsAndRates() {
    when(sessionRecordRepository.countBySessionType("SINGLE")).thenReturn(2L);
    when(sessionRecordRepository.countBySessionType("CP")).thenReturn(1L);
    when(palmRecordRepository.countByRecordType("single")).thenReturn(4L);
    when(appEventRepository.countByEventName("share_card")).thenReturn(1L);
    when(appEventRepository.countByEventName("single_ad_unlock")).thenReturn(2L);
    when(appEventRepository.countByEventName("cp_ad_unlock")).thenReturn(1L);
    when(appEventRepository.countByEventName("rare_mark_query")).thenReturn(2L);

    ApiDtos.MetricsSummaryResponse response = palmistryService.metricsSummary();

    assertThat(response.totalSingleAnalyze()).isEqualTo(2L);
    assertThat(response.totalCpAnalyze()).isEqualTo(1L);
    assertThat(response.totalWeeklyRecord()).isEqualTo(4L);
    assertThat(response.totalShare()).isEqualTo(1L);
    assertThat(response.totalAdUnlock()).isEqualTo(3L);
    assertThat(response.totalRareMarkQuery()).isEqualTo(2L);
    assertThat(response.adUnlockRate()).isEqualTo(100.0);
    assertThat(response.cpInitiateRate()).isEqualTo(50.0);
    assertThat(response.shareRate()).isEqualTo(33.33);
    assertThat(response.privateLeadRate()).isEqualTo(100.0);
  }

  @Test
  void submitWeeklyRecordPersistsCurrentSnapshotAndUnlocksMonthlyThreshold() {
    String validUserId = userTokenService.issueUserId();
    when(palmRecordRepository.findFirstByUserIdAndRecordDateLessThanOrderByRecordDateDescCreatedAtDesc(eq(validUserId), any(LocalDate.class)))
        .thenReturn(Optional.empty());

    when(palmRecordRepository.save(any(PalmRecordEntity.class))).thenAnswer(invocation -> {
      PalmRecordEntity record = invocation.getArgument(0);
      return new PalmRecordEntity(
          record.getUserId(),
          record.getRecordType(),
          record.getRecordMode(),
          record.getPhotoUrl(),
          record.getTracesJson(),
          record.getEnergyLevel(),
          record.getWisdomActive(),
          record.getEmotionWave(),
          record.getAiSummary(),
          record.getUserNote(),
          record.getRecordDate(),
          record.getCreatedAt()) {
        @Override
        public Long getId() {
          return 77L;
        }
      };
    });
    when(palmRecordRepository.countByUserIdAndRecordDateBetween(eq(validUserId), any(LocalDate.class), any(LocalDate.class)))
        .thenReturn(4L);
    when(appEventRepository.save(any(AppEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

    ApiDtos.WeeklyRecordResponse response = palmistryService.submitWeeklyRecord(new ApiDtos.WeeklyRecordRequest(
        validUserId,
        "quick",
        "data:image/png;base64,abc",
        null,
        "2026-07-10"));

    assertThat(response.recordId()).isEqualTo("77");
    assertThat(response.userId()).isEqualTo(validUserId);
    assertThat(response.recordMode()).isEqualTo("quick");
    assertThat(response.recordDate()).isEqualTo("2026-07-10");
    assertThat(response.monthlyUnlocked()).isTrue();
    assertThat(response.monthRecordCount()).isEqualTo(4);
    assertThat(response.compareHint()).contains("首次周记录");

    ArgumentCaptor<PalmRecordEntity> recordCaptor = ArgumentCaptor.forClass(PalmRecordEntity.class);
    verify(palmRecordRepository).save(recordCaptor.capture());
    assertThat(recordCaptor.getValue().getUserId()).isEqualTo(validUserId);
    assertThat(recordCaptor.getValue().getRecordType()).isEqualTo("single");
    assertThat(recordCaptor.getValue().getRecordMode()).isEqualTo("quick");

    ArgumentCaptor<AppEventEntity> eventCaptor = ArgumentCaptor.forClass(AppEventEntity.class);
    verify(appEventRepository).save(eventCaptor.capture());
    assertThat(eventCaptor.getValue().getEventName()).isEqualTo("weekly_record");
    assertThat(eventCaptor.getValue().getSessionId()).isEqualTo("REC-77");
    assertThat(eventCaptor.getValue().getChannel()).isEqualTo("quick");
  }

  @Test
  void submitWeeklyRecordRejectsForgedUserId() {
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> palmistryService.submitWeeklyRecord(new ApiDtos.WeeklyRecordRequest(
            "user-1",
            "quick",
            "data:image/png;base64,abc",
            null,
            "2026-07-10")));
  }

  @Test
  void submitWeeklyRecordStoresPhotoOnFileSystem(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) {
    String validUserId = userTokenService.issueUserId();
    when(palmRecordRepository.findFirstByUserIdAndRecordDateLessThanOrderByRecordDateDescCreatedAtDesc(eq(validUserId), any(LocalDate.class)))
        .thenReturn(Optional.empty());
    when(palmRecordRepository.save(any(PalmRecordEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(palmRecordRepository.countByUserIdAndRecordDateBetween(eq(validUserId), any(LocalDate.class), any(LocalDate.class)))
        .thenReturn(1L);

    // 使用真实照片落盘的完整构造（照片不进 DB，photo_url 只存相对路径）
    PalmistryService serviceWithStorage = new PalmistryService(
        sessionRecordRepository,
        appEventRepository,
        palmRecordRepository,
        monthlyReportRepository,
        llmClientService,
        new ObjectMapper(),
        userTokenService,
        perceptionClient,
        new LoreService(),
        Runnable::run,
        new PhotoStorageService(tempDir.toString()));

    serviceWithStorage.submitWeeklyRecord(new ApiDtos.WeeklyRecordRequest(
        validUserId,
        "quick",
        "data:image/png;base64,iVBORw0KGgoAAAANSUhEUg==",
        null,
        "2026-07-10"));

    ArgumentCaptor<PalmRecordEntity> recordCaptor = ArgumentCaptor.forClass(PalmRecordEntity.class);
    verify(palmRecordRepository).save(recordCaptor.capture());
    String photoUrl = recordCaptor.getValue().getPhotoUrl();
    assertThat(photoUrl).isNotNull();
    assertThat(photoUrl).doesNotStartWith("data:");
    assertThat(java.nio.file.Files.exists(tempDir.resolve(photoUrl))).isTrue();
  }

  @Test
  void analyzePalmRejectsOversizedImage() {
    String oversized = "data:image/png;base64," + "A".repeat(12_000_001);
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> palmistryService.analyzePalm(new ApiDtos.AnalyzePalmRequest(
            "camera", "left", null, oversized, null, "standard")));
  }

  @Test
  void getRecordDetailReturnsLatestRecordForDate() {
    String validUserId = userTokenService.issueUserId();
    when(palmRecordRepository.findFirstByUserIdAndRecordDateOrderByCreatedAtDesc(validUserId, LocalDate.parse("2026-07-10")))
        .thenReturn(Optional.of(com.palmistrylab.api.repository.PalmRecordRepository.RecordSummary.of(
            42L,
            validUserId,
            LocalDate.parse("2026-07-10"),
            "standard",
            8,
            6,
            5,
            "summary",
            "note")));

    ApiDtos.RecordDetailResponse response = palmistryService.getRecordDetail(validUserId, "2026-07-10");

    assertThat(response.userId()).isEqualTo(validUserId);
    assertThat(response.recordId()).isEqualTo("42");
    assertThat(response.recordDate()).isEqualTo("2026-07-10");
    assertThat(response.runeColor()).isEqualTo("gold");
  }

  @Test
  void validatePalmImageUsesAiWhenAvailable() {
    PalmistryService service = new PalmistryService(
        sessionRecordRepository,
        appEventRepository,
        palmRecordRepository,
        monthlyReportRepository,
        new StubVisionLlmClientService("{\"accepted\":true,\"confidence\":0.97,\"reason\":\"清晰手掌\"}"),
        new ObjectMapper(),
        userTokenService,
        perceptionClient,
        new LoreService());

    ApiDtos.PalmImageValidationResponse response = service.validatePalmImage("data:image/png;base64,abc");

    assertThat(response.accepted()).isTrue();
    assertThat(response.source()).isEqualTo("ai");
    assertThat(response.confidence()).isEqualTo(0.97);
    assertThat(response.reason()).contains("清晰手掌");
  }

  @Test
  void validatePalmImageCachesResultPerImage() {
    StubVisionLlmClientService stub = new StubVisionLlmClientService(
        "{\"accepted\":true,\"confidence\":0.97,\"reason\":\"清晰手掌\"}");
    PalmistryService service = new PalmistryService(
        sessionRecordRepository,
        appEventRepository,
        palmRecordRepository,
        monthlyReportRepository,
        stub,
        new ObjectMapper(),
        userTokenService,
        perceptionClient,
        new LoreService());

    String imageData = "data:image/png;base64,cache-validation";
    service.validatePalmImage(imageData);
    ApiDtos.PalmImageValidationResponse second = service.validatePalmImage(imageData);

    assertThat(second.source()).isEqualTo("ai");
    assertThat(stub.chatCalls.get()).isEqualTo(1);
  }

  @Test
  void analyzePalmRejectsImageWhenLlmSaysNotPalm() {
    StubVisionLlmClientService stub = new StubVisionLlmClientService(
        "{\"imageAccepted\":false,\"rejectReason\":\"这张图片不是手相，请上传清晰的手掌照片。\"}");
    PalmistryService service = new PalmistryService(
        sessionRecordRepository,
        appEventRepository,
        palmRecordRepository,
        monthlyReportRepository,
        stub,
        new ObjectMapper(),
        userTokenService,
        perceptionClient,
        new LoreService());

    String imageData = "data:image/png;base64,not-a-palm";
    org.junit.jupiter.api.Assertions.assertThrows(
        ImageRejectedException.class,
        () -> service.analyzePalm(new ApiDtos.AnalyzePalmRequest("camera", "left", null, imageData, null, "standard")));

    // 拒绝结果被缓存：同图再次分析不再调用 LLM，也不产生会话
    int callsAfterFirst = stub.chatCalls.get();
    org.junit.jupiter.api.Assertions.assertThrows(
        ImageRejectedException.class,
        () -> service.analyzePalm(new ApiDtos.AnalyzePalmRequest("camera", "left", null, imageData, null, "standard")));
    assertThat(stub.chatCalls.get()).isEqualTo(callsAfterFirst);
    verify(sessionRecordRepository, never()).save(any(SessionRecordEntity.class));
  }

  @Test
  void analyzePalmEmitsProgressStagesToListener() {
    when(sessionRecordRepository.save(any(SessionRecordEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

    List<String> stages = new ArrayList<>();
    ApiDtos.AnalyzeProgressListener listener = new ApiDtos.AnalyzeProgressListener() {
      @Override
      public void onStage(String stage, int percent, String message) {
        stages.add(stage);
      }
    };

    palmistryService.analyzePalm(new ApiDtos.AnalyzePalmRequest(
        "camera", "left", null, "data:image/png;base64,stage-test", null, "standard"), listener);

    assertThat(stages).containsExactly("perception", "generate", "finalize");
  }

  @Test
  void validatePalmImageFallsBackToHeuristicWhenAiUnavailable() throws Exception {
    String imageData = loadImageDataUri(Path.of("src/test/resources/fixtures/not-a-palm.png"));

    ApiDtos.PalmImageValidationResponse response = palmistryService.validatePalmImage(imageData);

    assertThat(response.accepted()).isFalse();
    assertThat(response.source()).isEqualTo("heuristic");
  }

  private String loadImageDataUri(Path path) throws Exception {
    byte[] bytes = Files.readAllBytes(path);
    return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
  }

  private static class StubVisionLlmClientService extends LlmClientService {
    private final String response;
    private final AtomicInteger chatCalls = new AtomicInteger();

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