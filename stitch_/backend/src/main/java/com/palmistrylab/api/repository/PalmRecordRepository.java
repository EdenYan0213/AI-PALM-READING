package com.palmistrylab.api.repository;

import com.palmistrylab.api.entity.PalmRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PalmRecordRepository extends JpaRepository<PalmRecordEntity, Long> {

  long countByRecordType(String recordType);

  long countByUserIdAndRecordDateBetween(String userId, LocalDate startDate, LocalDate endDate);

  /**
   * 列表查询统一走投影：photo_url 存的是整张 base64 图片（TEXT），
   * 不投影会把整月图片大字段一起拉进内存，拖垮日历/月报接口。
   */
  List<RecordSummary> findByUserIdAndRecordDateBetweenOrderByRecordDateAscCreatedAtAsc(
      String userId,
      LocalDate startDate,
      LocalDate endDate);

  Optional<RecordSummary> findFirstByUserIdAndRecordDateOrderByCreatedAtDesc(String userId, LocalDate recordDate);

  Optional<EnergySnapshot> findFirstByUserIdAndRecordDateLessThanOrderByRecordDateDescCreatedAtDesc(
      String userId,
      LocalDate recordDate);

  /** 日历/月报/详情展示所需字段的窄投影（不含图片与轨迹大字段）。 */
  interface RecordSummary {
    Long getId();

    String getUserId();

    LocalDate getRecordDate();

    String getRecordMode();

    int getEnergyLevel();

    int getWisdomActive();

    int getEmotionWave();

    String getAiSummary();

    String getUserNote();

    static RecordSummary of(
        Long id,
        String userId,
        LocalDate recordDate,
        String recordMode,
        int energyLevel,
        int wisdomActive,
        int emotionWave,
        String aiSummary,
        String userNote) {
      return new RecordSummaryRow(id, userId, recordDate, recordMode, energyLevel, wisdomActive, emotionWave, aiSummary, userNote);
    }
  }

  record RecordSummaryRow(
      Long id,
      String userId,
      LocalDate recordDate,
      String recordMode,
      int energyLevel,
      int wisdomActive,
      int emotionWave,
      String aiSummary,
      String userNote) implements RecordSummary {

    @Override
    public Long getId() {
      return id;
    }

    @Override
    public String getUserId() {
      return userId;
    }

    @Override
    public LocalDate getRecordDate() {
      return recordDate;
    }

    @Override
    public String getRecordMode() {
      return recordMode;
    }

    @Override
    public int getEnergyLevel() {
      return energyLevel;
    }

    @Override
    public int getWisdomActive() {
      return wisdomActive;
    }

    @Override
    public int getEmotionWave() {
      return emotionWave;
    }

    @Override
    public String getAiSummary() {
      return aiSummary;
    }

    @Override
    public String getUserNote() {
      return userNote;
    }
  }

  /** 与上次记录对比只需要三项分数。 */
  interface EnergySnapshot {
    int getEnergyLevel();

    int getWisdomActive();

    int getEmotionWave();
  }
}
