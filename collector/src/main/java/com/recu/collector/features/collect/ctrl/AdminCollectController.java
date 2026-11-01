package com.recu.collector.features.collect.ctrl;

import com.recu.collector.features.collect.service.CollectJobService;
import com.recu.collector.features.collect.service.ContentStub;
import com.recu.collector.features.collect.service.PostImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자용 적재 입구.
 *
 * <p>아직 권한을 걸지 않았다. 관리자 계정을 만드는 길이 없어 로그인해도 모두 USER 로 들어오기 때문이다. 계정이 생기면 SecurityConfig 에서
 * /api/admin/** 을 ADMIN 으로 조인다.
 */
@RestController
@RequestMapping("/api/admin/collect")
@Tag(name = "관리자 · 적재", description = "수집한 글을 표에 넣는다. 운영자만 부른다")
public class AdminCollectController {

  private final PostImportService importService;
  private final CollectJobService jobService;

  public AdminCollectController(PostImportService importService, CollectJobService jobService) {
    this.importService = importService;
    this.jobService = jobService;
  }

  /** 웹 수집 요청 바디. 통째로 생략하면 전체 기업이다. */
  public record CollectRequest(Long companyId) {}

  @PostMapping
  @Operation(
      summary = "웹에서 글 수집",
      description =
          "기업 블로그에서 12개월 안 글을 받아 AI 1차 분류를 매기고 표에 넣는다. 오래 걸려서 바로 202 를 돌려주고 뒤에서 돈다. "
              + "이미 있는 글은 url 로 걸러 AI 를 다시 부르지 않는다. 바디를 생략하면 전체 기업을 받는다. ADMIN 만 부른다.")
  public ResponseEntity<CollectJobService.Job> collectFromWeb(
      @RequestBody(required = false) CollectRequest request) {
    Long companyId = request == null ? null : request.companyId();
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(jobService.start(companyId));
  }

  @PostMapping("/import")
  @Operation(
      summary = "파일에서 글 적재",
      description =
          "수집 단계가 만든 파일(classified.jsonl · posts_12m*.jsonl · tech_dict.json)을 읽어 표에 넣는다. "
              + "폴더 경로는 .env 의 COLLECT_DATA_DIR 에서 읽는다. "
              + "이미 있는 글은 url 로 걸러내므로 여러 번 불러도 행이 늘지 않는다.")
  public ResponseEntity<ContentStub.IngestResult> importFromFiles() {
    return ResponseEntity.ok(importService.importFromFiles());
  }
}
