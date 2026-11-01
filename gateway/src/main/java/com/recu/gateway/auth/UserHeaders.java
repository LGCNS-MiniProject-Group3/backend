package com.recu.gateway.auth;

/**
 * Gateway가 JWT를 검사한 뒤 서비스에 붙여 보내는 헤더. common-web의 {@code UserHeaders}와 같은 값이다 (Gateway는 WebFlux라
 * common-web을 쓰지 않는다).
 */
public final class UserHeaders {

  public static final String USER_ID = "X-User-Id";
  public static final String USER_ROLE = "X-User-Role";

  /** 클라이언트가 보낸 이 이름으로 시작하는 헤더는 모두 지운다 */
  static final String PREFIX = "X-User-";

  private UserHeaders() {}
}
