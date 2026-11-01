package com.recu.common.web.error;

import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 다른 서비스 호출 실패(오류 응답, 연결 실패, 시간 초과)를 502 UPSTREAM_ERROR로 바꾼다. 실패해도 기본값으로 답해야 하는 곳(② 저장 표시)은 서비스가 직접
 * 잡아서 처리하므로 여기까지 오지 않는다.
 *
 * <p>GlobalExceptionHandler의 Exception 처리보다 먼저 골라지도록 순서를 앞에 둔다.
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FeignExceptionHandler {

  @ExceptionHandler(FeignException.class)
  public ResponseEntity<ErrorResponse> handleFeignException(FeignException exception) {
    // 시간 초과·연결 실패는 status가 -1로 온다
    log.warn("Upstream call failed: status={}, {}", exception.status(), exception.getMessage());
    return GlobalExceptionHandler.errorResponse(CommonErrorCode.UPSTREAM_ERROR, null);
  }
}
