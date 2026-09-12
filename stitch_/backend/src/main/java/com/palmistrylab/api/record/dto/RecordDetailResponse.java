package com.palmistrylab.api.record.dto;

public record RecordDetailResponse(
    String recordId,
    String userId,
    String recordDate,
    String recordMode,
    int energyLevel,
    int wisdomActive,
    int emotionWave,
    String aiSummary,
    String userNote,
    String runeColor) {
}
