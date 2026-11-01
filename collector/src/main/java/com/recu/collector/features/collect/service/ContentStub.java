package com.recu.collector.features.collect.service;

import com.recu.collector.features.collect.domain.dto.CompanyTarget;
import com.recu.collector.features.collect.domain.dto.ImportedPost;
import com.recu.collector.features.collect.domain.dto.TechDictionary;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 분리 이식 때 둔 대역. 원본 수집 코드가 content의 표와 PostIngestService를 직접 쓰던 자리를 한 곳에 모았다. 5번 작업에서 ⑥(Feign)과
 * A(Kafka 발행)로 바꾸고 이 클래스는 지운다.
 */
@Component
public class ContentStub {

  /** 원본 PostIngestService.IngestResult 모양. 화면과 로그에 쓴다. */
  public record IngestResult(
      int companyCount,
      int techTagCount,
      int newPosts,
      int skipped,
      int sectionCount,
      int tagCount) {}

  // TODO(A): 글 1편마다 recu.post.collected 를 발행한다. content가 받아 저장한다.
  //  원본: postIngestService.ingest(rows, dictionary) (content 표에 직접 저장)
  public IngestResult publish(List<ImportedPost> rows, TechDictionary dictionary) {
    throw new UnsupportedOperationException("TODO(A): 수집 글 발행 미구현");
  }

  // TODO(⑥): content의 GET /internal/companies 로 수집 대상 기업을 받는다.
  //  원본: postIngestService.ensureCompanies() (없는 기업을 만들고 전체 반환)
  public List<CompanyTarget> companies() {
    throw new UnsupportedOperationException("TODO(⑥): 수집 대상 기업 조회 미구현");
  }

  // TODO(⑥): 위 목록에서 고른다. 없는 기업이면 404.
  //  원본: companyRepository.findById(companyId)
  public Optional<CompanyTarget> company(Long companyId) {
    throw new UnsupportedOperationException("TODO(⑥): 수집 대상 기업 조회 미구현");
  }

  // TODO(⑥): content의 GET /internal/posts/urls?companyId= 로 이미 저장된 url을 받아 AI 재분류를 막는다.
  //  원본: blogPostRepository.existsByUrl(url)
  public boolean existsByUrl(String url) {
    throw new UnsupportedOperationException("TODO(⑥): 저장된 글 url 조회 미구현");
  }

  // TODO(확인 요망): 기술 사전을 만들 때 쓰던 기존 기술 이름과 고를 수 있는 기술 목록. ①~⑦에 없는 조회다.
  //  사전 만들기를 content의 A 수신 쪽으로 옮길지(처음 보는 기술 추가와 같은 자리), 내부 API를 더할지 5번·4번이 정한다.
  //  원본: techTagRepository.findAll() 이름, PostIngestService.selectableNames()
  public List<String> techNames() {
    throw new UnsupportedOperationException("TODO(확인 요망): 기술 이름 조회 미구현");
  }

  public static Set<String> selectableNames() {
    throw new UnsupportedOperationException("TODO(확인 요망): 고를 수 있는 기술 목록 미구현");
  }
}
