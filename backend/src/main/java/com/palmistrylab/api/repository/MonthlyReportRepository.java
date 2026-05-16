package com.palmistrylab.api.repository;

import com.palmistrylab.api.entity.MonthlyReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MonthlyReportRepository extends JpaRepository<MonthlyReportEntity, Long> {

  Optional<MonthlyReportEntity> findFirstByUserIdAndYearMonth(String userId, String yearMonth);
}
