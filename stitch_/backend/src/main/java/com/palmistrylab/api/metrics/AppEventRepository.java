package com.palmistrylab.api.metrics;

import com.palmistrylab.api.metrics.AppEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppEventRepository extends JpaRepository<AppEventEntity, Long> {

  long countByEventName(String eventName);
}
