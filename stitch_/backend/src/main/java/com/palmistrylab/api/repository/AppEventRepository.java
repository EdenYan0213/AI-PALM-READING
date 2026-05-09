package com.palmistrylab.api.repository;

import com.palmistrylab.api.entity.AppEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppEventRepository extends JpaRepository<AppEventEntity, Long> {

  long countByEventName(String eventName);
}
