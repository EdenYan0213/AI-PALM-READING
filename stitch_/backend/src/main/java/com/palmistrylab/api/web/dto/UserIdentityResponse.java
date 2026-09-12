package com.palmistrylab.api.web.dto;

public record UserIdentityResponse(
    String userId,
    boolean restored) {
}
