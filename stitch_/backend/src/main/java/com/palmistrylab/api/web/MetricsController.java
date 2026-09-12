package com.palmistrylab.api.web;

import com.palmistrylab.api.metrics.MetricsService;
import com.palmistrylab.api.metrics.dto.MetricsSummaryResponse;
import com.palmistrylab.api.metrics.dto.TrackEventRequest;
import com.palmistrylab.api.metrics.dto.TrackEventResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 埋点与运营指标。 */
@RestController
@RequestMapping("/api/v1")
public class MetricsController {

  private final MetricsService metricsService;

  public MetricsController(MetricsService metricsService) {
    this.metricsService = metricsService;
  }

  @PostMapping("/events/track")
  public ResponseEntity<TrackEventResponse> trackEvent(@Valid @RequestBody TrackEventRequest request) {
    return ResponseEntity.ok(metricsService.track(request));
  }

  @GetMapping("/metrics/summary")
  public ResponseEntity<MetricsSummaryResponse> metricsSummary() {
    return ResponseEntity.ok(metricsService.metricsSummary());
  }
}
