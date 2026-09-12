package com.palmistrylab.api.palm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * 确定性规则阈值（TD §4：阈值进 palmistry-rules.yml，不写死代码）。
 * 启动时加载 classpath 配置；文件缺失或键缺失时代码内默认值兜底，
 * 保证规则文件可只覆盖需要调整的键。
 */
@Component
public class PalmRules {

  private final Map<String, Object> root;

  public PalmRules() {
    this("classpath:palmistry-rules.yml");
  }

  public PalmRules(@Value("${app.rules.location:classpath:palmistry-rules.yml}") String location) {
    Map<String, Object> loaded = null;
    try {
      Resource resource = new DefaultResourceLoader().getResource(location);
      if (resource.exists()) {
        try (InputStream in = resource.getInputStream()) {
          Object parsed = new Yaml().load(in);
          if (parsed instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) map;
            loaded = typed;
          }
        }
      }
    } catch (Exception ignored) {
      // 规则文件损坏时退回代码内默认值
    }
    this.root = loaded == null ? Map.of() : loaded;
  }

  /** 测试便捷构造：直接用完整键值树。 */
  PalmRules(Map<String, Object> root) {
    this.root = root == null ? Map.of() : root;
  }

  public double num(String path, double defaultValue) {
    Object value = resolve(path);
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    if (value instanceof String text && !text.isBlank()) {
      try {
        return Double.parseDouble(text.trim());
      } catch (NumberFormatException ignored) {
        // fall through
      }
    }
    return defaultValue;
  }

  public int integer(String path, int defaultValue) {
    return (int) Math.round(num(path, defaultValue));
  }

  public String str(String path, String defaultValue) {
    Object value = resolve(path);
    return value == null ? defaultValue : String.valueOf(value);
  }

  @SuppressWarnings("unchecked")
  public List<Map<String, Object>> list(String path) {
    Object value = resolve(path);
    if (value instanceof List<?> items) {
      return (List<Map<String, Object>>) items;
    }
    return List.of();
  }

  private Object resolve(String path) {
    Object current = root;
    for (String key : path.split("\\.")) {
      if (!(current instanceof Map<?, ?> map)) {
        return null;
      }
      current = map.get(key);
    }
    return current;
  }

  // ---- trace 几何阈值 ----
  public double traceSampleMinDistance() {
    return num("trace.sample-min-distance", 3.0);
  }

  public double traceLongRatio() {
    return num("trace.length.long-ratio", 1.15);
  }

  public double traceMediumRatio() {
    return num("trace.length.medium-ratio", 0.75);
  }

  public double traceCurvatureLargeRatio() {
    return num("trace.curvature.large-ratio", 1.35);
  }

  public double traceCurvatureMediumRatio() {
    return num("trace.curvature.medium-ratio", 1.15);
  }

  public double traceJumpMinSegmentPx() {
    return num("trace.jumps.min-segment-px", 18.0);
  }

  public double traceJumpAvgMultiplier() {
    return num("trace.jumps.avg-multiplier", 3.2);
  }

  public int traceJumpBreakThreshold() {
    return integer("trace.jumps.break-threshold", 2);
  }

  public int traceForkMinPoints() {
    return integer("trace.fork.min-points", 8);
  }

  public double traceForkTailRatio() {
    return num("trace.fork.tail-ratio", 0.8);
  }

  public double traceForkSpreadRatio() {
    return num("trace.fork.spread-ratio", 0.12);
  }

  public double traceForkAngleBucketDegrees() {
    return num("trace.fork.angle-bucket-degrees", 25.0);
  }

  public int traceForkMinBuckets() {
    return integer("trace.fork.min-buckets", 2);
  }

  public int traceLoopMinPointsGap() {
    return integer("trace.events.loop-min-points-gap", 4);
  }

  public double traceLoopMaxDistance() {
    return num("trace.events.loop-max-distance", 6.0);
  }

  public double traceLoopPathRatio() {
    return num("trace.events.loop-path-ratio", 0.18);
  }

  public long tracePauseMs() {
    return integer("trace.events.pause-ms", 260);
  }

  public double tracePauseMaxDistance() {
    return num("trace.events.pause-max-distance", 2.0);
  }

  // ---- 图片校验阈值 ----
  public int imageSize() {
    return integer("image-validation.size", 64);
  }

  public double imageMinSkinRatio() {
    return num("image-validation.min-skin-ratio", 0.12);
  }

  public double imageDominantMinRatio() {
    return num("image-validation.dominant.min-ratio", 0.18);
  }

  public double imageDominantMaxRatio() {
    return num("image-validation.dominant.max-ratio", 0.82);
  }

  public double imageDominantMinBoxRatio() {
    return num("image-validation.dominant.min-box-ratio", 0.24);
  }

  public double imageLargeComponentRatio() {
    return num("image-validation.dominant.large-component-ratio", 0.04);
  }

  public int imageMaxLargeComponents() {
    return integer("image-validation.dominant.max-large-components", 2);
  }

  public double imageCenterStartRatio() {
    return num("image-validation.center.start-ratio", 0.25);
  }

  public double imageCenterEndRatio() {
    return num("image-validation.center.end-ratio", 0.75);
  }

  public double imageCenterMinSkinRatio() {
    return num("image-validation.center.min-skin-ratio", 0.16);
  }

  public double imageCenterMinX() {
    return num("image-validation.center.min-center-x", 0.22);
  }

  public double imageCenterMaxX() {
    return num("image-validation.center.max-center-x", 0.78);
  }

  public double imageCenterMinY() {
    return num("image-validation.center.min-center-y", 0.22);
  }

  public double imageCenterMaxY() {
    return num("image-validation.center.max-center-y", 0.80);
  }

  // ---- CP 规则 ----
  public double cpScoreMin() {
    return num("cp.score.min", 70.0);
  }

  public double cpScoreMax() {
    return num("cp.score.max", 98.0);
  }

  public int cpScoreHashRange() {
    return integer("cp.score.hash-range", 2900);
  }

  public List<Map<String, Object>> cpDimensions() {
    return list("cp.dimensions");
  }

  public String cpCombo(String handTypeA, String handTypeB) {
    String key = "cp.combos." + shortName(handTypeA) + "-" + shortName(handTypeB);
    return str(key, str("cp.combos.default", "理想主义组"));
  }

  public double cpFeatureShapeAffinity(String handTypeA, String handTypeB) {
    String key = "cp.feature-score." + shortName(handTypeA) + "-" + shortName(handTypeB);
    return num(key, num("cp.feature-score.default", 78.0));
  }

  private String shortName(String handType) {
    return handType == null ? "" : handType.replace("型手", "");
  }

  public double cpFeatureLinesBonusMax() {
    return num("cp.feature-score.lines-bonus-max", 8.0);
  }

  public double cpFeatureSameLengthBonus() {
    return num("cp.feature-score.line-bonus.same-length", 2.0);
  }

  public double cpFeatureSameCurvatureBonus() {
    return num("cp.feature-score.line-bonus.same-curvature", 1.0);
  }

  public double cpFeatureSameContinuityBonus() {
    return num("cp.feature-score.line-bonus.same-continuity", 1.0);
  }
}
