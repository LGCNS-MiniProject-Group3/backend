package com.recu.user.features.user.domain.dto;

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

  // Google userinfo API에서 사용자를 확인할 OAuth 2.0 access token
  @NotBlank private String googleAccessToken;
}
