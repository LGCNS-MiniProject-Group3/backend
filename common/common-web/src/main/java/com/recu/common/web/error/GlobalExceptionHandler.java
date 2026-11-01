package com.recu.common.web.error;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 원본 GlobalExceptionHandler를 옮긴 것. 서비스는 따로 만들지 않고 이것을 쓴다. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException exception) {
    return errorResponse(exception.getErrorCode(), exception.getField());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleInvalidInput(
      MethodArgumentNotValidException exception) {
    String field =
        exception.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(FieldError::getField)
            .orElse(null);
    return errorResponse(CommonErrorCode.INVALID_INPUT, field);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleException(Exception exception) {
    log.error("Unhandled exception", exception);
    return errorResponse(CommonErrorCode.INTERNAL_ERROR, null);
  }

  static ResponseEntity<ErrorResponse> errorResponse(ErrorCode errorCode, String field) {
    return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.of(errorCode, field));
  }
}
