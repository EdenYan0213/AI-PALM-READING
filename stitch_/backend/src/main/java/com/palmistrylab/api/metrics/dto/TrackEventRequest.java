package com.palmistrylab.api.metrics.dto;

import jakarta.validation.constraints.NotBlank;

public record TrackEventRequest(
    @NotBlank String eventName,
    String sessionId,
    String channel) {
}
