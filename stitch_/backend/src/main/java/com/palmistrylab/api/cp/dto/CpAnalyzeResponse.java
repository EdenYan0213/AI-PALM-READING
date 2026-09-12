package com.palmistrylab.api.cp.dto;

import java.util.List;

public record CpAnalyzeResponse(
    String cpSessionId,
    double matchScore,
    String comboName,
    List<CpDimension> dimensions,
    String interpretation,
    String tip) {
}
