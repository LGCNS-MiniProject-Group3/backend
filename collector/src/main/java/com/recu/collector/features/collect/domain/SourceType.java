package com.recu.collector.features.collect.domain;

/** 기업마다 글을 받는 방식. 결정 2-55 의 다섯 가지에 토스 목록 API 를 더했다. */
public enum SourceType {
  /** 피드에 본문까지 실려 온다. 올리브영 · 인프랩 · SK플래닛 · 토스 */
  FEED_FULL,
  /** 같은데 쪽을 넘겨야 다 온다. 우아한형제들 */
  FEED_PAGED,
  /** 피드에 목록만 온다. 본문은 글 페이지에서. 컬리 */
  FEED_LIST,
  /** 목록이 JSON 이고 상세를 한 번 더 부른다. 네이버 D2 */
  JSON_API,
  /** 사이트맵에서 주소만 얻는다. LY */
  SITEMAP,
  /** 목록과 발행 시각은 JSON 으로 오고 본문은 글 페이지에서 읽는다. 토스(피드는 최근 20편만 준다) */
  JSON_LIST_PAGE
}
