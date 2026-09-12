package com.palmistrylab.api.cp.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record CpUser(
    @NotBlank String nickname,
    @NotBlank String handType,
    @NotBlank String mbti) {
}
