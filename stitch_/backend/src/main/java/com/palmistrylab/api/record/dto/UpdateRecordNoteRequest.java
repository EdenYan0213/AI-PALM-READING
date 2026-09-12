package com.palmistrylab.api.record.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record UpdateRecordNoteRequest(
    @NotBlank String recordId,
    @NotBlank String userId,
    @NotEmpty String userNote) {
}
