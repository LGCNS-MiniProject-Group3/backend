package com.recu.collector.features.collect.service;

import com.recu.collector.database.entity.CollectJobEntity;
import com.recu.collector.database.repository.CollectJobRepository;
import com.recu.collector.features.collect.domain.dto.CollectJobResponseDTO;
import com.recu.collector.features.collect.domain.dto.CompanyTarget;
import com.recu.common.web.error.BusinessException;
import com.recu.common.web.error.CommonErrorCode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 웹 수집을 뒤에서 돌린다. 기업 8곳을 받고 글마다 AI 를 부르면 수십 분이 걸려서 요청은 바로 202 로 돌려준다.
 *
 * <p>한 번에 하나만 돈다. 도는 중에 또 부르면 새로 시작하지 않고 돌고 있는 작업을 알려 준다. 같은 글을 두 작업이 동시에 넣지 않게 하기 위해서다.
 */
@Service
public class CollectJobService {

  private static final Logger log = LoggerFactory.getLogger(CollectJobService.class);
  private static final ZoneOffset KST = ZoneOffset.of("+09:00");
  private static final DateTimeFormatter ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

  private static final String RUNNING = "RUNNING";
  private static final String DONE = "DONE";
  private static final String FAILED = "FAILED";

  private final WebCollectService webCollectService;
  private final ContentStub content;
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final CollectJobRepository repository;

  public CollectJobService(
      WebCollectService webCollectService, ContentStub content, CollectJobRepository repository) {
    this.webCollectService = webCollectService;
    this.content = content;
    this.repository = repository;
  }

  public synchronized CollectJobResponseDTO start(Long companyId) {
    // 1) 이미 도는 작업?
    var existing = repository.findFirstByStatus(RUNNING);
    if (existing.isPresent()) {
      log.info("웹 수집이 이미 돌고 있어 새로 시작하지 않습니다: {}", existing.get().getJobId());
      return toDto(existing.get());
    }

    // 2) 특정 기업만이면 존재 확인 (없으면 404)
    if (companyId != null) {
      content
          .company(companyId)
          .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    // 3) DB에 RUNNING 저장
    LocalDateTime now = LocalDateTime.now(KST).withNano(0);
    String jobId = "collect-" + now.format(ID_FORMAT);
    CollectJobEntity entity =
        CollectJobEntity.builder()
            .jobId(jobId)
            .companyId(companyId)
            .status(RUNNING)
            .startedAt(now)
            .newPostCount(0)
            .build();
    repository.save(entity);

    // 4) 백그라운드에서 수집 (기업 목록은 여기서 구함)
    executor.submit(() -> runJob(jobId, companyId));

    return toDto(entity);
  }

  private List<CompanyTarget> targets(Long companyId) {
    List<CompanyTarget> all = new ArrayList<>(content.companies());
    if (companyId == null) {
      return all;
    }
    return List.of(
        content
            .company(companyId)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND)));
  }

  private void runJob(String jobId, Long companyId) {
    CollectJobEntity entity = repository.findById(jobId).orElse(null);
    if (entity == null) {
      return;
    }

    log.info("웹 수집 시작 {}", jobId);
    try {
      List<CompanyTarget> targets = targets(companyId);
      log.info("웹 수집 대상 {} (기업 {}곳)", jobId, targets.size());

      List<WebCollectService.CompanyResult> results = webCollectService.run(targets);
      int saved =
          results.stream().filter(r -> r.saved() != null).mapToInt(r -> r.saved().newPosts()).sum();

      entity.setStatus(DONE);
      entity.setNewPostCount(saved);
      entity.setFinishedAt(LocalDateTime.now(KST).withNano(0));
      repository.save(entity);
      log.info("웹 수집 끝 {} (새 글 {}편)", jobId, saved);
    } catch (Exception e) {
      log.error("웹 수집 중단 {}", jobId, e);
      entity.setStatus(FAILED);
      entity.setFinishedAt(LocalDateTime.now(KST).withNano(0));
      repository.save(entity);
    }
  }

  private CollectJobResponseDTO toDto(CollectJobEntity e) {
    return new CollectJobResponseDTO(
        e.getJobId(),
        e.getCompanyId(),
        e.getStatus(),
        e.getStartedAt().atOffset(KST),
        e.getFinishedAt() == null ? null : e.getFinishedAt().atOffset(KST),
        e.getNewPostCount());
  }

  public List<CollectJobResponseDTO> listRecent() {
    return repository.findTop20ByOrderByStartedAtDesc().stream().map(this::toDto).toList();
  }

  public CollectJobResponseDTO get(String jobId) {
    return repository
        .findById(jobId)
        .map(this::toDto)
        .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
  }
}
