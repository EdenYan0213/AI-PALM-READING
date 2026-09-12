package com.palmistrylab.api.palm.dto;

public record PalmFeatureSet(
    String version,
    String source,
    String imageHash,
    PalmShapeFeature palmShape,
    QualityFeature quality) {
}
