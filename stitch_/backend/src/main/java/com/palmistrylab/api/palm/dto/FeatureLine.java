package com.palmistrylab.api.palm.dto;

/**
 * 描摹主线的几何特征（来自 TraceGeometryAnalyzer，仅包含用户实际描摹的线）。
 */
public record FeatureLine(
    String lineName,
    String length,
    String curvature,
    String continuity,
    boolean forked,
    String event) {
}
