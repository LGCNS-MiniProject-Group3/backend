package com.recu.common.web.error;

import org.springframework.http.HttpStatus;

/**
 * 오류 코드가 갖출 모양. 공통 코드는 {@link CommonErrorCode}, 서비스만의 코드는 각 서비스의 {@code <서비스>ErrorCode} enum이 구현한다.
 */
public interface ErrorCode {

  HttpStatus getStatus();

  String getMessage();

  /** enum 상수 이름이 그대로 응답의 code가 된다 (원본과 같음). */
  String name();

  default String getCode() {
    return name();
  }
}
