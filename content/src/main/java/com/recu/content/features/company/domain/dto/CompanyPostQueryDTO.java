package com.recu.content.features.company.domain.dto;

import java.time.LocalDate;
import java.util.List;

/** 트랜잭션 밖에서 외부 응답과 조합할 수 있도록 엔티티 대신 조회 값을 전달한다. */
public record CompanyPostQueryDTO(
    Long postId,
    String title,
    LocalDate publishedAt,
    String field,
    String level,
    String summary,
    List<String> skills,
    Integer charCount,
    int sectionCount,
    String originalUrl,
    boolean hasGuide) {}
