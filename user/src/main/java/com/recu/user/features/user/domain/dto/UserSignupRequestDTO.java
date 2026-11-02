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
  // postman에서 입력 받을 회원가입 JSON
  @NotBlank @Email private String email;

  @NotBlank
  @Size(min = 8)
  private String password;

  @NotBlank
  @Size(max = 50)
  private String name;
}
