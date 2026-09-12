package com.palmistrylab.api.metrics.dto;

public record MetricsSummaryResponse(
    long totalSingleAnalyze,
    long totalCpAnalyze,
    long totalWeeklyRecord,
    long totalShare,
    long totalAdUnlock,
    long totalRareMarkQuery,
    double adUnlockRate,
    double cpInitiateRate,
    double shareRate,
    double privateLeadRate) {
}
