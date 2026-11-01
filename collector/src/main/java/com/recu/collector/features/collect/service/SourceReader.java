package com.recu.collector.features.collect.service;

import com.recu.collector.features.collect.domain.BlogSource;
import com.recu.collector.features.collect.domain.SourceType;
import com.recu.collector.features.collect.domain.dto.CollectedPost;
import java.util.List;

/** 한 가지 방식으로 기업 글을 받아 온다. 방식마다 구현이 하나씩 있다(결정 2-55). */
public interface SourceReader {

  boolean supports(SourceType type);

  /** 그 기업의 글을 받는다. 목록을 못 받으면 예외를 던지고, 글 한 편이 안 되는 것은 건너뛴다. */
  List<CollectedPost> read(BlogSource source) throws Exception;
}
