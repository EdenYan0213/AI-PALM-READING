package com.palmistrylab.api.record.dto;

import java.util.List;

public record CalendarResponse(
    String userId,
    String yearMonth,
    int monthRecordCount,
    int monthTarget,
    List<CalendarDayRecord> records,
    List<EnergyTrendPoint> trend) {
}
