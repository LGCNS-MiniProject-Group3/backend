package com.recu.user.features.user.domain.dto;

import java.util.List;

/**
 * ④ guide가 AI 입력과 가이드 재사용 키로 쓰는 프로필. guide의 UserProfileResponseDTO와 같은 모양 (#45에서 합의).
 *
 * <p>경력을 비워 두었으면 careerYears는 null, 프로필을 등록하지 않았으면 profileVersion은 0이다.
 */
public record UserProfileResponseDTO(
    Long userId,
    Integer careerYears,
    List<String> jobFields,
    List<String> haveSkills,
    List<String> wantSkills,
    int profileVersion) {}
