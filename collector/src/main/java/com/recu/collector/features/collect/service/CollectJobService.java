package com.recu.collector.features.collect.service;

import com.recu.collector.client.ContentClient;
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

  private final WebCollectService webCollectService;
  private final ContentClient contentClient;
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final CollectJobRepository repository;

  public CollectJobService(
      WebCollectService webCollectService,
      ContentClient contentClient,
      CollectJobRepository repository) {
    this.webCollectService = webCollectService;
    this.contentClient = contentClient;
    this.repository = repository;
  }

  public synchronized CollectJobResponseDTO start(Long companyId) {
    // 1) 이미 도는 작업?
    var existing = repository.findFirstByStatus(RUNNING);
    if (existing.isPresent()) {
      log.info("웹 수집이 이미 돌고 있어 새로 시작하지 않습니다: {}", existing.get().getJobId());
      return toDto(existing.get());
    }

    // 2) 특정 기업만이면 존재 확인 (없으면 404). companyId == null 이면 전체 수집이라 건너뛴다.
    if (companyId != null) {
      boolean found =
          contentClient.getCompanies().stream().anyMatch(c -> companyId.equals(c.companyId()));
      if (!found) {
        throw new BusinessException(CommonErrorCode.NOT_FOUND);
      }
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
    List<CompanyTarget> all = new ArrayList<>(contentClient.getCompanies());
    if (companyId == null) {
      return all;
    }
    return all.stream()
        .filter(c -> companyId.equals(c.companyId()))
        .findFirst()
        .map(List::of)
        .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
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

      int saved = results.stream().mapToInt(WebCollectService.CompanyResult::publishedCount).sum();

      // 성공: DONE + 발행(저장)한 글 수 + 종료 시각
      entity.markDone(saved, LocalDateTime.now(KST).withNano(0));
      repository.save(entity);
      log.info("웹 수집 끝 {} (새 글 {}편)", jobId, saved);
    } catch (Exception e) {
      log.error("웹 수집 중단 {}", jobId, e);
      // 실패: FAILED + 종료 시각 (newPostCount는 그대로)
      entity.markFailed(LocalDateTime.now(KST).withNano(0));
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

  /** 재기동 후 남아 있는 RUNNING을 FAILED로 바꾼다. 안 바꾸면 새 수집을 시작하지 못한다. */
  public void failStaleRunningJobs() {
    LocalDateTime now = LocalDateTime.now(KST).withNano(0);
    List<CollectJobEntity> stale = repository.findByStatus(RUNNING);
    for (CollectJobEntity job : stale) {
      job.markFailed(now);
      repository.save(job);
      log.warn("재기동으로 중단된 수집을 FAILED로 바꿉니다: {}", job.getJobId());
    }
  }
}
