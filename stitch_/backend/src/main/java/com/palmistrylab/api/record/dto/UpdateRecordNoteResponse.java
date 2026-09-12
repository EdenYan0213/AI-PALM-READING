package com.palmistrylab.api.record.dto;

public record UpdateRecordNoteResponse(
    boolean updated,
    String recordId,
    String userNote) {
}
