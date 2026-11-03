package com.recu.user.features.user.domain.dto;

import java.util.List;

/** ① content가 기업 정렬·글 필터에 쓰는 선호. content의 UserPreferenceResponseDTO와 같은 모양. */
public record UserPreferenceResponseDTO(
    List<String> jobFields, List<String> wantSkillNames, int profileVersion) {}
