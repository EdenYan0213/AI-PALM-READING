package com.palmistrylab.api.palm;

import com.palmistrylab.api.palm.dto.PalmLineSummary;
import com.palmistrylab.api.palm.dto.PalmLineTraces;
import com.palmistrylab.api.palm.dto.TracePoint;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 掌纹描摹轨迹的确定性几何解析（TD §4 量化层）：长度/弯曲度/连续性/分叉/事件。
 * 阈值全部来自 palmistry-rules.yml（PalmRules），纯几何、无 IO；
 * 输出既喂给 LLM 提示词，也生成无模型时的兜底解读。
 */
@Component
public class TraceGeometryAnalyzer {

  private final PalmRules rules;

  public TraceGeometryAnalyzer(PalmRules rules) {
    this.rules = rules;
  }

  /** 从描摹轨迹提取三条主线的几何特征，并组装进提示词块。 */
  public TracePromptBundle buildTracePromptBundle(PalmLineTraces traces) {
    LineTraceFeature life = extractLineFeature("生命线", traces == null ? null : traces.lifeLine());
    LineTraceFeature wisdom = extractLineFeature("智慧线", traces == null ? null : traces.wisdomLine());
    LineTraceFeature love = extractLineFeature("感情线", traces == null ? null : traces.loveLine());

    List<LineTraceFeature> available = new ArrayList<>();
    if (life.available()) {
      available.add(life);
    }
    if (wisdom.available()) {
      available.add(wisdom);
    }
    if (love.available()) {
      available.add(love);
    }

    if (available.isEmpty()) {
      return new TracePromptBundle(false, "", "未进行掌纹手动确认。", List.of());
    }

    StringBuilder prompt = new StringBuilder();
    prompt.append("以下是用户亲手描摹出的手相特征，请严格基于这些描述进行解读，不要编造。\\n");
    for (LineTraceFeature feature : available) {
      prompt.append("- ")
          .append(feature.lineName())
          .append("：长度")
          .append(feature.lengthLabel())
          .append("，弯曲度")
          .append(feature.curvatureLabel())
          .append("，连续性")
          .append(feature.continuityLabel())
          .append("，末端分叉")
          .append(feature.forked() ? "是" : "否")
          .append("，事件")
          .append(feature.eventLabel())
          .append("。\\n");
    }

    String summary = "已进行掌纹确认：" + available.stream().map(LineTraceFeature::compactText).reduce((a, b) -> a + "；" + b)
        .orElse("已进行掌纹确认");
    return new TracePromptBundle(true, prompt.toString(), summary, available);
  }

  /** 无模型时的确定性兜底解读：直接按几何特征生成三条主线文案。 */
  public List<PalmLineSummary> buildOverviewFromFeatures(List<LineTraceFeature> features) {
    Map<String, LineTraceFeature> byName = new ConcurrentHashMap<>();
    for (LineTraceFeature feature : features) {
      byName.put(feature.lineName(), feature);
    }

    return List.of(
        toOverview(byName.get("感情线"), "感情线"),
        toOverview(byName.get("智慧线"), "智慧线"),
        toOverview(byName.get("生命线"), "生命线"));
  }

  private PalmLineSummary toOverview(LineTraceFeature feature, String defaultName) {
    if (feature == null || !feature.available()) {
      return new PalmLineSummary(defaultName, "未确认", "你未手动描摹该线，当前结果基于通用模型推断，仅供参考。");
    }

    String tags = "长度" + feature.lengthLabel() + " / 弯曲" + feature.curvatureLabel() + " / " + feature.continuityLabel();
    String text = feature.naturalText() + "建议你结合最近30天的实际状态做一次对照复盘。";
    return new PalmLineSummary(feature.lineName(), tags, text);
  }

  private LineTraceFeature extractLineFeature(String lineName, List<TracePoint> points) {
    if (points == null || points.size() < 3) {
      return LineTraceFeature.unavailable(lineName);
    }

    List<TracePoint> sampled = samplePoints(points, rules.traceSampleMinDistance());
    if (sampled.size() < 3) {
      return LineTraceFeature.unavailable(lineName);
    }

    double length = pathLength(sampled);
    double chord = distance(sampled.get(0), sampled.get(sampled.size() - 1));
    double baseDiagonal = estimateDiagonal(sampled);
    if (baseDiagonal < 1e-6) {
      return LineTraceFeature.unavailable(lineName);
    }

    double lengthRatio = length / baseDiagonal;
    String lengthLabel = classifyLength(lengthRatio);
    double curvatureRatio = chord < 1e-6 ? 1.0 : length / chord;
    String curvatureLabel = classifyCurvature(curvatureRatio);

    int jumpCount = countJumps(sampled);
    String continuity = jumpCount >= rules.traceJumpBreakThreshold() ? "断续" : "连续";
    boolean forked = detectFork(sampled, baseDiagonal);
    String eventLabel = detectEvents(sampled, baseDiagonal);
    String natural = buildNaturalSentence(lineName, lengthLabel, curvatureLabel, continuity, forked, eventLabel);

    return new LineTraceFeature(lineName, true, lengthLabel, curvatureLabel, continuity, forked, eventLabel, natural);
  }

  private List<TracePoint> samplePoints(List<TracePoint> points, double minDistance) {
    List<TracePoint> sampled = new ArrayList<>();
    TracePoint last = null;
    for (TracePoint p : points) {
      if (p == null) {
        continue;
      }
      if (last == null || distance(last, p) >= minDistance) {
        sampled.add(p);
        last = p;
      }
    }
    return sampled;
  }

  private double pathLength(List<TracePoint> points) {
    double sum = 0.0;
    for (int i = 1; i < points.size(); i++) {
      sum += distance(points.get(i - 1), points.get(i));
    }
    return sum;
  }

