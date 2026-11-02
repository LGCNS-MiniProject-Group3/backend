package com.recu.user.error;

import com.recu.common.web.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** user 서비스에만 있는 오류 코드. 원본 ErrorCode에서 옮겼고 이름·메시지는 그대로다. 공통 코드는 CommonErrorCode. */
@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {
  INVALID_REFRESH_TOKEN(HttpStatus.FORBIDDEN, "유효하지 않은 Refresh 토큰입니다."),
  EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
  PROFILE_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 등록된 프로필입니다.");

  private final HttpStatus status;
  private final String message;
}
