package com.recu.guide.client.dto;

import java.util.List;

/**
 * ④ 응답. careerYears는 경력을 입력하지 않았으면 null이고, 프로필을 등록하지 않았으면 profileVersion이 0이다.
 *
 * <p>기술은 이름 목록으로 받는다 (#45에서 2번과 합의).
 */
public record UserProfileResponseDTO(
    Long userId,
    Integer careerYears,
    List<String> jobFields,
    List<String> haveSkills,
    List<String> wantSkills,
    Integer profileVersion) {}
