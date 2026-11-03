package com.recu.user.features.user.domain.dto;

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
public class UserResponseDTO {

  private Long userId;

  private String name;

  // 경력 정보가 저장되어 있으면 최초 프로필 입력을 마친 사용자다.
  private boolean profileCompleted;
}