  private double estimateDiagonal(List<TracePoint> points) {
    double minX = Double.MAX_VALUE;
    double minY = Double.MAX_VALUE;
    double maxX = -Double.MAX_VALUE;
    double maxY = -Double.MAX_VALUE;
    for (TracePoint p : points) {
      minX = Math.min(minX, p.x());
      minY = Math.min(minY, p.y());
      maxX = Math.max(maxX, p.x());
      maxY = Math.max(maxY, p.y());
    }
    return Math.hypot(maxX - minX, maxY - minY);
  }

  private String classifyLength(double ratio) {
    if (ratio >= rules.traceLongRatio()) {
      return "长";
    }
    if (ratio >= rules.traceMediumRatio()) {
      return "中等";
    }
    return "短";
  }

  private String classifyCurvature(double ratio) {
    if (ratio >= rules.traceCurvatureLargeRatio()) {
      return "大";
    }
    if (ratio >= rules.traceCurvatureMediumRatio()) {
      return "中";
    }
    return "小";
  }

  private int countJumps(List<TracePoint> points) {
    if (points.size() < 3) {
      return 0;
    }
    double total = 0.0;
    for (int i = 1; i < points.size(); i++) {
      total += distance(points.get(i - 1), points.get(i));
    }
    double avg = total / (points.size() - 1);
    int jumps = 0;
    for (int i = 1; i < points.size(); i++) {
      double segment = distance(points.get(i - 1), points.get(i));
      if (segment > Math.max(rules.traceJumpMinSegmentPx(), avg * rules.traceJumpAvgMultiplier())) {
        jumps++;
      }
    }
    return jumps;
  }

  private boolean detectFork(List<TracePoint> points, double baseDiagonal) {
    int n = points.size();
    if (n < rules.traceForkMinPoints()) {
      return false;
    }
    int from = Math.max(0, (int) Math.floor(n * rules.traceForkTailRatio()));
    List<TracePoint> tail = points.subList(from, n);
    TracePoint end = points.get(n - 1);
    double maxSpread = 0.0;
    Set<Integer> angleBuckets = new HashSet<>();
    for (TracePoint p : tail) {
      maxSpread = Math.max(maxSpread, distance(end, p));
    }
    for (int i = 1; i < tail.size(); i++) {
      TracePoint a = tail.get(i - 1);
      TracePoint b = tail.get(i);
      double dx = b.x() - a.x();
      double dy = b.y() - a.y();
      if (Math.hypot(dx, dy) < 1e-6) {
        continue;
      }
      double angle = Math.toDegrees(Math.atan2(dy, dx));
      int bucket = (int) Math.round(angle / rules.traceForkAngleBucketDegrees());
      angleBuckets.add(bucket);
    }
    return maxSpread > baseDiagonal * rules.traceForkSpreadRatio() && angleBuckets.size() >= rules.traceForkMinBuckets();
  }

  private String detectEvents(List<TracePoint> points, double baseDiagonal) {
    boolean hasPause = false;
    for (int i = 1; i < points.size(); i++) {
      TracePoint prev = points.get(i - 1);
      TracePoint cur = points.get(i);
      if (prev.t() != null && cur.t() != null) {
        long dt = Math.abs(cur.t() - prev.t());
        if (dt >= rules.tracePauseMs() && distance(prev, cur) < rules.tracePauseMaxDistance()) {
          hasPause = true;
          break;
        }
      }
    }

    boolean hasLoop = false;
    for (int i = 0; i < points.size(); i++) {
      for (int j = i + rules.traceLoopMinPointsGap(); j < points.size(); j++) {
        if (distance(points.get(i), points.get(j)) < rules.traceLoopMaxDistance()) {
          double path = 0.0;
          for (int k = i + 1; k <= j; k++) {
            path += distance(points.get(k - 1), points.get(k));
          }
          if (path > baseDiagonal * rules.traceLoopPathRatio()) {
            hasLoop = true;
            break;
          }
        }
      }
      if (hasLoop) {
        break;
      }
    }

    if (hasLoop) {
      return "岛纹";
    }
    if (hasPause) {
      return "停顿";
    }
    return "无";
  }

  private String buildNaturalSentence(
      String lineName,
      String lengthLabel,
      String curvatureLabel,
      String continuity,
      boolean forked,
      String eventLabel) {
    StringBuilder sb = new StringBuilder();
    sb.append(lineName)
        .append("长度")
        .append(lengthLabel)
        .append("，弯曲度")
        .append(curvatureLabel)
        .append("，整体")
        .append(continuity)
        .append("。");
    if (forked) {
      sb.append("末端出现分叉，说明你在关键阶段有双路径倾向。");
    }
    if (!"无".equals(eventLabel)) {
      sb.append("轨迹中检测到").append(eventLabel).append("事件，代表该阶段存在明显停留或反复确认。");
    }
    return sb.toString();
  }

  private double distance(TracePoint a, TracePoint b) {
    return Math.hypot(a.x() - b.x(), a.y() - b.y());
  }

  public record TracePromptBundle(
      boolean confirmed,
      String promptBlock,
      String summaryText,
      List<LineTraceFeature> features) {
  }

  public record LineTraceFeature(
      String lineName,
      boolean available,
      String lengthLabel,
      String curvatureLabel,
      String continuityLabel,
      boolean forked,
      String eventLabel,
      String naturalText) {

    static LineTraceFeature unavailable(String lineName) {
      return new LineTraceFeature(lineName, false, "未知", "未知", "未确认", false, "无", "");
    }

    String compactText() {
      if (!available) {
        return lineName + "未确认";
      }
      return lineName + "长度" + lengthLabel + "、弯曲" + curvatureLabel + "、" + continuityLabel;
    }
  }
}
