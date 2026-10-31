package com.recu.common.web.auth;

/** Gateway가 JWT를 검사한 뒤 붙여 보내는 헤더 이름. Gateway와 같은 값을 쓴다. */
public final class UserHeaders {

  public static final String USER_ID = "X-User-Id";
  public static final String USER_ROLE = "X-User-Role";

  private UserHeaders() {}
}
