package com.recu.content.features.ingest.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * 파일에서 읽은 글 한 편. 분류 파일(classified.jsonl)과 본문 파일(posts_12m*.jsonl)을 url 로 합친 모양.
 *
 * <p>칸 이름은 파일에 적힌 그대로 둔다. 표 칸 이름과 다른 것은 저장할 때 맞춘다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ImportedPost(
    String company,
    String external_id,
    String title,
    String url,
    String published_at,
    Integer char_count,
    Boolean has_code,
    List<String> source_categories,
    List<Heading> headings,
    String body_html,
    String body_text,
    Ai ai) {

  /** 글 안 소제목 하나. 본문을 이 제목들로 잘라 구간을 만든다. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Heading(Integer idx, String level, String title) {}

  /** AI 1차 분류 결과. 실패한 글은 이 칸이 없다. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Ai(
      Boolean is_tech, List<String> core_techs, String field, String level, String summary) {}
}
