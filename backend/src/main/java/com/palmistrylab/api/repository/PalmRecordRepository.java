package com.palmistrylab.api.repository;

import com.palmistrylab.api.entity.PalmRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PalmRecordRepository extends JpaRepository<PalmRecordEntity, Long> {

  long countByRecordType(String recordType);

  long countByUserIdAndRecordDateBetween(String userId, LocalDate startDate, LocalDate endDate);

  List<PalmRecordEntity> findByUserIdAndRecordDateBetweenOrderByRecordDateAscCreatedAtAsc(
      String userId,
      LocalDate startDate,
      LocalDate endDate);

  Optional<PalmRecordEntity> findFirstByUserIdAndRecordDateOrderByCreatedAtDesc(String userId, LocalDate recordDate);

  Optional<PalmRecordEntity> findFirstByUserIdAndRecordDateLessThanOrderByRecordDateDescCreatedAtDesc(
      String userId,
      LocalDate recordDate);
}
