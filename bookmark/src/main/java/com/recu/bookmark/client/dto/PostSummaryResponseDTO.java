package com.recu.bookmark.client.dto;

import java.time.LocalDate;
import java.util.List;

/** ⑦ 응답 한 건. content의 InternalPostResponseDTO.Summary와 같은 모양. */
public record PostSummaryResponseDTO(
    Long postId,
    Long companyId,
    String companyName,
    String title,
    List<String> categories,
    String summary,
    List<String> skills,
    LocalDate publishedAt) {}
