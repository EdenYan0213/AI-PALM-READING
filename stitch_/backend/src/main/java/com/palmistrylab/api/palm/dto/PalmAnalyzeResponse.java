package com.palmistrylab.api.palm.dto;

import java.util.List;

public record PalmAnalyzeResponse(
    String sessionId,
    String handType,
    List<String> personalityTags,
    List<PalmLineSummary> freeOverview,
    String rareMark,
    String teaser,
    String slogan,
    boolean traceConfirmed,
    String traceFeatureSummary,
    boolean llmUsed,
    String llmStatus,
    PalmFeatureSet featureSet) {
}
