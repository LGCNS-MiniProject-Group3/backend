package com.recu.guide.error;

import com.recu.common.web.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** guide 서비스에만 있는 오류 코드. 원본 ErrorCode에서 옮겼고 이름·메시지는 그대로다. 공통 코드는 CommonErrorCode. */
@Getter
@RequiredArgsConstructor
public enum GuideErrorCode implements ErrorCode {
  PROFILE_REQUIRED(HttpStatus.BAD_REQUEST, "프로필 등록이 필요합니다."),
  AI_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "AI 서비스를 사용할 수 없습니다.");

  private final HttpStatus status;
  private final String message;
}
