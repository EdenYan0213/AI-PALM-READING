package com.palmistrylab.api.record.dto;

public record WeeklyRecordResponse(
    String recordId,
    String userId,
    String recordMode,
    String recordDate,
    int energyLevel,
    int wisdomActive,
    int emotionWave,
    String aiSummary,
    String compareHint,
    String runeColor,
    boolean monthlyUnlocked,
    int monthRecordCount,
    int monthTarget) {
}
