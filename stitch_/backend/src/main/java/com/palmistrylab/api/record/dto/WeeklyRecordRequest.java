package com.palmistrylab.api.record.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import com.palmistrylab.api.palm.dto.PalmLineTraces;

public record WeeklyRecordRequest(
    @NotBlank String userId,
    @NotBlank String recordMode,
    @NotBlank String imageData,
    PalmLineTraces traces,
    String recordDate) {
}
