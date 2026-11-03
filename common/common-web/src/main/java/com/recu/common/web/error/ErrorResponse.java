package com.recu.common.web.error;

/** 모든 오류 응답의 모양. 원본 규격 {code, message, field} 그대로. */
public record ErrorResponse(String code, String message, String field) {

  public static ErrorResponse of(ErrorCode errorCode, String field) {
    return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), field);
  }
}
