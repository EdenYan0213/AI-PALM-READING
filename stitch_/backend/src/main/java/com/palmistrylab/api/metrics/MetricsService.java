package com.palmistrylab.api.metrics;

import com.palmistrylab.api.metrics.dto.MetricsSummaryResponse;
import com.palmistrylab.api.metrics.dto.TrackEventRequest;
import com.palmistrylab.api.metrics.dto.TrackEventResponse;
import com.palmistrylab.api.palm.SessionRecordRepository;
import com.palmistrylab.api.record.PalmRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.Executor;

/**
 * 埋点与运营指标（北极星：广告解锁率/CP 发起率/分享率/私域转化率）。
 * 埋点异步落库，不阻塞分析主链路；失败静默丢弃。
 */
@Service
public class MetricsService {

  private final AppEventRepository appEventRepository;
  private final SessionRecordRepository sessionRecordRepository;
  private final PalmRecordRepository palmRecordRepository;
  private final Executor eventExecutor;

  public MetricsService(
      AppEventRepository appEventRepository,
      SessionRecordRepository sessionRecordRepository,
      PalmRecordRepository palmRecordRepository) {
    this(appEventRepository, sessionRecordRepository, palmRecordRepository, Runnable::run);
  }

  @Autowired
  public MetricsService(
      AppEventRepository appEventRepository,
      SessionRecordRepository sessionRecordRepository,
      PalmRecordRepository palmRecordRepository,
      @Qualifier("eventExecutor") Executor eventExecutor) {
    this.appEventRepository = appEventRepository;
    this.sessionRecordRepository = sessionRecordRepository;
    this.palmRecordRepository = palmRecordRepository;
    this.eventExecutor = eventExecutor;
  }

  public TrackEventResponse track(TrackEventRequest request) {
    recordEvent(request.eventName(), request.sessionId(), request.channel());
    return new TrackEventResponse(true, request.eventName());
  }

  /** 异步埋点：测试与默认构造使用同步执行器，生产行为一致。 */
  public void recordEvent(String eventName, String sessionId, String channel) {
    Instant at = Instant.now();
    eventExecutor.execute(() -> {
      try {
        appEventRepository.save(new AppEventEntity(eventName, sessionId, channel, at));
      } catch (Exception ignored) {
        // 埋点失败不影响主流程
      }
    });
  }

  public MetricsSummaryResponse metricsSummary() {
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

    return new MetricsSummaryResponse(
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

  private double round2(double value) {
    return Math.round(value * 10000.0) / 100.0;
  }
}
