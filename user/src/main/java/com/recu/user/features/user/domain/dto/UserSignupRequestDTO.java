package com.recu.user.features.user.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UserSignupRequestDTO {
  // 입력 검증 규칙은 design.md 4장 회원가입 입력 검증 표
  @NotBlank
  @Email
  @Size(max = 100)
  private String email;

  // BCrypt는 72바이트까지만 쓴다. 한글 등은 한 글자가 여러 바이트라 글자 수로는 충분히 막지 못한다 (확인 요망)
  @NotBlank
  @Size(min = 8, max = 72)
  private String password;

  @NotBlank
  @Size(max = 50)
  private String name;
}
