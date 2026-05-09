package com.palmistrylab.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "monthly_reports")
public class MonthlyReportEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false, length = 64)
  private String userId;

  @Column(name = "year_month", nullable = false, length = 7)
  private String yearMonth;

  @Column(name = "record_count", nullable = false)
  private int recordCount;

  @Column(name = "dominant_energy", nullable = false, length = 32)
  private String dominantEnergy;

  @Column(name = "energy_trend_json", columnDefinition = "TEXT")
  private String energyTrendJson;

  @Column(name = "report_text", nullable = false, columnDefinition = "TEXT")
  private String reportText;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected MonthlyReportEntity() {
  }

  public MonthlyReportEntity(
      String userId,
      String yearMonth,
      int recordCount,
      String dominantEnergy,
      String energyTrendJson,
      String reportText,
      Instant createdAt) {
    this.userId = userId;
    this.yearMonth = yearMonth;
    this.recordCount = recordCount;
    this.dominantEnergy = dominantEnergy;
    this.energyTrendJson = energyTrendJson;
    this.reportText = reportText;
    this.createdAt = createdAt;
  }

  public Long getId() {
    return id;
  }

  public String getUserId() {
    return userId;
  }

  public String getYearMonth() {
    return yearMonth;
  }

  public int getRecordCount() {
    return recordCount;
  }

  public String getDominantEnergy() {
    return dominantEnergy;
  }

  public String getEnergyTrendJson() {
    return energyTrendJson;
  }

  public String getReportText() {
    return reportText;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setRecordCount(int recordCount) {
    this.recordCount = recordCount;
  }

  public void setDominantEnergy(String dominantEnergy) {
    this.dominantEnergy = dominantEnergy;
  }

  public void setEnergyTrendJson(String energyTrendJson) {
    this.energyTrendJson = energyTrendJson;
  }

  public void setReportText(String reportText) {
    this.reportText = reportText;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
