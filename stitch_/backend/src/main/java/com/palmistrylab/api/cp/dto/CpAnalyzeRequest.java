package com.palmistrylab.api.cp.dto;

import com.palmistrylab.api.palm.dto.PalmFeatureSet;
import jakarta.validation.constraints.NotNull;

public record CpAnalyzeRequest(
    @NotNull CpUser userA,
    @NotNull CpUser userB,
    /** 可选：双方各自分析产出的 PalmFeatureSet，双方都提供时走确定性特征亲和打分。 */
    PalmFeatureSet featureA,
    PalmFeatureSet featureB) {
}
