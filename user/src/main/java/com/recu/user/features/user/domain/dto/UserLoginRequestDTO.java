package com.recu.user.features.user.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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
public class UserLoginRequestDTO {

  @NotBlank @Email private String email;

  // 길이는 가입할 때만 검사한다. 짧은 비밀번호도 400이 아니라 401(INVALID_CREDENTIALS)로 답한다
  @NotBlank private String password;
}
