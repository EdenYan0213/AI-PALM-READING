package com.palmistrylab.api.palm.dto;

public record RareMarkQueryResponse(
    String sessionId,
    String markName,
    String wechatId,
    int remainQuota,
    String hint) {
}
