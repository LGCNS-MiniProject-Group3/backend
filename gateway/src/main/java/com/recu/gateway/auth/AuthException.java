package com.recu.gateway.auth;

/** JWT 검사 실패. {@link AuthFilter}가 받아 오류 응답으로 바꾼다 */
public class AuthException extends RuntimeException {

  private final AuthError error;

  public AuthException(AuthError error) {
    super(error.message());
    this.error = error;
  }

  public AuthError error() {
    return error;
  }
}
