package com.recu.collector.features.collect.service;

import com.recu.collector.features.collect.domain.dto.ImportedPost;
import com.recu.collector.features.collect.domain.dto.TechDictionary;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 분리 이식 때 둔 대역. 원본 수집 코드가 content의 표와 PostIngestService를 직접 쓰던 자리를 한 곳에 모았다.
 *
 * <p>⑥(기업·URL 조회)는 ContentClient(Feign)로 옮겼다. 남는 publish·기술 사전 조회는 이후 A(Kafka 발행) 작업에서 바꾸고 이 클래스를
 * 지운다.
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
