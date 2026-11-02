package com.recu.common.web.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 모든 서비스가 같이 쓰는 오류 코드. 원본에 있던 코드는 이름과 메시지를 바꾸지 않는다. */
@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
  FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
  INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
  NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
  // 새로 생김: 원본은 경로에 없는 메서드(예: DELETE /api/tech-tags)를 500으로 응답했다
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),
  // MSA에서 새로 생김: 다른 서비스 호출이 실패하거나 시간이 넘었을 때
  UPSTREAM_ERROR(HttpStatus.BAD_GATEWAY, "다른 서비스가 응답하지 않습니다. 잠시 후 다시 시도해 주세요.");

  private final HttpStatus status;
  private final String message;
}
