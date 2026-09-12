package com.palmistrylab.api.palm;

import com.palmistrylab.api.palm.dto.PalmLineSummary;
import com.palmistrylab.api.palm.dto.PalmLineTraces;
import com.palmistrylab.api.palm.dto.TracePoint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TraceGeometryAnalyzerTest {

  private final TraceGeometryAnalyzer analyzer = new TraceGeometryAnalyzer(new PalmRules());

  private TracePoint pt(double x, double y) {
    return new TracePoint(x, y, null);
  }

  @Test
  void straightModerateLineClassifiesAsMediumContinuous() {
    List<TracePoint> points = new java.util.ArrayList<>();
    for (int i = 0; i <= 10; i++) {
      points.add(pt(i * 10, i)); // 近似直线：长度≈对角线 → 弯曲度“小”
    }

    TraceGeometryAnalyzer.TracePromptBundle bundle = analyzer.buildTracePromptBundle(
        new PalmLineTraces(points, null, null));

    assertThat(bundle.confirmed()).isTrue();
    assertThat(bundle.features()).hasSize(1);
    TraceGeometryAnalyzer.LineTraceFeature feature = bundle.features().get(0);
    assertThat(feature.lineName()).isEqualTo("生命线");
    assertThat(feature.lengthLabel()).isEqualTo("中等");
    assertThat(feature.curvatureLabel()).isEqualTo("小");
    assertThat(feature.continuityLabel()).isEqualTo("连续");
    assertThat(feature.forked()).isFalse();
    assertThat(feature.eventLabel()).isEqualTo("无");
    assertThat(bundle.summaryText()).contains("已进行掌纹确认");
  }

  @Test
  void loopBackPathDetectsIslandEvent() {
    List<TracePoint> points = new java.util.ArrayList<>();
    for (int i = 0; i <= 10; i++) {
      points.add(pt(i * 10, 0));
    }
    for (int i = 9; i >= 1; i--) {
      points.add(pt(i * 10 + 2, 1)); // 折返，构成闭环
    }

    TraceGeometryAnalyzer.TracePromptBundle bundle = analyzer.buildTracePromptBundle(
        new PalmLineTraces(points, null, null));

    assertThat(bundle.features().get(0).eventLabel()).isEqualTo("岛纹");
  }

  @Test
  void tooFewPointsMarksFeatureUnavailable() {
    TraceGeometryAnalyzer.TracePromptBundle bundle = analyzer.buildTracePromptBundle(
        new PalmLineTraces(List.of(pt(0, 0), pt(1, 1)), null, null));

    assertThat(bundle.confirmed()).isFalse();
    assertThat(bundle.promptBlock()).isBlank();
  }

  @Test
  void nullTracesProduceUnconfirmedBundle() {
    TraceGeometryAnalyzer.TracePromptBundle bundle = analyzer.buildTracePromptBundle(null);

    assertThat(bundle.confirmed()).isFalse();
    assertThat(bundle.summaryText()).isEqualTo("未进行掌纹手动确认。");
  }

  @Test
  void thresholdsComeFromRulesFile() {
    // 把“长”的阈值调低到 0.5：同一条直线从“中等”变“长”，证明阈值外置生效
    java.util.Map<String, Object> trace = java.util.Map.of(
        "length", java.util.Map.of("long-ratio", 0.5, "medium-ratio", 0.3));
    java.util.Map<String, Object> root = java.util.Map.of("trace", trace);
    TraceGeometryAnalyzer tuned = new TraceGeometryAnalyzer(new PalmRules(root));

    List<TracePoint> points = new java.util.ArrayList<>();
    for (int i = 0; i <= 10; i++) {
      points.add(pt(i * 10, i));
    }
    TraceGeometryAnalyzer.TracePromptBundle bundle = tuned.buildTracePromptBundle(
        new PalmLineTraces(points, null, null));

    assertThat(bundle.features().get(0).lengthLabel()).isEqualTo("长");
  }

  @Test
  void buildOverviewFromFeaturesFillsUntracedLinesWithFallbackText() {
    List<TracePoint> points = new java.util.ArrayList<>();
    for (int i = 0; i <= 10; i++) {
      points.add(pt(i * 10, i));
    }
    TraceGeometryAnalyzer.LineTraceFeature life =
        analyzer.buildTracePromptBundle(new PalmLineTraces(points, null, null)).features().get(0);

    List<PalmLineSummary> overview = analyzer.buildOverviewFromFeatures(List.of(life));

    assertThat(overview).hasSize(3);
    assertThat(overview.get(0).lineName()).isEqualTo("感情线");
    assertThat(overview.get(0).tags()).isEqualTo("未确认");
    assertThat(overview.get(2).shortInterpretation()).contains("生命线长度");
  }
}
