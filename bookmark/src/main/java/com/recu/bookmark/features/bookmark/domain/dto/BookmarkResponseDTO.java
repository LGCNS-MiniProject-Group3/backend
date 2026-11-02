package com.recu.bookmark.features.bookmark.domain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

// 추가 응답은 {bookmarkId, postId, createdAt}, 목록 응답은 카드 필드만 (reference/api-spec.md 4.11·4.12)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class BookmarkResponseDTO {

  private Long bookmarkId;
  private Long postId;
  private Long companyId;
  private String title;
  private String companyName;
  private List<String> categories;
  // 06 카드가 04 글 카드를 그대로 쓰므로 04 글 목록과 같은 요지 · 기술 칩을 준다
  private String summary;
  private List<String> skills;
  private LocalDate publishedAt;
  private OffsetDateTime savedAt;
  private Boolean hasGuide;

  private OffsetDateTime createdAt;
}
