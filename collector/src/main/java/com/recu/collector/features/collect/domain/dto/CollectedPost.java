package com.recu.collector.features.collect.domain.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 기업 블로그에서 받아온 글 한 편.
 *
 * <p>받아오기 → 쪼개기 → 분류 → 저장 단계가 모두 이 모양 하나만 본다. 원천이 RSS 인지 JSON 인지 HTML 인지는 받아오는 코드까지만 안다.
 */
public record CollectedPost(
    String company,
    String externalId,
    String title,
    String url,
    LocalDateTime publishedAt,
    String bodyHtml,
    String bodyText,
    int charCount,
    boolean hasCode,
    List<Heading> headings,
    List<String> sourceCategories) {

  /** 본문 속 소제목 하나. level 은 h2 · h3 만 나온다(실측 4,753개). idx 는 글 안에서 1부터. */
  public record Heading(int idx, String level, String title) {}

  /** 소제목을 찾은 뒤 그 값만 채운 새 그릇을 만든다. record 는 값을 못 바꾸므로 갈아끼운다. */
  public CollectedPost withHeadings(List<Heading> found) {
    return new CollectedPost(
        company,
        externalId,
        title,
        url,
        publishedAt,
        bodyHtml,
        bodyText,
        charCount,
        hasCode,
        found,
        sourceCategories);
  }
}
