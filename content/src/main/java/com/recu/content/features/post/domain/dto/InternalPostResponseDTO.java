package com.recu.content.features.post.domain.dto;

import java.time.LocalDate;
import java.util.List;

public final class InternalPostResponseDTO {
  private InternalPostResponseDTO() {}

  public record Section(int seq, String heading, String contentText) {}

  public record GuideInput(
      Long postId, String title, String field, String level, List<Section> sections) {}

  public record Summary(
      Long postId,
      Long companyId,
      String companyName,
      String title,
      List<String> categories,
      String summary,
      List<String> skills,
      LocalDate publishedAt) {}
}
