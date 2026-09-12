package com.palmistrylab.api.palm.dto;

import java.util.List;

/**
 * 量化层契约（TD §4）v1.1：确定性特征集合，前端/CP 引擎唯一事实源。
 * v1.1 补全 lines（描摹主线的几何特征）与 marks（印记及其判定来源）。
 */
public record PalmFeatureSet(
    String version,
    String source,
    String imageHash,
    PalmShapeFeature palmShape,
    QualityFeature quality,
    List<FeatureLine> lines,
    List<FeatureMark> marks) {
}
