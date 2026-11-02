package com.recu.collector.database.repository;

import com.recu.collector.database.entity.CollectJobEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectJobRepository extends JpaRepository<CollectJobEntity, String> {

  Optional<CollectJobEntity> findFirstByStatus(String status);

  List<CollectJobEntity> findTop20ByOrderByStartedAtDesc();
}
