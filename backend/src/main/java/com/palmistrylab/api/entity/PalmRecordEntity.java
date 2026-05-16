package com.palmistrylab.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "palm_records")
public class PalmRecordEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false, length = 64)
  private String userId;

  @Column(name = "record_type", nullable = false, length = 16)
  private String recordType;

  @Column(name = "record_mode", nullable = false, length = 16)
  private String recordMode;

  @Column(name = "photo_url", columnDefinition = "TEXT")
  private String photoUrl;

  @Column(name = "traces_json", columnDefinition = "TEXT")
  private String tracesJson;

  @Column(name = "energy_level", nullable = false)
  private int energyLevel;

  @Column(name = "wisdom_active", nullable = false)
  private int wisdomActive;

  @Column(name = "emotion_wave", nullable = false)
  private int emotionWave;

  @Column(name = "ai_summary", nullable = false, columnDefinition = "TEXT")
  private String aiSummary;

  @Column(name = "user_note", columnDefinition = "TEXT")
  private String userNote;

  @Column(name = "record_date", nullable = false)
  private LocalDate recordDate;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected PalmRecordEntity() {
  }

  public PalmRecordEntity(
      String userId,
      String recordType,
      String recordMode,
      String photoUrl,
      String tracesJson,
      int energyLevel,
      int wisdomActive,
      int emotionWave,
      String aiSummary,
      String userNote,
      LocalDate recordDate,
      Instant createdAt) {
    this.userId = userId;
    this.recordType = recordType;
    this.recordMode = recordMode;
    this.photoUrl = photoUrl;
    this.tracesJson = tracesJson;
    this.energyLevel = energyLevel;
    this.wisdomActive = wisdomActive;
    this.emotionWave = emotionWave;
    this.aiSummary = aiSummary;
    this.userNote = userNote;
    this.recordDate = recordDate;
    this.createdAt = createdAt;
  }

  public Long getId() {
    return id;
  }

  public String getUserId() {
    return userId;
  }

  public String getRecordType() {
    return recordType;
  }

  public String getRecordMode() {
    return recordMode;
  }

  public String getPhotoUrl() {
    return photoUrl;
  }

  public String getTracesJson() {
    return tracesJson;
  }

  public int getEnergyLevel() {
    return energyLevel;
  }

  public int getWisdomActive() {
    return wisdomActive;
  }

  public int getEmotionWave() {
    return emotionWave;
  }

  public String getAiSummary() {
    return aiSummary;
  }

  public String getUserNote() {
    return userNote;
  }

  public LocalDate getRecordDate() {
    return recordDate;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setUserNote(String userNote) {
    this.userNote = userNote;
  }
}
