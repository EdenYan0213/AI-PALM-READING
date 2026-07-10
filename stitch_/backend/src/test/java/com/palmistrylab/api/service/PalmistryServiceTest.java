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
import java.util.Base64;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

  @BeforeEach
  void setUp() {
    llmClientService = new LlmClientService(
        new RestTemplateBuilder(),
        new ObjectMapper(),
        false,
        "http://localhost:9",
        "",
        "test-model",
        "test-model");
    palmistryService = new PalmistryService(
        sessionRecordRepository,
        appEventRepository,
        palmRecordRepository,
        monthlyReportRepository,
        llmClientService,
        new ObjectMapper());
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
    when(palmRecordRepository.findFirstByUserIdAndRecordDateLessThanOrderByRecordDateDescCreatedAtDesc(eq("user-1"), any(LocalDate.class)))
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
    when(palmRecordRepository.countByUserIdAndRecordDateBetween(eq("user-1"), any(LocalDate.class), any(LocalDate.class)))
        .thenReturn(4L);
    when(appEventRepository.save(any(AppEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

    ApiDtos.WeeklyRecordResponse response = palmistryService.submitWeeklyRecord(new ApiDtos.WeeklyRecordRequest(
        "user-1",
        "quick",
        "data:image/png;base64,abc",
        null,
        "2026-07-10"));

    assertThat(response.recordId()).isEqualTo("77");
    assertThat(response.userId()).isEqualTo("user-1");
    assertThat(response.recordMode()).isEqualTo("quick");
    assertThat(response.recordDate()).isEqualTo("2026-07-10");
    assertThat(response.monthlyUnlocked()).isTrue();
    assertThat(response.monthRecordCount()).isEqualTo(4);
    assertThat(response.compareHint()).contains("首次周记录");

    ArgumentCaptor<PalmRecordEntity> recordCaptor = ArgumentCaptor.forClass(PalmRecordEntity.class);
    verify(palmRecordRepository).save(recordCaptor.capture());
    assertThat(recordCaptor.getValue().getUserId()).isEqualTo("user-1");
    assertThat(recordCaptor.getValue().getRecordType()).isEqualTo("single");
    assertThat(recordCaptor.getValue().getRecordMode()).isEqualTo("quick");

    ArgumentCaptor<AppEventEntity> eventCaptor = ArgumentCaptor.forClass(AppEventEntity.class);
    verify(appEventRepository).save(eventCaptor.capture());
    assertThat(eventCaptor.getValue().getEventName()).isEqualTo("weekly_record");
    assertThat(eventCaptor.getValue().getSessionId()).isEqualTo("REC-77");
    assertThat(eventCaptor.getValue().getChannel()).isEqualTo("quick");
  }

  @Test
  void getRecordDetailReturnsLatestRecordForDate() {
    PalmRecordEntity record = new PalmRecordEntity(
        "user-2",
        "single",
        "standard",
        "image",
        "{}",
        8,
        6,
        5,
        "summary",
        "note",
        LocalDate.parse("2026-07-10"),
        Instant.parse("2026-07-10T10:15:30Z"));
    when(palmRecordRepository.findFirstByUserIdAndRecordDateOrderByCreatedAtDesc("user-2", LocalDate.parse("2026-07-10")))
        .thenReturn(Optional.of(record));

    ApiDtos.RecordDetailResponse response = palmistryService.getRecordDetail("user-2", "2026-07-10");

    assertThat(response.userId()).isEqualTo("user-2");
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
        new ObjectMapper());

    ApiDtos.PalmImageValidationResponse response = service.validatePalmImage("data:image/png;base64,abc");

    assertThat(response.accepted()).isTrue();
    assertThat(response.source()).isEqualTo("ai");
    assertThat(response.confidence()).isEqualTo(0.97);
    assertThat(response.reason()).contains("清晰手掌");
  }

  @Test
  void validatePalmImageFallsBackToHeuristicWhenAiUnavailable() throws Exception {
    String imageData = loadImageDataUri(Path.of("src/main/resources/static/light_1/screen.png"));

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

    StubVisionLlmClientService(String response) {
      super(new RestTemplateBuilder(), new ObjectMapper(), true, "http://localhost:9", "test-key", "test-model", "test-model");
      this.response = response;
    }

    @Override
    public boolean isAvailable() {
      return true;
    }

    @Override
    public String chat(String systemPrompt, String userPrompt, String imageData) {
      return response;
    }
  }
}