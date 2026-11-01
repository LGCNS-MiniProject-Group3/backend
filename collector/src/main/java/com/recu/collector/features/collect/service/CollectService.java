package com.recu.collector.features.collect.service;

import com.recu.collector.features.collect.domain.BlogSource;
import com.recu.collector.features.collect.domain.dto.CollectedPost;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 기업 여러 곳을 돌며 글을 받아 온다. 방식에 맞는 구현을 골라 넘긴다.
 *
 * <p>한 기업이 실패해도 나머지를 계속한다. 파이썬 수집기는 한 쪽이라도 실패하면 그 기업을 통째로 포기해서, 재수집에서 한 번 삐끗하면 그 기업 글이 0편이 되고 목록에서
 * 사라졌다.
 */
@Service
public class CollectService {

  private static final Logger log = LoggerFactory.getLogger(CollectService.class);

  private final List<SourceReader> readers;

  public CollectService(List<SourceReader> readers) {
    this.readers = readers;
  }

  public List<CollectedPost> collect(List<BlogSource> sources) {
    List<CollectedPost> all = new ArrayList<>();
    for (BlogSource source : sources) {
      all.addAll(collectOne(source));
    }
    return all;
  }

  public List<CollectedPost> collectOne(BlogSource source) {
    SourceReader reader =
        readers.stream()
            .filter(candidate -> candidate.supports(source.type()))
            .findFirst()
            .orElse(null);
    if (reader == null) {
      log.warn("수집 건너뜀: {} 방식을 읽는 구현이 없다 ({})", source.type(), source.company());
      return List.of();
    }

    try {
      List<CollectedPost> posts = reader.read(source);
      log.info("{} {}편", source.company(), posts.size());
      return posts;
    } catch (Exception e) {
      log.warn("{} 수집 실패, 건너뛴다: {}", source.company(), e.getMessage());
      return List.of();
    }
  }
}
