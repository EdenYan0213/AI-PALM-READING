package com.palmistrylab.api.record;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.palmistrylab.api.identity.UserTokenService;
import com.palmistrylab.api.media.PhotoStorageService;
import com.palmistrylab.api.metrics.AppEventEntity;
import com.palmistrylab.api.metrics.MetricsService;
import com.palmistrylab.api.record.dto.RecordDetailResponse;
import com.palmistrylab.api.record.dto.WeeklyRecordRequest;
import com.palmistrylab.api.record.dto.WeeklyRecordResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PalmRecordServiceTest {

  @Mock
  private PalmRecordRepository palmRecordRepository;

  @Mock
  private com.palmistrylab.api.metrics.AppEventRepository appEventRepository;

  private UserTokenService userTokenService;
  private PalmRecordService palmRecordService;

  @BeforeEach
  void setUp() {
    userTokenService = new UserTokenService("test-secret");
    MetricsService metricsService = new MetricsService(appEventRepository,
        org.mockito.Mockito.mock(com.palmistrylab.api.palm.SessionRecordRepository.class), palmRecordRepository);
    palmRecordService = new PalmRecordService(
        palmRecordRepository,
        org.mockito.Mockito.mock(MonthlyReportRepository.class),
        userTokenService,
        metricsService,
        new ObjectMapper());
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

    WeeklyRecordResponse response = palmRecordService.submitWeeklyRecord(new WeeklyRecordRequest(
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
  void submitWeeklyRecordStoresPhotoOnFileSystem(@TempDir Path tempDir) {
    String validUserId = userTokenService.issueUserId();
    when(palmRecordRepository.findFirstByUserIdAndRecordDateLessThanOrderByRecordDateDescCreatedAtDesc(eq(validUserId), any(LocalDate.class)))
        .thenReturn(Optional.empty());
    when(palmRecordRepository.save(any(PalmRecordEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(palmRecordRepository.countByUserIdAndRecordDateBetween(eq(validUserId), any(LocalDate.class), any(LocalDate.class)))
        .thenReturn(1L);

    PhotoStorageService storage = new PhotoStorageService(tempDir.toString());
    PalmRecordService serviceWithStorage = new PalmRecordService(
        palmRecordRepository,
        org.mockito.Mockito.mock(MonthlyReportRepository.class),
        userTokenService,
        new MetricsService(appEventRepository,
            org.mockito.Mockito.mock(com.palmistrylab.api.palm.SessionRecordRepository.class), palmRecordRepository),
        storage,
        new ObjectMapper());

    serviceWithStorage.submitWeeklyRecord(new WeeklyRecordRequest(
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
    assertThat(Files.exists(tempDir.resolve(photoUrl))).isTrue();
  }

  @Test
  void submitWeeklyRecordRejectsForgedUserId() {
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> palmRecordService.submitWeeklyRecord(new WeeklyRecordRequest(
            "user-1",
            "quick",
            "data:image/png;base64,abc",
            null,
            "2026-07-10")));
  }

  @Test
  void getRecordDetailReturnsLatestRecordForDate() {
    String validUserId = userTokenService.issueUserId();
    when(palmRecordRepository.findFirstByUserIdAndRecordDateOrderByCreatedAtDesc(validUserId, LocalDate.parse("2026-07-10")))
        .thenReturn(Optional.of(PalmRecordRepository.RecordSummary.of(
            42L,
            validUserId,
            LocalDate.parse("2026-07-10"),
            "standard",
            8,
            6,
            5,
            "summary",
            "note")));

    RecordDetailResponse response = palmRecordService.getRecordDetail(validUserId, "2026-07-10");

    assertThat(response.userId()).isEqualTo(validUserId);
    assertThat(response.recordId()).isEqualTo("42");
    assertThat(response.recordDate()).isEqualTo("2026-07-10");
    assertThat(response.runeColor()).isEqualTo("gold");
  }
}
