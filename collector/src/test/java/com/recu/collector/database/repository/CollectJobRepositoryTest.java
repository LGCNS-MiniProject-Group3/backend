package com.recu.collector.database.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.recu.collector.database.entity.CollectJobEntity;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class CollectJobRepositoryTest {

  @Autowired CollectJobRepository repository;

  @Test
  void 저장하고_RUNNING으로_찾는다() {
    repository.save(
        CollectJobEntity.builder()
            .jobId("collect-test")
            .status("RUNNING")
            .startedAt(LocalDateTime.now())
            .build());

    assertThat(repository.findFirstByStatus("RUNNING")).isPresent();
  }
}
