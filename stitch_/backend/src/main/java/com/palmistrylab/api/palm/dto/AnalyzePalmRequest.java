package com.palmistrylab.api.palm.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record AnalyzePalmRequest(
    @NotBlank String source,
    @NotBlank String handSide,
    String gender,
    String imageData,
    PalmLineTraces traces,
    String recordMode) {
}
