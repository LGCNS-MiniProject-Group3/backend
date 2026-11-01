package com.recu.bookmark.features.bookmark.domain.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

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

  private LocalDateTime createdAt;
}
