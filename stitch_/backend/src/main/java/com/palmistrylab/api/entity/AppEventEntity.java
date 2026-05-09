package com.palmistrylab.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "app_event")
public class AppEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_name", nullable = false, length = 64)
    private String eventName;

    @Column(name = "session_id", length = 32)
    private String sessionId;

    @Column(name = "channel", length = 32)
    private String channel;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AppEventEntity() {
    }

    public AppEventEntity(String eventName, String sessionId, String channel, Instant createdAt) {
        this.eventName = eventName;
        this.sessionId = sessionId;
        this.channel = channel;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getEventName() {
        return eventName;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getChannel() {
        return channel;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
