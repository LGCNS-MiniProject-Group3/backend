package com.recu.gateway.auth;

import org.springframework.http.HttpStatus;

/** Gateway가 직접 돌려주는 인증 오류. 이름·메시지는 원본 SecurityConfig·JwtAuthenticationFilter와 같다 */
public enum AuthError {
  /** 토큰이 필요한 경로에 토큰이 없음 */
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다."),
  /** 서명이 틀렸거나 형식이 잘못된 토큰, Refresh 토큰 */
  INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "유효하지 않은 인증 정보입니다."),
  TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED", "JWT 토큰이 만료되었습니다."),
  /** 로그인은 했지만 관리자 API 권한이 없음 */
  FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "접근 권한이 없습니다.");

  private final HttpStatus status;
  private final String code;
  private final String message;

  AuthError(HttpStatus status, String code, String message) {
    this.status = status;
    this.code = code;
    this.message = message;
  }

  public HttpStatus status() {
    return status;
  }

  public String code() {
    return code;
  }

  public String message() {
    return message;
  }
}
