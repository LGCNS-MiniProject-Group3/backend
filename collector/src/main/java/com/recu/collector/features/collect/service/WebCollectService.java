package com.recu.collector.features.collect.service;

import com.recu.collector.client.ContentClient;
import com.recu.collector.features.collect.domain.BlogSource;
import com.recu.collector.features.collect.domain.SourceType;
import com.recu.collector.features.collect.domain.dto.CollectedPost;
import com.recu.collector.features.collect.domain.dto.CompanyTarget;
import com.recu.collector.features.collect.domain.dto.ImportedPost;
import com.recu.collector.features.collect.domain.dto.TechDictionary;
import com.recu.collector.messaging.PostCollectedPublisher;
import com.recu.common.kafka.event.PostCollectedEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 웹에서 직접 받아 표에 넣는 입구(결정 2-81 의 두 번째 입구).
 *
 * <p>기업마다 받기 → 범위 안 새 글만 남기기 → AI 1차 분류 → 저장 순으로 돈다. 이미 있는 글은 분류 전에 걸러 AI 를 다시 부르지 않는다. 처음 돌리면
 * 12개월치가 다 들어가고, 다음부터는 새 글만 들어간다.
 *
 * <p>기업 하나가 끝날 때마다 저장한다. 중간에 멈춰도 끝난 기업의 글은 남는다.
 */
@Service
public class WebCollectService {

  private static final Logger log = LoggerFactory.getLogger(WebCollectService.class);
  private final ContentClient contentClient;
  private final PostCollectedEventMapper mapper;
  private final PostCollectedPublisher publisher;
  private final CollectService collectService;
  private final PostClassifier classifier;

  public WebCollectService(
      ContentClient contentClient,
      PostCollectedEventMapper mapper,
      PostCollectedPublisher publisher,
      CollectService collectService,
      PostClassifier classifier) {
    this.contentClient = contentClient;
    this.mapper = mapper;
    this.publisher = publisher;
    this.collectService = collectService;
    this.classifier = classifier;
  }

  /** 한 기업의 결과. 로그에 남긴다. publishStopped면 발행이 실패해 남은 글을 보내지 않았다. */
  public record CompanyResult(
      String company,
      int received,
      int newInWindow,
      int classified,
      int publishedCount,
      boolean publishStopped) {}

  public List<CompanyResult> run(List<CompanyTarget> targets) {
    List<CompanyResult> results = new ArrayList<>();
    for (CompanyTarget company : targets) {
      try {
        CompanyResult result = runOne(company);
        results.add(result);
        if (result.publishStopped()) {
          log.error("{} 에서 발행이 실패해 남은 기업 수집을 멈춥니다", company.name());
          break;
        }
      } catch (Exception e) {
        log.warn("{} 웹 수집 실패, 다음 기업으로 넘어갑니다: {}", company.name(), e.getMessage());
      }
    }
    return results;
  }

  CompanyResult runOne(CompanyTarget company) {
    if (company.feedUrl() == null || company.feedType() == null) {
      log.warn("{} 수집 주소가 없어 건너뜁니다", company.name());
      return new CompanyResult(company.name(), 0, 0, 0, 0, false);
    }
    BlogSource source =
        new BlogSource(company.name(), SourceType.valueOf(company.feedType()), company.feedUrl());

    // 이 기업에 이미 저장된 URL을 한 번에 받아 둔다 (글마다 Feign 호출하지 않음)
    Set<String> existingUrls = new HashSet<>(contentClient.getCollectedUrls(company.companyId()));

    List<CollectedPost> received = collectService.collectOne(source);
    List<CollectedPost> fresh = freshInWindow(received, source, existingUrls);

    List<ImportedPost> rows = new ArrayList<>();
    List<String> extracted = new ArrayList<>();
    int classified = 0;
    for (CollectedPost post : fresh) {
      ImportedPost.Ai ai = classifier.classify(post).orElse(null);
      if (ai != null) {
        classified++;
        extracted.addAll(ai.core_techs());
      }
      rows.add(toRow(post, ai));
    }

    TechDictionary dict = dictionary(extracted);
    TechNameResolver resolver = new TechNameResolver(dict);
    Long companyId = company.companyId();

    int published = 0;
    boolean stopped = false;
    for (ImportedPost row : rows) {
      PostCollectedEvent event = mapper.toEvent(row, companyId, resolver);
      if (!publisher.publish(event)) {
        log.error(
            "{} 발행이 실패해 멈춥니다. 발행 {}편 / 전체 {}편, 멈춘 글 url={}",
            company.name(),
            published,
            rows.size(),
            row.url());
        stopped = true;
        break;
      }
      published++;
    }

    CompanyResult result =
        new CompanyResult(
            company.name(), received.size(), fresh.size(), classified, published, stopped);
    log.info("웹 수집 {}", result);
    return result;
  }

  /** 범위 안이고 표에 없는 글. 같은 주소가 두 번 오면 한 번만 둔다. */
  private List<CollectedPost> freshInWindow(
      List<CollectedPost> received, BlogSource source, Set<String> existingUrls) {
    Set<String> seen = new LinkedHashSet<>();
    List<CollectedPost> fresh = new ArrayList<>();
    for (CollectedPost post : received) {
      if (post.url() == null
          || !source.covers(post.publishedAt())
          || !seen.add(post.url())
          || existingUrls.contains(post.url())) {
        continue;
      }
      fresh.add(post);
    }
    return fresh;
  }

  private TechDictionary dictionary(List<String> extracted) {
    // existing = 비움 (content DB 조회 안 함)
    // preferred = 화면 선택 20종 (로컬 상수)
    return TechNameResolver.buildDictionary(List.of(), selectableNames(), extracted);
  }

  /** 수집한 글을 저장 단계가 읽는 모양으로 옮긴다. 파일 입구와 저장을 같이 쓰기 위해서다. */
  static ImportedPost toRow(CollectedPost post, ImportedPost.Ai ai) {
    List<ImportedPost.Heading> headings =
        post.headings() == null
            ? List.of()
            : post.headings().stream()
                .map(h -> new ImportedPost.Heading(h.idx(), h.level(), h.title()))
                .toList();
    return new ImportedPost(
        post.company(),
        post.externalId(),
        post.title(),
        post.url(),
        post.publishedAt().toString(),
        post.charCount(),
        post.hasCode(),
        post.sourceCategories(),
        headings,
        post.bodyHtml(),
        post.bodyText(),
        ai);
  }

  private static Set<String> selectableNames() {
    return Set.of(
        "Spring",
        "Spring Boot",
        "MySQL",
        "Kotlin",
        "SSE",
        "React",
        "JavaScript",
        "Vite",
        "LLM",
        "RAG",
        "MCP",
        "Claude Code",
        "Machine Learning",
        "Airflow",
        "Kafka",
        "Kubernetes",
        "Redis",
        "AWS",
        "Android",
        "Flutter");
  }
}
