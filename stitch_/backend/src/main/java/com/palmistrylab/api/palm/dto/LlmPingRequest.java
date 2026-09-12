package com.palmistrylab.api.palm.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record LlmPingRequest(@NotBlank String prompt) {
}
