package com.palmistrylab.api.metrics;

import com.palmistrylab.api.metrics.dto.MetricsSummaryResponse;
import com.palmistrylab.api.palm.SessionRecordRepository;
import com.palmistrylab.api.record.PalmRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricsServiceTest {

  @Mock
  private AppEventRepository appEventRepository;

  @Mock
  private SessionRecordRepository sessionRecordRepository;

  @Mock
  private PalmRecordRepository palmRecordRepository;

  @Test
  void metricsSummaryAggregatesCountsAndRates() {
    when(sessionRecordRepository.countBySessionType("SINGLE")).thenReturn(2L);
    when(sessionRecordRepository.countBySessionType("CP")).thenReturn(1L);
    when(palmRecordRepository.countByRecordType("single")).thenReturn(4L);
    when(appEventRepository.countByEventName("share_card")).thenReturn(1L);
    when(appEventRepository.countByEventName("single_ad_unlock")).thenReturn(2L);
    when(appEventRepository.countByEventName("cp_ad_unlock")).thenReturn(1L);
    when(appEventRepository.countByEventName("rare_mark_query")).thenReturn(2L);

    MetricsService metricsService = new MetricsService(appEventRepository, sessionRecordRepository, palmRecordRepository);
    MetricsSummaryResponse response = metricsService.metricsSummary();

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
  void trackRecordsEventAndEchoesName() {
    when(appEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    MetricsService metricsService = new MetricsService(appEventRepository, sessionRecordRepository, palmRecordRepository);
    var response = metricsService.track(
        new com.palmistrylab.api.metrics.dto.TrackEventRequest("share_card", "PALM-1", "report_share"));

    assertThat(response.accepted()).isTrue();
    assertThat(response.eventName()).isEqualTo("share_card");
    org.mockito.Mockito.verify(appEventRepository).save(org.mockito.ArgumentMatchers.argThat(
        (AppEventEntity entity) -> "share_card".equals(entity.getEventName()) && "PALM-1".equals(entity.getSessionId())));
  }
}
