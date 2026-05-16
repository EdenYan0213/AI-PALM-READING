package com.palmistrylab.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "session_record")
public class SessionRecordEntity {

    @Id
    @Column(name = "session_id", nullable = false, length = 32)
    private String sessionId;

    @Column(name = "session_type", nullable = false, length = 16)
    private String sessionType;

    @Column(name = "subject", nullable = false, length = 64)
    private String subject;

    @Column(name = "rare_mark", length = 64)
    private String rareMark;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SessionRecordEntity() {
    }

    public SessionRecordEntity(String sessionId, String sessionType, String subject, String rareMark, Instant createdAt) {
        this.sessionId = sessionId;
        this.sessionType = sessionType;
        this.subject = subject;
        this.rareMark = rareMark;
        this.createdAt = createdAt;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getSessionType() {
        return sessionType;
    }

    public String getSubject() {
        return subject;
    }

    public String getRareMark() {
        return rareMark;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
