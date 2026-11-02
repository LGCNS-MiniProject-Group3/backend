package com.recu.common.kafka.event;

import java.time.OffsetDateTime;
import java.util.List;

/** A. recu.post.collected 내용물: 수집·분류가 끝난 글 1편 (design.md 6장). 키는 url. */
public record PostCollectedEvent(
    Long companyId,
    String url,
    String externalId,
    String title,
    OffsetDateTime publishedAt,
    String contentHtml,
    String contentText,
    Boolean hasCode,
    Boolean isTech,
    String field,
    String level,
    String summary,
    List<String> categories,
    List<Tag> tags,
    List<Section> sections) {

  /** 기술 태그와 순위. */
  public record Tag(String name, Integer rank) {}

  /** 본문 구간. level은 소제목 수준(h2, h3 등)이다. */
  public record Section(Integer seq, String heading, String level, String contentText) {}
}
