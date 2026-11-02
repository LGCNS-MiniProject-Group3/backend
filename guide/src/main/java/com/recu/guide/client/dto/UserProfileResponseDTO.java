package com.recu.guide.client.dto;

import java.util.List;

/**
 * ④ 응답. careerYears가 null이면 프로필을 아직 등록하지 않은 사람이다.
 *
 * <p>기술은 이름 목록으로 받는다. 필드 이름은 이슈 #45에서 2번과 맞춘다.
 */
public record UserProfileResponseDTO(
    Long userId,
    Integer careerYears,
    List<String> jobFields,
    List<String> haveSkills,
    List<String> wantSkills,
    Integer profileVersion) {}
