package com.recu.common.web.error;

import com.fasterxml.jackson.databind.JsonMappingException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 원본 GlobalExceptionHandler를 옮긴 것. 서비스는 따로 만들지 않고 이것을 쓴다.
 *
 * <p>원본은 요청이 컨트롤러에 닿기 전에 Spring이 던지는 예외(잘못된 JSON, 타입 불일치, 없는 경로 등)도 마지막 처리기가 받아 500으로 응답했다. 클라이언트
 * 잘못이라 서버 오류 로그를 남기지 않고 400·404·405로 응답한다.
 */
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

  /** 본문이 JSON이 아니거나, 필드 타입이 맞지 않을 때 (예: {"postIds":["a"]}) */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleUnreadableBody(
      HttpMessageNotReadableException exception) {
    return errorResponse(CommonErrorCode.INVALID_INPUT, jsonField(exception.getCause()));
  }

  /** 경로·쿼리 값의 타입이 맞지 않을 때 (예: @PathVariable Long id 에 abc) */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleTypeMismatch(
      MethodArgumentTypeMismatchException exception) {
    return errorResponse(CommonErrorCode.INVALID_INPUT, exception.getName());
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ErrorResponse> handleMissingParameter(
      MissingServletRequestParameterException exception) {
    return errorResponse(CommonErrorCode.INVALID_INPUT, exception.getParameterName());
  }

  @ExceptionHandler(MissingRequestHeaderException.class)
  public ResponseEntity<ErrorResponse> handleMissingHeader(
      MissingRequestHeaderException exception) {
    return errorResponse(CommonErrorCode.INVALID_INPUT, exception.getHeaderName());
  }

  /** 컨트롤러 인자에 직접 단 검증(@Positive Long id 등)에 실패했을 때 */
  @ExceptionHandler(HandlerMethodValidationException.class)
  public ResponseEntity<ErrorResponse> handleMethodValidation(
      HandlerMethodValidationException exception) {
    String field =
        exception.getAllValidationResults().stream()
            .findFirst()
            .map(result -> result.getMethodParameter().getParameterName())
            .orElse(null);
    return errorResponse(CommonErrorCode.INVALID_INPUT, field);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException exception) {
    return errorResponse(CommonErrorCode.NOT_FOUND, null);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ErrorResponse> handleMethodNotSupported(
      HttpRequestMethodNotSupportedException exception) {
    CommonErrorCode errorCode = CommonErrorCode.METHOD_NOT_ALLOWED;
    HttpHeaders headers = new HttpHeaders();
    if (exception.getSupportedHttpMethods() != null) {
      headers.setAllow(exception.getSupportedHttpMethods());
    }
    return ResponseEntity.status(errorCode.getStatus())
        .headers(headers)
        .body(ErrorResponse.of(errorCode, null));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleException(Exception exception) {
    log.error("Unhandled exception", exception);
    return errorResponse(CommonErrorCode.INTERNAL_ERROR, null);
  }

  static ResponseEntity<ErrorResponse> errorResponse(ErrorCode errorCode, String field) {
    return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.of(errorCode, field));
  }

  /** JSON 안에서 문제가 된 필드 이름. 본문이 아예 JSON이 아니면 null */
  private static String jsonField(Throwable cause) {
    if (!(cause instanceof JsonMappingException mapping)) {
      return null;
    }
    List<JsonMappingException.Reference> path = mapping.getPath();
    for (int i = path.size() - 1; i >= 0; i--) {
      if (path.get(i).getFieldName() != null) {
        return path.get(i).getFieldName();
      }
    }
    return null;
  }
}
