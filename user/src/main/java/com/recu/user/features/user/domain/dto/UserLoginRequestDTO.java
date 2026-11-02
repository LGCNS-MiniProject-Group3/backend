package com.recu.user.features.user.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.validator.constraints.Email;

@Builder
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class UserLoginRequestDTO {

  @NotBlank @Email private String email;

  @NotBlank
  @Size(min = 8)
  private String password;
}
