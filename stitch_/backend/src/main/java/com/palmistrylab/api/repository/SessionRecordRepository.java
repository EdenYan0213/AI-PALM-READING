package com.palmistrylab.api.repository;

import com.palmistrylab.api.entity.SessionRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRecordRepository extends JpaRepository<SessionRecordEntity, String> {

  long countBySessionType(String sessionType);
}
