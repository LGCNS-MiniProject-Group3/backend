package com.recu.common.web.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 인자에 로그인한 회원 번호를 넣는다. 원본의 {@code @AuthenticationPrincipal Long userId} 자리에 쓴다.
 *
 * <pre>{@code
 * public ResponseEntity<...> addBookmark(@CurrentUserId Long userId, ...)
 * }</pre>
 *
 * <p>헤더가 없으면 401 UNAUTHORIZED. 토큰이 필요 없는 경로에서는 쓰지 않는다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUserId {}
