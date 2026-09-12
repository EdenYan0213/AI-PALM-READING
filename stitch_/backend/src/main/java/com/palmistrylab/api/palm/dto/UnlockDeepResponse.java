package com.palmistrylab.api.palm.dto;

import java.util.List;

public record UnlockDeepResponse(
    String sessionId,
    boolean unlocked,
    String unlockType,
    List<DeepSection> sections) {
}
