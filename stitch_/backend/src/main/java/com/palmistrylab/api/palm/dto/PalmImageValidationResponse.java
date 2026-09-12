package com.palmistrylab.api.palm.dto;

public record PalmImageValidationResponse(
    boolean accepted,
    double confidence,
    String reason,
    String source) {
}
