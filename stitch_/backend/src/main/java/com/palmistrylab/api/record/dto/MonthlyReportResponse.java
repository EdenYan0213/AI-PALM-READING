package com.palmistrylab.api.record.dto;

import java.util.List;

public record MonthlyReportResponse(
    String userId,
    String yearMonth,
    int recordCount,
    String dominantEnergy,
    List<EnergyTrendPoint> trend,
    String reportText,
    boolean unlocked) {
}
