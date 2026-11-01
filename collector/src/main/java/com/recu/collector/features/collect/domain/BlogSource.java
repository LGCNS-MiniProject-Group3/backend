package com.recu.collector.features.collect.domain;

import java.time.LocalDateTime;

/**
 * 한 기업을 어디서 어떻게 받는지. company 행에서 읽어 만든다.
 *
 * @param company 기업 이름. 수집 결과에 그대로 담는다
 * @param type 받는 방식
 * @param url 목록 주소(피드 · JSON · 사이트맵)
 * @param since 이 시각보다 오래된 글은 받지 않는다. 쪽을 넘기는 방식은 여기서 멈춘다
 */
public record BlogSource(String company, SourceType type, String url, LocalDateTime since) {

  /** 받는 범위. 목록 화면이 12개월 안 글만 보여 주므로 수집도 거기까지만 한다. */
  public static final int WINDOW_MONTHS = 12;

  public BlogSource(String company, SourceType type, String url) {
    this(company, type, url, LocalDateTime.now().minusMonths(WINDOW_MONTHS));
  }

  /** 받을 범위 안의 글인지. 발행 시각을 모르면 범위 밖으로 본다. */
  public boolean covers(LocalDateTime publishedAt) {
    return publishedAt != null && !publishedAt.isBefore(since);
  }
}
