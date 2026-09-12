package com.palmistrylab.api.record.dto;

public record CalendarDayRecord(
    String date,
    String runeColor,
    int energyLevel,
    int wisdomActive,
    int emotionWave,
    String mode,
    String summary) {
}
