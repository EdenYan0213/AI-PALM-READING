package com.palmistrylab.api.palm;

import com.palmistrylab.api.palm.SessionRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRecordRepository extends JpaRepository<SessionRecordEntity, String> {

  long countBySessionType(String sessionType);
}
