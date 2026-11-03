package com.recu.collector.features.collect.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recu.collector.client.ContentClient;
import com.recu.collector.database.entity.CollectJobEntity;
import com.recu.collector.database.repository.CollectJobRepository;
import com.recu.collector.features.collect.domain.dto.CompanyTarget;
import com.recu.common.web.error.BusinessException;
import com.recu.common.web.error.CommonErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CollectJobServiceTest {

  @Mock WebCollectService webCollectService;
  @Mock ContentClient contentClient;
  @Mock CollectJobRepository repository;

  @InjectMocks CollectJobService service;

  @Test
  void failStaleRunningJobs_RUNNING을_FAILED로_바꾼다() {
    CollectJobEntity job =
        CollectJobEntity.builder()
            .jobId("collect-stale")
            .status("RUNNING")
            .startedAt(LocalDateTime.of(2026, 11, 2, 12, 0))
            .newPostCount(0)
            .build();
    when(repository.findByStatus("RUNNING")).thenReturn(List.of(job));

    service.failStaleRunningJobs();

    assertThat(job.getStatus()).isEqualTo("FAILED");
    assertThat(job.getFinishedAt()).isNotNull();
    verify(repository).save(job);
  }

  @Test
  void start_이미_RUNNING이면_같은_jobId를_반환한다() {
    CollectJobEntity existing =
        CollectJobEntity.builder()
            .jobId("collect-existing")
            .status("RUNNING")
            .startedAt(LocalDateTime.of(2026, 11, 2, 12, 0))
            .newPostCount(0)
            .build();
    when(repository.findFirstByStatus("RUNNING")).thenReturn(Optional.of(existing));

    var dto = service.start(null);

    assertThat(dto.jobId()).isEqualTo("collect-existing");
    assertThat(dto.status()).isEqualTo("RUNNING");
    verify(repository, never()).save(any()); // 새 작업 안 만듦
  }

  @Test
  void start_없는_companyId면_NOT_FOUND() {
    // ⑥이 기업 1(토스)만 돌려준다고 가정
    when(repository.findFirstByStatus("RUNNING")).thenReturn(Optional.empty());
    when(contentClient.getCompanies())
        .thenReturn(List.of(new CompanyTarget(1L, "토스", "JSON_LIST_PAGE", "https://example.com")));
    // 존재하지 않는 999 요청
    assertThatThrownBy(() -> service.start(999L))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(CommonErrorCode.NOT_FOUND));
    verify(repository, never()).save(any()); // job을 만들면 안 됨
  }
}
