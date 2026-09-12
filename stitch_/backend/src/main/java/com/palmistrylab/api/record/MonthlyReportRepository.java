package com.palmistrylab.api.record;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface MonthlyReportRepository extends JpaRepository<MonthlyReportEntity, Long> {

  /**
   * 悲观锁读：配合 PalmRecordService.getMonthlyReport 的 @Transactional，
   * 串行化同一用户的月报 upsert，避免并发首查产生重复行。
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<MonthlyReportEntity> findFirstByUserIdAndYearMonth(String userId, String yearMonth);
}
